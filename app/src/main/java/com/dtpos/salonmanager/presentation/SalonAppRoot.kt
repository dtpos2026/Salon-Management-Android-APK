package com.dtpos.salonmanager.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.CurrencyFormatter
import com.dtpos.salonmanager.domain.model.BusinessProfile
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.components.LoadingState
import com.dtpos.salonmanager.presentation.lock.LockScreen
import com.dtpos.salonmanager.presentation.navigation.SalonMainScaffold
import com.dtpos.salonmanager.presentation.setup.SetupScreen
import com.dtpos.salonmanager.services.security.ProtectedArea
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface RootState {
    data object Loading : RootState
    data object NeedsSetup : RootState
    data object Locked : RootState
    data class Ready(val profile: BusinessProfile) : RootState
}

class RootViewModel(container: AppContainer) : ViewModel() {
    private val security = container.securityManager

    val state: StateFlow<RootState> = combine(
        container.ready,
        container.businessRepository.profile,
        security.config,
        security.unlocked,
    ) { ready, profile, config, unlocked ->
        when {
            !ready || profile == null || config == null -> RootState.Loading
            !profile.isSetupComplete -> RootState.NeedsSetup
            config.protects(ProtectedArea.APP) && !unlocked -> RootState.Locked
            else -> RootState.Ready(profile)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootState.Loading)
}

@Composable
fun SalonAppRoot() {
    val vm = appViewModel { RootViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val background = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    when (val s = state) {
        RootState.Loading -> LoadingState(background)
        RootState.NeedsSetup -> SetupScreen()
        RootState.Locked -> LockScreen(area = ProtectedArea.APP, modifier = background)
        is RootState.Ready -> {
            val formatter = remember(s.profile.currency) { CurrencyFormatter(s.profile.currency) }
            CompositionLocalProvider(LocalMoney provides formatter) {
                SalonMainScaffold()
            }
        }
    }
}
