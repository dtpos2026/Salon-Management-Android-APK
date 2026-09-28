package com.dtpos.salonmanager.presentation.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.domain.insights.Insight
import com.dtpos.salonmanager.domain.insights.InsightSeverity
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.InfoBanner
import com.dtpos.salonmanager.presentation.components.LoadingState
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InsightsViewModel(private val container: AppContainer) : BaseViewModel() {
    private val _insights = MutableStateFlow<List<Insight>?>(null)
    val insights: StateFlow<List<Insight>?> = _insights.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = launchSafe {
        _insights.value = null
        val snapshot = container.reportRepository.buildSnapshot(DateTimeUtils.today())
        _insights.value = container.insightEngine.generate(snapshot)
    }
}

@Composable
fun InsightsScreen(onBack: () -> Unit) {
    val vm = appViewModel { InsightsViewModel(it) }
    val insights by vm.insights.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SalonTopBar(
                stringResource(R.string.insights_title),
                onBack = onBack,
                actions = { IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh)) } },
            )
        },
    ) { padding ->
        val list = insights
        if (list == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoBanner(
                    text = stringResource(R.string.insights_offline_note),
                    icon = Icons.Filled.Info,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            items(list) { insight -> InsightCard(insight) }
        }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    val content = insight.content()
    val ext = SalonTheme.extended
    val (container, tint) = when (insight.severity) {
        InsightSeverity.POSITIVE -> ext.positiveContainer to ext.positive
        InsightSeverity.WARNING -> ext.warningContainer to ext.warning
        InsightSeverity.INFO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    }
    ContentCard {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
                Icon(content.icon, contentDescription = null, tint = tint)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(content.title, style = MaterialTheme.typography.titleMedium)
                Text(content.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
