package com.baibaoge.home.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** 指纹/生物识别封装（BiometricPrompt 需要 FragmentActivity） */
object BiometricHelper {

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS

    fun showPrompt(
        activity: FragmentActivity,
        title: String = "指纹解锁",
        subtitle: String = "验证指纹以进入百宝格",
        negativeText: String = "使用 PIN",
        onSuccess: () -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError?.invoke(errString.toString())
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .build()
        prompt.authenticate(info)
    }
}
