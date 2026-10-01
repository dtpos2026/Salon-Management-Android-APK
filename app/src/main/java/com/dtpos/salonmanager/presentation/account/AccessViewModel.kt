package com.dtpos.salonmanager.presentation.account

import android.app.Activity
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.UiText
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.services.account.AccessState
import com.dtpos.salonmanager.services.account.AccountRegistration
import com.dtpos.salonmanager.services.account.GoogleSignInError
import com.dtpos.salonmanager.services.account.RefreshResult
import com.dtpos.salonmanager.services.account.SignInOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Actions of the sign-in / approval screens shown before the salon app opens. */
class AccessViewModel(private val container: AppContainer) : BaseViewModel() {
    private val accounts = container.accountManager

    val state: StateFlow<AccessState> = accounts.state
    val branding = accounts.branding
    val checking = accounts.checking

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Inline message on the glass screens (errors and hints). */
    private val _notice = MutableStateFlow<UiText?>(null)
    val notice: StateFlow<UiText?> = _notice.asStateFlow()

    fun clearNotice() {
        _notice.value = null
    }

    private fun run(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _notice.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notice.value = UiText.res(R.string.error_unexpected)
            } finally {
                _busy.value = false
            }
        }
    }

    fun signIn(activity: Activity) = run {
        when (val outcome = accounts.signInWithGoogle(activity)) {
            SignInOutcome.Success -> container.soundEffects.tap()
            is SignInOutcome.FirebaseFailed -> _notice.value = UiText.res(R.string.login_error_network)
            is SignInOutcome.Failed -> _notice.value = when (outcome.error) {
                GoogleSignInError.CANCELLED -> null
                GoogleSignInError.NO_ACCOUNT -> UiText.res(R.string.login_error_no_account)
                GoogleSignInError.NOT_CONFIGURED -> UiText.res(R.string.login_error_not_configured, branding.value.appName)
                GoogleSignInError.FAILED -> UiText.res(R.string.login_error_failed)
            }
        }
    }

    fun register(registration: AccountRegistration) {
        if (registration.salonName.isBlank() || registration.ownerName.isBlank() || registration.phone.isBlank()) {
            _notice.value = UiText.res(R.string.register_error_required)
            return
        }
        run {
            if (accounts.register(registration) is RefreshResult.Failed) {
                _notice.value = UiText.res(R.string.register_error_failed)
            } else {
                container.soundEffects.success()
            }
        }
    }

    fun checkAgain() = run {
        val before = state.value
        when (accounts.refresh()) {
            is RefreshResult.Failed -> _notice.value = UiText.res(R.string.status_check_failed)
            else -> if (state.value == before && before !is AccessState.Allowed) {
                _notice.value = UiText.res(R.string.status_unchanged)
            }
        }
    }

    fun signOut() = run { accounts.signOut() }

    /** The previous owner's salon data is erased (after a safety backup) and this account takes over. */
    fun eraseAndTakeOver() = run {
        if (container.eraseAllData()) {
            accounts.takeOverDevice()
        } else {
            _notice.value = UiText.res(R.string.device_erase_failed)
        }
    }
}
