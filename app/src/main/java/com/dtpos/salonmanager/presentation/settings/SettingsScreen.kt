package com.dtpos.salonmanager.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.domain.model.ExpenseType
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.components.ConfirmDialog
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SectionHeader
import com.dtpos.salonmanager.presentation.navigation.Routes

class SettingsViewModel(private val container: AppContainer) : BaseViewModel() {
    fun eraseAll() = launchSafe {
        if (!container.eraseAllData()) showMessage(R.string.settings_erase_failed)
    }
}

private data class SettingsEntry(val route: String, val titleRes: Int, val subtitleRes: Int, val icon: ImageVector)

@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val snackbar = remember { SnackbarHostState() }
    var confirmErase by remember { mutableStateOf(false) }
    MessageEffect(vm.messages, snackbar)

    val business = listOf(
        SettingsEntry(Routes.BUSINESS_PROFILE, R.string.settings_business, R.string.settings_business_sub, Icons.Filled.Storefront),
        SettingsEntry(Routes.RECEIPT_SETTINGS, R.string.settings_receipt, R.string.settings_receipt_sub, Icons.AutoMirrored.Filled.ReceiptLong),
        SettingsEntry(Routes.PRINTER, R.string.settings_printer, R.string.settings_printer_sub, Icons.Filled.Print),
    )
    val catalog = listOf(
        SettingsEntry(Routes.SERVICES, R.string.nav_services, R.string.settings_services_sub, Icons.Filled.ContentCut),
        SettingsEntry(Routes.STAFF, R.string.nav_staff, R.string.settings_staff_sub, Icons.Filled.Groups),
        SettingsEntry(Routes.expenseCategories(ExpenseType.BUSINESS), R.string.expenses_categories, R.string.settings_categories_sub, Icons.Filled.Category),
        SettingsEntry(Routes.TARGETS, R.string.nav_targets, R.string.settings_targets_sub, Icons.Filled.Flag),
    )
    val app = listOf(
        SettingsEntry(Routes.PREFERENCES, R.string.prefs_title, R.string.prefs_sub, Icons.Filled.Palette),
    )
    val account = listOf(
        SettingsEntry(Routes.ACCOUNT, R.string.account_title, R.string.account_sub, Icons.Filled.AccountCircle),
    )
    val safety = listOf(
        SettingsEntry(Routes.BACKUP, R.string.settings_backup, R.string.settings_backup_sub, Icons.Filled.Backup),
        SettingsEntry(Routes.SECURITY, R.string.settings_security, R.string.settings_security_sub, Icons.Filled.Lock),
        SettingsEntry(Routes.ABOUT, R.string.nav_about, R.string.settings_about_sub, Icons.Filled.Info),
    )

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.nav_settings), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionHeader(stringResource(R.string.settings_group_account)) }
            account.forEach { entry -> item(key = entry.route) { SettingsRow(entry) { onNavigate(entry.route) } } }
            item { SectionHeader(stringResource(R.string.settings_group_app)) }
            app.forEach { entry -> item(key = entry.route) { SettingsRow(entry) { onNavigate(entry.route) } } }
            item { SectionHeader(stringResource(R.string.settings_group_business)) }
            business.forEach { entry -> item(key = entry.route) { SettingsRow(entry) { onNavigate(entry.route) } } }
            item { SectionHeader(stringResource(R.string.settings_group_catalog)) }
            catalog.forEach { entry -> item(key = entry.route) { SettingsRow(entry) { onNavigate(entry.route) } } }
            item { SectionHeader(stringResource(R.string.settings_group_safety)) }
            safety.forEach { entry -> item(key = entry.route) { SettingsRow(entry) { onNavigate(entry.route) } } }
            item { SectionHeader(stringResource(R.string.settings_group_danger)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { confirmErase = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    ListItem(
                        leadingContent = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        headlineContent = { Text(stringResource(R.string.settings_erase)) },
                        supportingContent = { Text(stringResource(R.string.settings_erase_sub)) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    )
                }
            }
        }
    }

    if (confirmErase) {
        ConfirmDialog(
            title = stringResource(R.string.settings_erase_title),
            message = stringResource(R.string.settings_erase_message),
            confirmLabel = stringResource(R.string.settings_erase_confirm),
            destructive = true,
            icon = Icons.Filled.DeleteForever,
            onConfirm = {
                confirmErase = false
                vm.eraseAll()
            },
            onDismiss = { confirmErase = false },
        )
    }
}

@Composable
private fun SettingsRow(entry: SettingsEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        ListItem(
            leadingContent = { Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(stringResource(entry.titleRes)) },
            supportingContent = { Text(stringResource(entry.subtitleRes)) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        )
    }
}
