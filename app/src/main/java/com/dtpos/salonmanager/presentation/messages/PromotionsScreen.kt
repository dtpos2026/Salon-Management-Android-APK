package com.dtpos.salonmanager.presentation.messages

import com.dtpos.salonmanager.services.export.WhatsAppResult
import com.dtpos.salonmanager.presentation.common.showWhatsAppResult
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.DateTimeUtils
import com.dtpos.salonmanager.data.database.model.CustomerListRow
import com.dtpos.salonmanager.domain.model.BusinessProfile
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.EmptyState
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SectionHeader
import com.dtpos.salonmanager.services.export.ExternalApps
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import java.util.concurrent.TimeUnit

enum class PromoTemplate(val label: Int, val text: Int) {
    OFFER(R.string.promo_offer, R.string.promo_offer_text),
    FESTIVAL(R.string.promo_festival, R.string.promo_festival_text),
    NEW_SERVICE(R.string.promo_new_service, R.string.promo_new_service_text),
    MISS_YOU(R.string.promo_miss_you, R.string.promo_miss_you_text),
    THANKS(R.string.promo_thanks, R.string.promo_thanks_text),
}

enum class PromoAudience(val label: Int) {
    ALL(R.string.promo_audience_all),
    INACTIVE(R.string.promo_audience_inactive),
    TOP(R.string.promo_audience_top),
}

class PromotionsViewModel(container: AppContainer) : BaseViewModel() {
    val customers: StateFlow<List<CustomerListRow>> = container.customerRepository.search("", limit = 5_000)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val profile: StateFlow<BusinessProfile?> = container.businessRepository.profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun audience(all: List<CustomerListRow>, audience: PromoAudience, now: Long = System.currentTimeMillis()): List<CustomerListRow> {
        val withPhone = all.filter { !it.customer.phone.isNullOrBlank() }
        return when (audience) {
            PromoAudience.ALL -> withPhone
            PromoAudience.INACTIVE -> withPhone.filter { (it.lastVisitAt ?: 0L) < now - TimeUnit.DAYS.toMillis(INACTIVE_DAYS) }
            PromoAudience.TOP -> withPhone.sortedByDescending { it.totalSpentMinor }.take(TOP_COUNT)
        }
    }

    companion object {
        const val INACTIVE_DAYS = 30L
        const val TOP_COUNT = 30
    }
}

/**
 * Promotional and greeting messages to customers on WhatsApp. WhatsApp does not allow apps to
 * send in bulk, so each chat opens with the message ready and the owner taps send.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromotionsScreen(onBack: () -> Unit) {
    val vm = appViewModel { PromotionsViewModel(it) }
    val customers by vm.customers.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val salon = profile?.name.orEmpty()
    var template by rememberSaveable { mutableStateOf(PromoTemplate.OFFER) }
    var audience by rememberSaveable { mutableStateOf(PromoAudience.ALL) }
    var greetByName by rememberSaveable { mutableStateOf(true) }
    var message by rememberSaveable(template, salon) { mutableStateOf(context.getString(template.text, salon)) }
    val sent = remember { mutableStateListOf<Long>() }
    val targets = remember(customers, audience) { vm.audience(customers, audience) }

    Scaffold(topBar = { SalonTopBar(stringResource(R.string.promo_title), onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ContentCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.promo_template), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PromoTemplate.entries.forEach { t ->
                            FilterChip(selected = template == t, onClick = { template = t }, label = { Text(stringResource(t.label)) })
                        }
                    }
                    FormTextField(message, { message = it }, stringResource(R.string.promo_message), singleLine = false, minLines = 4)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.promo_greet_by_name), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = greetByName, onCheckedChange = { greetByName = it })
                    }
                    Text(stringResource(R.string.promo_audience), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PromoAudience.entries.forEach { a ->
                            FilterChip(selected = audience == a, onClick = { audience = a }, label = { Text(stringResource(a.label)) })
                        }
                    }
                    Text(
                        stringResource(R.string.promo_how, targets.size, sent.count { id -> targets.any { it.customer.id == id } }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } }
            }
            item { SectionHeader(stringResource(R.string.promo_recipients)) }
            if (targets.isEmpty()) {
                item { EmptyState(Icons.Filled.Campaign, stringResource(R.string.promo_empty_title), stringResource(R.string.promo_empty_message)) }
            }
            items(targets, key = { it.customer.id }) { row ->
                val done = row.customer.id in sent
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.customer.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(row.customer.phone, row.lastVisitAt?.let { stringResource(R.string.promo_last_visit, DateTimeUtils.formatDate(it)) }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (done) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.promo_sent), tint = WhatsAppGreen)
                            Spacer(Modifier.width(8.dp))
                        }
                        Button(
                            onClick = {
                                val text = if (greetByName) context.getString(R.string.promo_greeting, row.customer.name) + "\n" + message else message
                                val result = ExternalApps.whatsAppText(context, row.customer.phone, text)
                                if (result != WhatsAppResult.FAILED) sent.add(row.customer.id)
                                context.showWhatsAppResult(result)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (done) MaterialTheme.colorScheme.surfaceVariant else WhatsAppGreen, contentColor = if (done) MaterialTheme.colorScheme.onSurfaceVariant else Color.White),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (done) R.string.promo_send_again else R.string.promo_send))
                        }
                    }
                }
            }
        }
    }
}
