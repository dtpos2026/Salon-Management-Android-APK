package com.dtpos.salonmanager.presentation.dashboard

import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.repository.DashboardData
import com.dtpos.salonmanager.data.repository.SettingKeys
import com.dtpos.salonmanager.domain.insights.Insight
import com.dtpos.salonmanager.domain.license.LicenseState
import com.dtpos.salonmanager.domain.model.BusinessProfile
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DashboardUiState(
    val data: DashboardData? = null,
    val profile: BusinessProfile? = null,
    val license: LicenseState = LicenseState.NOT_ENFORCED,
    val lastBackupAt: Long = 0L,
    val isDemo: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val container: AppContainer) : BaseViewModel() {

    /** Emits the current date and rolls over at midnight while the app stays open. */
    private val today = flow {
        while (true) {
            emit(DateTimeUtils.today())
            delay(60_000)
        }
    }.distinctUntilChanged()

    val state: StateFlow<DashboardUiState> = combine(
        today.flatMapLatest { container.reportRepository.observeDashboard(it) },
        container.businessRepository.profile,
        container.licenseManager.state,
        container.settingsRepository.observeLong(SettingKeys.BACKUP_LAST_MANUAL),
        container.settingsRepository.observeBoolean(SettingKeys.DEMO_DATA),
    ) { data, profile, license, lastBackup, demo ->
        DashboardUiState(data, profile, license, lastBackup, demo)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private val _insights = MutableStateFlow<List<Insight>>(emptyList())
    val insights: StateFlow<List<Insight>> = _insights.asStateFlow()

    init {
        // Recompute insights whenever today's sales change (cheap aggregate queries).
        viewModelScope.launch {
            state.map { it.data?.let { d -> d.today to d.todaySales.saleCount } }
                .distinctUntilChanged()
                .collect { key -> if (key != null) refreshInsights(key.first) }
        }
    }

    private suspend fun refreshInsights(today: LocalDate) {
        try {
            val snapshot = container.reportRepository.buildSnapshot(today)
            _insights.value = container.insightEngine.generate(snapshot)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _insights.value = emptyList()
        }
    }
}
