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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.SupportAgent
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
import androidx.compose.material3.OutlinedButton
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
import com.dtpos.salonmanager.data.database.entities.BookingEntity
import com.dtpos.salonmanager.data.repository.DashboardData
import com.dtpos.salonmanager.data.repository.PeriodSummary
import com.dtpos.salonmanager.domain.model.BookingStatus
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.presentation.components.LabeledValueRow
import com.dtpos.salonmanager.presentation.components.PeriodPicker
import com.dtpos.salonmanager.presentation.tokens.formatSlot
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
    val period by vm.period.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            SalonTopBar(
                title = state.profile?.name?.ifBlank { null } ?: stringResource(R.string.app_name),
                subtitle = DateTimeUtils.formatDate(period.businessDay),
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
            item { RushButtons(period.tokensEnabled, onNavigate) }
            if (period.todayClosed) {
                item {
                    InfoBanner(
                        text = stringResource(R.string.dashboard_day_closed, DateTimeUtils.formatDate(period.businessDay)),
                        icon = Icons.Filled.Lock,
                        container = SalonTheme.extended.warningContainer,
                        content = SalonTheme.extended.warning,
                        actionLabel = stringResource(R.string.action_open),
                        onAction = { onNavigate(Routes.CLOSE_DAY) },
                    )
                }
            }
            if (period.tokensEnabled && period.tokens.isNotEmpty()) {
                item { TokensCard(period.tokens, onClick = { onNavigate(Routes.TOKENS) }) }
            }
            item { SectionHeader(stringResource(R.string.dashboard_period_title)) }
            item { PeriodPicker(period.choice, vm::choose) }
            period.summary?.let { summary ->
                item { PeriodGrid(summary) }
                item { MoneyByAccountCard(summary, onManage = { onNavigate(Routes.PAYMENT_ACCOUNTS) }) }
                if (summary.staff.isNotEmpty()) item { StaffPeriodCard(summary, onClick = { onNavigate(Routes.STAFF) }) }
            }
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
                Icon(Icons.Filled.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
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
private fun PeriodGrid(summary: PeriodSummary) {
    val money = LocalMoney.current
    val ext = SalonTheme.extended
    val profit = summary.profit
    val online = summary.received.filter { it.paymentMethod != PaymentMethod.CASH }.sumOf { it.totalMinor }
    val cards: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> StatCard(stringResource(R.string.dashboard_sales), money.format(summary.sales.totalMinor), Icons.AutoMirrored.Filled.TrendingUp, m) },
        { m -> StatCard(stringResource(R.string.dashboard_customers), summary.sales.saleCount.toString(), Icons.Filled.People, m) },
        { m -> StatCard(stringResource(R.string.dashboard_cash_received), money.format(summary.sales.cashMinor), Icons.Filled.Payments, m, accent = ext.gold) },
        { m -> StatCard(stringResource(R.string.dashboard_online_received), money.format(online), Icons.Filled.AccountBalance, m, accent = ext.positive) },
        { m ->
            StatCard(
                stringResource(R.string.dashboard_udhaar),
                money.format(summary.sales.creditMinor),
                Icons.AutoMirrored.Filled.ReceiptLong,
                m,
                accent = ext.warning,
                valueColor = if (summary.sales.creditMinor > 0) ext.warning else MaterialTheme.colorScheme.onSurface,
            )
        },
        { m -> StatCard(stringResource(R.string.dashboard_commission), money.format(summary.commissionMinor), Icons.Filled.Badge, m, accent = ext.warning) },
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
    )
    AdaptiveGrid(cards)
}

/** Where today's (or the period's) money is: cash drawer, JazzCash, EasyPaisa, bank, card, udhaar. */
@Composable
private fun MoneyByAccountCard(summary: PeriodSummary, onManage: () -> Unit) {
    val money = LocalMoney.current
    ContentCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.dashboard_money_where), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onManage) { Text(stringResource(R.string.accounts_manage)) }
        }
        val cash = summary.received.filter { it.paymentMethod == PaymentMethod.CASH }.sumOf { it.totalMinor }
        LabeledValueRow(stringResource(R.string.payment_cash), money.format(cash))
        summary.received.filter { it.paymentMethod != PaymentMethod.CASH }.forEach { row ->
            LabeledValueRow(row.accountName ?: stringResource(row.paymentMethod.labelRes), money.format(row.totalMinor))
        }
        if (summary.sales.creditMinor > 0) {
            LabeledValueRow(stringResource(R.string.dashboard_udhaar), money.format(summary.sales.creditMinor), valueColor = SalonTheme.extended.warning)
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        LabeledValueRow(stringResource(R.string.dashboard_sales), money.format(summary.sales.totalMinor), emphasize = true)
    }
}

/** Each staff member's customers, sales and commission for the chosen period. */
@Composable
private fun StaffPeriodCard(summary: PeriodSummary, onClick: () -> Unit) {
    val money = LocalMoney.current
    ContentCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.dashboard_staff_work), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        summary.staff.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.staffName ?: stringResource(R.string.pos_no_staff), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.dashboard_staff_line, row.customerCount, money.format(row.salesMinor)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (row.commissionMinor > 0) {
                    Text(money.format(row.commissionMinor), style = MaterialTheme.typography.titleSmall, color = SalonTheme.extended.warning)
                }
            }
        }
    }
}

/** The big buttons for rush hour, right under "New sale". */
@Composable
private fun RushButtons(tokensEnabled: Boolean, onNavigate: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val buttons = buildList {
            if (tokensEnabled) add(Triple(Routes.TOKENS, R.string.nav_tokens, Icons.Filled.ConfirmationNumber))
            add(Triple(Routes.DUES, R.string.nav_dues, Icons.AutoMirrored.Filled.ReceiptLong))
            add(Triple(Routes.CLOSE_DAY, R.string.nav_close_day, Icons.Filled.Lock))
        }
        buttons.forEach { (route, label, icon) ->
            OutlinedButton(onClick = { onNavigate(route) }, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Today's queue at a glance: who is being served and who is next. */
@Composable
private fun TokensCard(tokens: List<BookingEntity>, onClick: () -> Unit) {
    val serving = tokens.firstOrNull { it.status == BookingStatus.SERVING }
    val waiting = tokens.filter { it.status == BookingStatus.WAITING }
    ContentCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.dashboard_tokens_today), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.tokens_waiting_count, waiting.size), style = MaterialTheme.typography.labelLarge)
        }
        serving?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.dashboard_token_serving, it.tokenNumber, it.customerName),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        waiting.take(4).forEach { b ->
            Text(
                listOfNotNull("#${b.tokenNumber}", b.customerName, b.service, b.timeMinutes?.let { formatSlot(it) }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
    QuickAction(Routes.TOKENS, R.string.nav_tokens, Icons.Filled.ConfirmationNumber),
    QuickAction(Routes.CLOSE_DAY, R.string.nav_close_day, Icons.Filled.Lock),
    QuickAction(Routes.MENU, R.string.nav_menu, Icons.AutoMirrored.Filled.MenuBook),
    QuickAction(Routes.DUES, R.string.nav_dues, Icons.AutoMirrored.Filled.ReceiptLong),
    QuickAction(Routes.PROMOTIONS, R.string.nav_promotions, Icons.Filled.Campaign),
    QuickAction(Routes.AI, R.string.nav_ai, Icons.Filled.AutoAwesome),
    QuickAction(Routes.SUPPORT, R.string.nav_support, Icons.Filled.SupportAgent),
    QuickAction(Routes.SERVICES, R.string.nav_services, Icons.Filled.ContentCut),
    QuickAction(Routes.STAFF, R.string.nav_staff, Icons.Filled.Groups),
    QuickAction(Routes.CASH, R.string.nav_cash, Icons.Filled.AccountBalanceWallet),
    QuickAction(Routes.PAYMENT_ACCOUNTS, R.string.nav_accounts, Icons.Filled.AccountBalance),
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
