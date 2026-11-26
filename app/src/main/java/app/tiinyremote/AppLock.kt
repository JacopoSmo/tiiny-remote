package app.tiinyremote

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Settings → Privacy → App lock: Tiiny Remote asks for a fingerprint, the face or the phone's screen lock when it's
 * opened, and again after it was away longer than the chosen time. Work in the background (jobs, automations,
 * notifications) goes on while it's locked.
 */
object AppLock {
    /** True while the lock screen covers the app. */
    val locked = MutableStateFlow(false)
    private var awaySince = 0L
    private var opened = false

    /** Why the lock can't be turned on (no screen lock set on the phone), or null when it can. */
    fun cannot(ctx: Context): String? = when (BiometricManager.from(ctx).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)) {
        BiometricManager.BIOMETRIC_SUCCESS -> null
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Set a screen lock on the phone first (Android Settings → Security), then turn this on."
        else -> "This phone can't lock apps with its screen lock."
    }

    fun onStop() { awaySince = System.currentTimeMillis() }

    /** On coming back: locks when it's on and the app was away long enough (or is just opening). */
    fun onStart(s: AppSettings) {
        if (!s.appLock) { locked.value = false; opened = true; return }
        val away = System.currentTimeMillis() - awaySince
        if (!opened || away >= s.lockAfter * 1000L) locked.value = true
        opened = true
    }

    fun ask(activity: FragmentActivity) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked.value = false }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Tiiny Remote")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build(),
        )
    }
}
