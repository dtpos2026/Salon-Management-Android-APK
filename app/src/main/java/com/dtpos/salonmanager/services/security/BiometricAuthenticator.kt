package com.dtpos.salonmanager.services.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Thin wrapper around AndroidX BiometricPrompt (fingerprint / face, when the phone has it). */
object BiometricAuthenticator {

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun isAvailable(context: Context): Boolean = try {
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (e: Exception) {
        false
    }

    fun authenticate(
        context: Context,
        title: String,
        subtitle: String,
        negativeText: String,
        onSuccess: () -> Unit,
        onFailure: (message: String?) -> Unit,
    ) {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            onFailure(null)
            return
        }
        try {
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                        onFailure(if (cancelled) null else errString.toString())
                    }
                },
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeText)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            prompt.authenticate(info)
        } catch (e: Exception) {
            onFailure(null)
        }
    }

    private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
}
