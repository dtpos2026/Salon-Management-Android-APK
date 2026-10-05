package com.dtpos.salonmanager.presentation.services

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.data.database.entities.ServiceEntity
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.LocalMoney
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.components.ChoiceChips
import com.dtpos.salonmanager.presentation.components.EmptyState
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MenuViewModel(container: AppContainer) : BaseViewModel() {
    val services: StateFlow<List<ServiceEntity>?> = container.serviceRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * The salon's menu, like a restaurant menu: pick a category, see each style / beard / facial
 * with its photo, name and price. Show it to the customer, then start the sale.
 */
@Composable
fun MenuScreen(onBack: () -> Unit, onNewSale: () -> Unit, onManage: () -> Unit) {
    val vm = appViewModel { MenuViewModel(it) }
    val services by vm.services.collectAsStateWithLifecycle()
    val money = LocalMoney.current
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<ServiceEntity?>(null) }

    Scaffold(
        topBar = {
            SalonTopBar(
                stringResource(R.string.nav_menu),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onManage) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.nav_services)) }
                },
            )
        },
    ) { padding ->
        val list = services.orEmpty()
        val categories = list.map { it.category }.distinct()
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (categories.size > 1) {
                ChoiceChips(
                    options = listOf<String?>(null) + categories,
                    selected = category,
                    label = { it ?: stringResource(R.string.filter_all) },
                    onSelect = { category = it },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (services != null && list.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.ContentCut,
                    title = stringResource(R.string.pos_no_services_title),
                    message = stringResource(R.string.menu_empty_message),
                    actionLabel = stringResource(R.string.nav_services),
                    onAction = onManage,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(list.filter { category == null || it.category == category }, key = { it.id }) { service ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { open = service },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        ServicePhoto(service.imagePath, Modifier.fillMaxWidth().aspectRatio(1f))
                        Column(Modifier.padding(10.dp)) {
                            Text(service.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(money.format(service.priceMinor), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            if (service.durationMinutes > 0) {
                                Text(
                                    stringResource(R.string.services_minutes, service.durationMinutes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    open?.let { service ->
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(service.name) },
            text = {
                Column {
                    ServicePhoto(service.imagePath, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)), maxSize = 720)
                    Text(
                        money.format(service.priceMinor),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(service.category, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = {
                    open = null
                    onNewSale()
                }) {
                    Icon(Icons.Filled.AddShoppingCart, contentDescription = null)
                    Text(" " + stringResource(R.string.dashboard_new_sale))
                }
            },
            dismissButton = { TextButton(onClick = { open = null }) { Text(stringResource(R.string.action_close)) } },
        )
    }
}
