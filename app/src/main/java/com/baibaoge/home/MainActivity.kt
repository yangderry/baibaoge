package com.baibaoge.home

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.baibaoge.home.auth.AuthManager
import com.baibaoge.home.ui.auth.PinLockScreen
import com.baibaoge.home.ui.auth.PinSetupScreen
import com.baibaoge.home.ui.navigation.AppNavHost
import com.baibaoge.home.ui.theme.BaibaogeTheme
import com.baibaoge.home.util.NotificationHelper
import com.baibaoge.home.worker.AutoSyncWorker
import com.baibaoge.home.worker.ExpiryCheckWorker

/** BiometricPrompt 需要 FragmentActivity */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 切后台自动锁定
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                AuthManager.getInstance(applicationContext).lock()
            }
        })
        // 临期提醒：通知渠道 + 每日凌晨 2 点周期任务
        NotificationHelper.ensureChannel(this)
        ExpiryCheckWorker.schedule(this)
        // NAS 自动同步：每日凌晨 3 点（仅家庭 WiFi 白名单下执行）
        AutoSyncWorker.schedule(this)
        setContent {
            BaibaogeTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val auth = remember { AuthManager.getInstance(context) }
    val pinSet by auth.pinSet.collectAsState()
    val locked by auth.locked.collectAsState()

    LaunchedEffect(Unit) { auth.init() }

    when {
        !pinSet -> PinSetupScreen(onDone = { })
        locked -> PinLockScreen(onUnlocked = { })
        else -> AppNavHost()
    }
}
