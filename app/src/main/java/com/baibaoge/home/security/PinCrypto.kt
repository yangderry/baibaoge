package com.baibaoge.home.security

import java.security.MessageDigest
import java.security.SecureRandom

/** PIN 加盐 SHA-256 哈希工具 */
object PinCrypto {

    /** 生成 16 字节随机盐，返回 hex 字符串 */
    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** SHA-256(salt + pin)，hex 输出 */
    fun hashPin(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest((salt + pin).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verify(pin: String, salt: String, expectedHash: String): Boolean =
        hashPin(pin, salt) == expectedHash
}
