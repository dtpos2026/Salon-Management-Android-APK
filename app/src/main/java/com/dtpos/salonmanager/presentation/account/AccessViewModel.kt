package com.dtpos.salonmanager.presentation.account

import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.UiText
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.services.account.AccessState
import com.dtpos.salonmanager.services.account.AccountRegistration
import com.dtpos.salonmanager.services.account.AuthError
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
        _info.value = null
    }

    private fun run(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _notice.value = null
        _info.value = null
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

    /** Positive message (e.g. reset email sent), shown instead of an error. */
    private val _info = MutableStateFlow<UiText?>(null)
    val info: StateFlow<UiText?> = _info.asStateFlow()

    fun signIn(email: String, password: String) {
        if (!validEmail(email)) return fail(R.string.login_error_invalid_email)
        if (password.isEmpty()) return fail(R.string.login_error_wrong)
        run { handle(accounts.signIn(email, password)) }
    }

    fun createLogin(email: String, password: String, confirm: String) {
        if (!validEmail(email)) return fail(R.string.login_error_invalid_email)
        if (password.length < MIN_PASSWORD) return fail(R.string.login_error_weak_password)
        if (password != confirm) return fail(R.string.login_error_password_mismatch)
        run { handle(accounts.createLogin(email, password)) }
    }

    fun forgotPassword(email: String) {
        if (!validEmail(email)) return fail(R.string.login_reset_need_email)
        run {
            val error = accounts.sendPasswordReset(email)
            if (error == null) _info.value = UiText.res(R.string.login_reset_sent, email.trim()) else _notice.value = message(error)
        }
    }

    private fun fail(res: Int) {
        _info.value = null
        _notice.value = UiText.res(res)
    }

    private fun handle(outcome: SignInOutcome) {
        when (outcome) {
            SignInOutcome.Success -> container.soundEffects.tap()
            is SignInOutcome.Failed -> _notice.value = message(outcome.error)
        }
    }

    private fun message(error: AuthError): UiText {
        val name = branding.value.companyName
        return when (error) {
            AuthError.INVALID_EMAIL -> UiText.res(R.string.login_error_invalid_email)
            AuthError.WRONG_CREDENTIALS -> UiText.res(R.string.login_error_wrong)
            AuthError.EMAIL_IN_USE -> UiText.res(R.string.login_error_email_in_use)
            AuthError.WEAK_PASSWORD -> UiText.res(R.string.login_error_weak_password)
            AuthError.USER_DISABLED -> UiText.res(R.string.login_error_disabled, name)
            AuthError.TOO_MANY_ATTEMPTS -> UiText.res(R.string.login_error_too_many)
            AuthError.NETWORK -> UiText.res(R.string.login_error_network)
            AuthError.PROVIDER_DISABLED -> UiText.res(R.string.login_error_provider_disabled, name)
            AuthError.NOT_CONFIGURED -> UiText.res(R.string.login_error_not_configured, name)
            AuthError.FAILED -> UiText.res(R.string.login_error_failed)
        }
    }

    private fun validEmail(email: String): Boolean = EMAIL.matches(email.trim())

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

    private companion object {
        const val MIN_PASSWORD = 6
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
