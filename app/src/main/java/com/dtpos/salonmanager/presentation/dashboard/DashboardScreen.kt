package com.dtpos.salonmanager.presentation.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.core.util.Percent
import com.dtpos.salonmanager.data.database.entities.SaleEntity
import com.dtpos.salonmanager.data.repository.DashboardData
import com.dtpos.salonmanager.domain.calc.TargetProgress
import com.dtpos.salonmanager.domain.insights.Insight
import com.dtpos.salonmanager.domain.license.LicenseState
import com.dtpos.salonmanager.domain.license.LicenseStatus
import com.dtpos.salonmanager.domain.model.SaleStatus
import com.dtpos.salonmanager.presentation.common.LocalAppContainer
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.components.BarChart
import com.dtpos.salonmanager.presentation.components.ChartBar
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.EmptyState
import com.dtpos.salonmanager.presentation.components.InfoBanner
import com.dtpos.salonmanager.presentation.components.LoadingState
import com.dtpos.salonmanager.presentation.components.ProgressBar
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SectionHeader
import com.dtpos.salonmanager.presentation.components.StatCard
import com.dtpos.salonmanager.presentation.components.StatusBadge
import com.dtpos.salonmanager.presentation.insights.content
import com.dtpos.salonmanager.presentation.navigation.Routes
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import java.util.concurrent.TimeUnit

@Composable
fun DashboardScreen(onNewSale: () -> Unit, onOpenSale: (Long) -> Unit, onNavigate: (String) -> Unit) {
    val vm = appViewModel { DashboardViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val insights by vm.insights.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SalonTopBar(
                title = state.profile?.name?.ifBlank { null } ?: stringResource(R.string.app_name),
                subtitle = DateTimeUtils.formatDate(DateTimeUtils.today()),
                actions = {
                    IconButton(onClick = { onNavigate(Routes.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
            )
        },
    ) { padding ->
        val data = state.data
        if (data == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SalonHeader(state.profile?.logoPath, state.isDemo) }
            item { LicenseBanner(state.license, onClick = { onNavigate(Routes.LICENSE) }) }
            item { BackupReminder(state.lastBackupAt, data.todaySales.saleCount > 0 || data.recentSales.isNotEmpty()) { onNavigate(Routes.BACKUP) } }
            item {
                Button(
                    onClick = onNewSale,
                    enabled = !state.license.isReadOnly,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Filled.AddShoppingCart, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.dashboard_new_sale), style = MaterialTheme.typography.titleMedium)
                }
            }
            item { SectionHeader(stringResource(R.string.dashboard_today)) }
            item { TodayGrid(data) }
            item { TargetsCard(data, onClick = { onNavigate(Routes.TARGETS) }) }
            item { WeekChartCard(data) }
            item { InsightsPreview(insights, onViewAll = { onNavigate(Routes.INSIGHTS) }) }
            item { SectionHeader(stringResource(R.string.dashboard_manage)) }
            item { QuickActions(onNavigate) }
            item {
                SectionHeader(stringResource(R.string.dashboard_recent_sales)) {
                    TextButton(onClick = { onNavigate(Routes.SALES) }) { Text(stringResource(R.string.action_view_all)) }
                }
            }
            if (data.recentSales.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.PointOfSale,
                        title = stringResource(R.string.dashboard_no_sales_title),
                        message = stringResource(R.string.dashboard_no_sales_message),
                    )
                }
            } else {
                items(data.recentSales, key = { it.id }) { sale -> RecentSaleRow(sale) { onOpenSale(sale.id) } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SalonHeader(logoPath: String?, isDemo: Boolean) {
    val logoStore = LocalAppContainer.current.logoStore
    val logo = remember(logoPath) { logoStore.loadBitmap(logoPath, 256)?.asImageBitmap() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            if (logo != null) {
                Image(logo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.ContentCut, contentDescription = null, tint = SalonTheme.extended.gold, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.dashboard_greeting), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.dashboard_greeting_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isDemo) {
            StatusBadge(
                stringResource(R.string.dashboard_demo_badge),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun LicenseBanner(license: LicenseState, onClick: () -> Unit) {
    val ext = SalonTheme.extended
    val text = when (license.status) {
        LicenseStatus.TRIAL -> stringResource(R.string.dashboard_license_trial, license.daysRemaining ?: 0L)
        LicenseStatus.EXPIRING_SOON -> stringResource(R.string.dashboard_license_expiring, license.daysRemaining ?: 0L)
        LicenseStatus.EXPIRED, LicenseStatus.TRIAL_EXPIRED, LicenseStatus.INVALID, LicenseStatus.CLOCK_TAMPERED ->
            if (license.enforced) stringResource(R.string.dashboard_license_read_only) else null
        else -> null
    } ?: return
    val warning = license.isReadOnly
    InfoBanner(
        text = text,
        icon = Icons.Filled.VerifiedUser,
        container = if (warning) ext.negativeContainer else ext.warningContainer,
        content = if (warning) ext.negative else ext.warning,
        actionLabel = stringResource(R.string.action_open),
        onAction = onClick,
    )
}

@Composable
private fun BackupReminder(lastBackupAt: Long, hasData: Boolean, onClick: () -> Unit) {
    if (!hasData) return
    val days = if (lastBackupAt == 0L) null else TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastBackupAt)
    if (days != null && days < 7) return
    val ext = SalonTheme.extended
    InfoBanner(
        text = if (days == null) stringResource(R.string.dashboard_backup_never) else stringResource(R.string.dashboard_backup_old, days),
        icon = Icons.Filled.Backup,
        container = ext.warningContainer,
        content = ext.warning,
        actionLabel = stringResource(R.string.dashboard_backup_now),
        onAction = onClick,
    )
}

@Composable
private fun TodayGrid(data: DashboardData) {
    val money = LocalMoney.current
    val ext = SalonTheme.extended
    val profit = data.todayProfit
    val cards: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> StatCard(stringResource(R.string.dashboard_sales), money.format(data.todaySales.totalMinor), Icons.AutoMirrored.Filled.TrendingUp, m) },
        { m ->
            StatCard(
                stringResource(R.string.dashboard_expenses),
                money.format(profit.totalBusinessCostsMinor),
                Icons.Filled.MoneyOff,
                m,
                accent = ext.negative,
                subtitle = if (profit.staffPaymentsMinor > 0) stringResource(R.string.dashboard_incl_staff, money.format(profit.staffPaymentsMinor)) else null,
            )
        },
        { m ->
            StatCard(
                stringResource(R.string.dashboard_profit),
                money.format(profit.businessProfitMinor),
                Icons.Filled.Savings,
                m,
                accent = if (profit.isLoss) ext.negative else ext.positive,
                valueColor = if (profit.isLoss) ext.negative else ext.positive,
            )
        },
        { m -> StatCard(stringResource(R.string.dashboard_cash_received), money.format(data.todaySales.cashMinor), Icons.Filled.Payments, m, accent = ext.gold) },
        { m -> StatCard(stringResource(R.string.dashboard_customers), data.todaySales.saleCount.toString(), Icons.Filled.People, m) },
        { m -> StatCard(stringResource(R.string.dashboard_services), data.todaySales.serviceCount.toString(), Icons.Filled.Spa, m) },
        { m ->
            StatCard(
                stringResource(R.string.dashboard_staff_outstanding),
                money.format(data.outstandingStaffPayMinor),
                Icons.Filled.Badge,
                m,
                accent = ext.warning,
                subtitle = stringResource(R.string.dashboard_this_month),
            )
        },
    )
    AdaptiveGrid(cards)
}

/** 2 columns on phones, 3-4 on tablets. */
@Composable
private fun AdaptiveGrid(cells: List<@Composable (Modifier) -> Unit>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth > 840.dp -> 4
            maxWidth > 560.dp -> 3
            else -> 2
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            cells.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { cell -> cell(Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun TargetsCard(data: DashboardData, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Flag, contentDescription = null, tint = SalonTheme.extended.gold)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.dashboard_targets),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            if (!data.dailyTarget.hasTarget && !data.monthlyTarget.hasTarget) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.dashboard_targets_none),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                TargetLine(stringResource(R.string.target_daily), data.dailyTarget)
                TargetLine(stringResource(R.string.target_monthly), data.monthlyTarget)
            }
        }
    }
}

@Composable
private fun TargetLine(label: String, progress: TargetProgress) {
    if (!progress.hasTarget) return
    val money = LocalMoney.current
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text("${Percent.formatRatio(progress.achievedPercent)}%", color = SalonTheme.extended.gold, style = MaterialTheme.typography.labelLarge)
    }
    Spacer(Modifier.height(6.dp))
    ProgressBar(progress.fraction, color = SalonTheme.extended.gold)
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(
            R.string.dashboard_target_detail,
            money.format(progress.achievedMinor),
            money.format(progress.targetMinor),
            money.format(progress.remainingMinor),
        ),
        color = onPrimary.copy(alpha = 0.85f),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun WeekChartCard(data: DashboardData) {
    val money = LocalMoney.current
    ContentCard {
        Text(stringResource(R.string.dashboard_last_7_days), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        BarChart(
            bars = data.last7Days.map {
                ChartBar(
                    label = it.start.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()),
                    value = it.amountMinor,
                    highlight = it.start == data.today,
                )
            },
            valueLabel = { money.compact(it) },
        )
    }
}

@Composable
private fun InsightsPreview(insights: List<Insight>, onViewAll: () -> Unit) {
    if (insights.isEmpty()) return
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = SalonTheme.extended.gold)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.insights_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onViewAll) { Text(stringResource(R.string.action_view_all)) }
        }
        insights.take(2).forEach { insight ->
            val content = insight.content()
            Spacer(Modifier.height(8.dp))
            Row {
                Icon(content.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(content.title, style = MaterialTheme.typography.titleSmall)
                    Text(content.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private data class QuickAction(val route: String, val labelRes: Int, val icon: ImageVector)

private val quickActions = listOf(
    QuickAction(Routes.SERVICES, R.string.nav_services, Icons.Filled.ContentCut),
    QuickAction(Routes.STAFF, R.string.nav_staff, Icons.Filled.Groups),
    QuickAction(Routes.CASH, R.string.nav_cash, Icons.Filled.AccountBalanceWallet),
    QuickAction(Routes.TARGETS, R.string.nav_targets, Icons.Filled.Flag),
    QuickAction(Routes.BUDGET, R.string.nav_budget, Icons.Filled.PieChart),
    QuickAction(Routes.INSIGHTS, R.string.nav_insights, Icons.Filled.AutoAwesome),
    QuickAction(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
    QuickAction(Routes.ABOUT, R.string.nav_about, Icons.Filled.Info),
)

@Composable
private fun QuickActions(onNavigate: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth > 560.dp) 8 else 4
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quickActions.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { action ->
                        Column(
                            Modifier.weight(1f).clip(MaterialTheme.shapes.medium).clickable { onNavigate(action.route) }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(48.dp).clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(action.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun RecentSaleRow(sale: SaleEntity, onClick: () -> Unit) {
    val money = LocalMoney.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        ListItem(
            leadingContent = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
            headlineContent = {
                Text(sale.customerName ?: stringResource(R.string.walk_in_customer), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text("${sale.receiptNumber} · ${DateTimeUtils.formatDateTime(sale.createdAt)} · ${stringResource(sale.paymentMethod.labelRes)}")
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(money.format(sale.totalMinor), style = MaterialTheme.typography.titleSmall)
                    if (sale.status == SaleStatus.VOIDED) {
                        StatusBadge(
                            stringResource(R.string.sale_voided),
                            container = SalonTheme.extended.negativeContainer,
                            content = SalonTheme.extended.negative,
                        )
                    }
                }
            },
        )
    }
    HorizontalDivider(color = androidx.compose.ui.graphics.Color.Transparent)
}
