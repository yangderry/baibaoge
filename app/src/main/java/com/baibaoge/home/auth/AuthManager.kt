package com.baibaoge.home.auth

import android.content.Context
import com.baibaoge.home.data.AppDatabase
import com.baibaoge.home.data.entity.UserPinEntity
import com.baibaoge.home.security.PinCrypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 认证管理单例（设计方案 3.4）：
 * PIN 4-6 位，加盐 SHA-256 存储不存明文；连续 5 次错误锁定 30 秒。
 */
class AuthManager private constructor(context: Context) {

    private val dao = AppDatabase.getInstance(context).userPinDao()

    private val _pinSet = MutableStateFlow(false)
    val pinSet: StateFlow<Boolean> = _pinSet

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    private var failCount = 0
    private var lockUntil = 0L

    /** 应用启动时调用：加载是否已设置 PIN，并默认进入锁定态 */
    suspend fun init() {
        _pinSet.value = dao.get() != null
        _locked.value = _pinSet.value
    }

    /** 设置 PIN（首次或修改） */
    suspend fun setupPin(pin: String, fingerprintEnabled: Boolean) {
        val salt = PinCrypto.generateSalt()
        dao.insert(
            UserPinEntity(
                pinHash = PinCrypto.hashPin(pin, salt),
                salt = salt,
                fingerprintEnabled = if (fingerprintEnabled) 1 else 0
            )
        )
        _pinSet.value = true
        _locked.value = false
        failCount = 0
    }

    /**
     * 校验 PIN。
     * @return null 表示通过；否则返回错误提示文案
     */
    suspend fun verifyPin(pin: String): String? {
        val now = System.currentTimeMillis()
        if (now < lockUntil) {
            return "已锁定，请 ${(lockUntil - now) / 1000 + 1} 秒后再试"
        }
        val record = dao.get() ?: return "PIN 未设置"
        return if (PinCrypto.verify(pin, record.salt, record.pinHash)) {
            failCount = 0
            _locked.value = false
            null
        } else {
            failCount++
            if (failCount >= MAX_FAIL_COUNT) {
                lockUntil = now + LOCKOUT_MILLIS
                failCount = 0
                "错误次数过多，锁定 30 秒"
            } else {
                "PIN 错误，还可尝试 ${MAX_FAIL_COUNT - failCount} 次"
            }
        }
    }

    /** 指纹是否可用（已设置且开关打开） */
    suspend fun fingerprintEnabled(): Boolean = dao.get()?.fingerprintEnabled == 1

    /** 指纹验证成功后解锁 */
    fun onFingerprintSuccess() {
        _locked.value = false
        failCount = 0
    }

    /** 锁定（切后台/冷启动时调用） */
    fun lock() {
        if (_pinSet.value) _locked.value = true
    }

    companion object {
        const val MAX_FAIL_COUNT = 5
        const val LOCKOUT_MILLIS = 30_000L
        const val PIN_MIN_LENGTH = 4
        const val PIN_MAX_LENGTH = 6

        @Volatile
        private var INSTANCE: AuthManager? = null

        fun getInstance(context: Context): AuthManager =
            INSTANCE ?: synchronized(this) {
                AuthManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}
