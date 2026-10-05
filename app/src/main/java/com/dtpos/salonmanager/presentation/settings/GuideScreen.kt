package com.dtpos.salonmanager.presentation.settings

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.presentation.common.LocalAppContainer
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SearchField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One topic of the in-app guide: a title and its steps. */
data class GuideTopic(val title: String, val steps: List<String>)

object Guide {
    /** Reads assets/guide/<language>.md ("## Title" lines followed by "- step" lines). */
    fun load(context: Context, languageTag: String): List<GuideTopic> {
        val text = listOf(languageTag, "en").firstNotNullOfOrNull { tag ->
            runCatching { context.assets.open("guide/$tag.md").bufferedReader().use { it.readText() } }.getOrNull()
        } ?: return emptyList()
        return parse(text)
    }

    fun parse(text: String): List<GuideTopic> {
        val topics = mutableListOf<GuideTopic>()
        var title: String? = null
        val steps = mutableListOf<String>()
        fun flush() {
            title?.let { topics += GuideTopic(it, steps.toList()) }
            steps.clear()
        }
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                line.startsWith("## ") -> {
                    flush()
                    title = line.removePrefix("## ").trim()
                }
                line.startsWith("- ") -> steps += line.removePrefix("- ").trim()
                else -> steps += line
            }
        }
        flush()
        return topics
    }
}

/**
 * The A-Z guide inside the app, in the app's language: every task in short, simple steps so
 * any barber or helper can learn the app. Searchable; works offline.
 */
@Composable
fun GuideScreen(onBack: () -> Unit, onSupport: () -> Unit) {
    val context = LocalContext.current
    val language = LocalAppContainer.current.uiPreferences.language.tag
    var topics by remember(language) { mutableStateOf(emptyList<GuideTopic>()) }
    LaunchedEffect(language) {
        topics = withContext(Dispatchers.IO) { Guide.load(context, language) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf(-1) }
    val shown = remember(topics, query) {
        val q = query.trim()
        if (q.isEmpty()) topics else topics.filter { t -> t.title.contains(q, true) || t.steps.any { it.contains(q, true) } }
    }

    Scaffold(topBar = { SalonTopBar(stringResource(R.string.nav_guide), onBack = onBack, subtitle = stringResource(R.string.guide_subtitle)) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SearchField(query, { query = it }, stringResource(R.string.guide_search)) }
            itemsIndexed(shown, key = { _, t -> t.title }) { index, topic ->
                val expanded = open == index || query.isNotBlank()
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().animateContentSize().clickable { open = if (open == index) -1 else index },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${index + 1}. ${topic.title}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                        }
                        if (expanded) {
                            topic.steps.forEach { step ->
                                Row(Modifier.padding(top = 8.dp)) {
                                    Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(step, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onSupport, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Filled.SupportAgent, contentDescription = null)
                    Text("  " + stringResource(R.string.guide_ask_support))
                }
            }
        }
    }
}
