package com.dtpos.salonmanager.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.CurrencyFormatter
import com.dtpos.salonmanager.core.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Currency formatter of the current salon (updates when the currency setting changes). */
val LocalMoney = staticCompositionLocalOf { CurrencyFormatter() }

/** Creates a ViewModel scoped to the current navigation entry with access to the container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** Base ViewModel with one-off snackbar messages and crash-safe coroutine launching. */
abstract class BaseViewModel : ViewModel() {
    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    protected fun showMessage(text: UiText) {
        _messages.tryEmit(text)
    }

    protected fun showMessage(resId: Int, vararg args: Any) = showMessage(UiText.Res(resId, args.toList()))

    /** Launches work that can never crash the app; unexpected errors become a friendly message. */
    protected fun launchSafe(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showMessage(R.string.error_unexpected)
        }
    }
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> if (args.isEmpty()) stringResource(id) else stringResource(id, *args.toTypedArray())
}

fun UiText.asString(context: android.content.Context): String = when (this) {
    is UiText.Raw -> value
    is UiText.Res -> if (args.isEmpty()) context.getString(id) else context.getString(id, *args.toTypedArray())
}

@Composable
fun currentContext(): android.content.Context = LocalContext.current
