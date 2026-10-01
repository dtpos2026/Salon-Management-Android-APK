package com.dtpos.salonmanager.services.prefs

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Colour themes the salon can choose in Settings > App preferences. Each has light and dark. */
enum class ColorTheme { ROYAL_PURPLE, BLACK_GOLD, ROSE_GOLD }

/** App languages. Roman Urdu uses the "ur-Latn" locale (resources in values-b+ur+Latn). */
enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    URDU("ur"),
    ROMAN_URDU("ur-Latn");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}

/** Per-phone UI preferences: appearance, language and sound effects. */
class UiPreferences(context: Context) {

    private val prefs = prefsOf(context)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "") }.getOrDefault(ThemeMode.SYSTEM),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _colorTheme = MutableStateFlow(
        runCatching { ColorTheme.valueOf(prefs.getString(KEY_COLOR_THEME, null) ?: "") }.getOrDefault(ColorTheme.ROYAL_PURPLE),
    )
    val colorTheme: StateFlow<ColorTheme> = _colorTheme.asStateFlow()

    private val _sound = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    val soundEffects: StateFlow<Boolean> = _sound.asStateFlow()

    val language: AppLanguage get() = storedLanguage(prefs)

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setColorTheme(theme: ColorTheme) {
        prefs.edit().putString(KEY_COLOR_THEME, theme.name).apply()
        _colorTheme.value = theme
    }

    fun setSoundEffects(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
        _sound.value = enabled
    }

    /** Admin notices are shown once each (remembered by their text). */
    fun isNoticeSeen(text: String): Boolean = prefs.getInt(KEY_NOTICE, 0) == text.hashCode()

    fun markNoticeSeen(text: String) {
        prefs.edit().putInt(KEY_NOTICE, text.hashCode()).apply()
    }

    /** Saved synchronously because the app restarts right after a language change. */
    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.tag).commit()
    }

    companion object {
        private const val PREFS = "dt_ui"
        private const val KEY_THEME = "theme"
        private const val KEY_COLOR_THEME = "colorTheme"
        private const val KEY_SOUND = "sound"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_NOTICE = "noticeSeen"

        private fun prefsOf(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun storedLanguage(prefs: SharedPreferences) = AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null))

        /**
         * Wraps [base] so resources use the chosen app language (also sets RTL for Urdu).
         * Called from attachBaseContext of the Application and the Activity.
         */
        fun wrapWithLanguage(base: Context): Context {
            val language = storedLanguage(prefsOf(base))
            val locale = Locale.forLanguageTag(language.tag)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return base.createConfigurationContext(config)
        }
    }
}
