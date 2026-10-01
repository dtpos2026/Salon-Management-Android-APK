package com.dtpos.salonmanager.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.presentation.account.languageName
import com.dtpos.salonmanager.presentation.common.LocalAppContainer
import com.dtpos.salonmanager.presentation.components.ContentCard
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import com.dtpos.salonmanager.presentation.components.SectionHeader
import com.dtpos.salonmanager.services.backup.AppRestarter
import com.dtpos.salonmanager.services.prefs.AppLanguage
import com.dtpos.salonmanager.services.prefs.ColorTheme
import com.dtpos.salonmanager.services.prefs.ThemeMode

private data class ThemeSwatch(val theme: ColorTheme, val nameRes: Int, val colors: List<Color>)

private val swatches = listOf(
    ThemeSwatch(ColorTheme.ROYAL_PURPLE, R.string.theme_royal_purple, listOf(Color(0xFF2A0757), Color(0xFF6A2BD9), Color(0xFFC9A4FF))),
    ThemeSwatch(ColorTheme.BLACK_GOLD, R.string.theme_black_gold, listOf(Color(0xFF0C0C0D), Color(0xFFB8902A), Color(0xFFF4E6BF))),
    ThemeSwatch(ColorTheme.ROSE_GOLD, R.string.theme_rose_gold, listOf(Color(0xFF4A1E2A), Color(0xFFC26A86), Color(0xFFF5C6B8))),
)

/** Settings > App preferences: colour theme, light/dark, language and sound effects. */
@Composable
fun AppPreferencesScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val prefs = container.uiPreferences
    val context = LocalContext.current
    val themeMode by prefs.themeMode.collectAsStateWithLifecycle()
    val colorTheme by prefs.colorTheme.collectAsStateWithLifecycle()
    val sound by prefs.soundEffects.collectAsStateWithLifecycle()
    val language = prefs.language

    Scaffold(topBar = { SalonTopBar(stringResource(R.string.prefs_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader(stringResource(R.string.prefs_color_theme))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                swatches.forEach { swatch ->
                    val selected = swatch.theme == colorTheme
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .border(
                                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                RoundedCornerShape(18.dp),
                            )
                            .clickable {
                                container.soundEffects.tap()
                                prefs.setColorTheme(swatch.theme)
                            }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(swatch.colors)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(swatch.nameRes),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            SectionHeader(stringResource(R.string.prefs_appearance))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val modes = listOf(
                    ThemeMode.SYSTEM to R.string.prefs_theme_system,
                    ThemeMode.LIGHT to R.string.prefs_theme_light,
                    ThemeMode.DARK to R.string.prefs_theme_dark,
                )
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { prefs.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) { Text(stringResource(label), maxLines = 1) }
                }
            }

            SectionHeader(stringResource(R.string.prefs_language))
            ContentCard {
                AppLanguage.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (option != language) {
                                    prefs.setLanguage(option)
                                    AppRestarter.restart(context)
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == language, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(languageName(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    stringResource(R.string.prefs_language_restart),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            SectionHeader(stringResource(R.string.prefs_sound))
            ContentCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.prefs_sound_sub),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = sound, onCheckedChange = {
                        prefs.setSoundEffects(it)
                        if (it) container.soundEffects.tap()
                    })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
