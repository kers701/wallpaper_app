package com.kers.killove.jhsy.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricUnlock {
    fun canAuthenticate(context: Context): Boolean {
        val m = BiometricManager.from(context)
        val code = m.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return code == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun availabilityLabel(context: Context): String {
        val m = BiometricManager.from(context)
        return when (
            m.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> "可用"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "系统未录入指纹/面容"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "本机无生物识别硬件"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "硬件暂不可用"
            else -> "不可用"
        }
    }

    fun prompt(
        activity: FragmentActivity,
        title: String = "解锁应用锁",
        subtitle: String = "使用指纹或系统生物识别",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                ) {
                    onCancel()
                } else {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                onError("未识别，请重试")
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("取消")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
        prompt.authenticate(info)
    }
}
