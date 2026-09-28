package com.dtpos.salonmanager.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dtpos.salonmanager.BuildConfig
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.data.database.SalonDatabase
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.LabeledValueRow
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.theme.Brand

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = { SalonTopBar(stringResource(R.string.nav_about), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Brand.Navy), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.ContentCut, contentDescription = null, tint = Brand.Gold, modifier = Modifier.size(48.dp))
            }
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            ContentCard {
                LabeledValueRow(stringResource(R.string.about_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                LabeledValueRow(stringResource(R.string.about_database), SalonDatabase.VERSION.toString())
                LabeledValueRow(stringResource(R.string.about_build), if (BuildConfig.DEBUG) "Debug" else "Release")
            }
            ContentCard {
                Text(stringResource(R.string.about_offline_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_offline_text), style = MaterialTheme.typography.bodyMedium)
            }
            ContentCard {
                Text(stringResource(R.string.about_features_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_features_text), style = MaterialTheme.typography.bodyMedium)
            }
            ContentCard {
                Text(stringResource(R.string.about_open_source_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_open_source_text), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.about_copyright),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
