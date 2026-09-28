package com.dtpos.salonmanager.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Salon brand palette: deep navy with a warm gold accent. */
object Brand {
    val Navy = Color(0xFF14213D)
    val NavyLight = Color(0xFF2C3E66)
    val Gold = Color(0xFFD4A537)
    val GoldDark = Color(0xFF9A7418)
    val Teal = Color(0xFF1F7A68)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B2A4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3F5),
    onPrimaryContainer = Color(0xFF0F1A33),
    secondary = Color(0xFF9A7418),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF8E7C0),
    onSecondaryContainer = Color(0xFF3D2A00),
    tertiary = Color(0xFF1F7A68),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDEDE4),
    onTertiaryContainer = Color(0xFF00201A),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF191C22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C22),
    surfaceVariant = Color(0xFFE7E9F0),
    onSurfaceVariant = Color(0xFF4A4F5C),
    outline = Color(0xFF7A7F8C),
    outlineVariant = Color(0xFFD0D3DC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FAFD),
    surfaceContainer = Color(0xFFF1F3F8),
    surfaceContainerHigh = Color(0xFFEBEDF3),
    surfaceContainerHighest = Color(0xFFE4E7EE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5EC),
    onPrimary = Color(0xFF1B2A4A),
    primaryContainer = Color(0xFF2C3C60),
    onPrimaryContainer = Color(0xFFDCE3F5),
    secondary = Color(0xFFE9C06A),
    onSecondary = Color(0xFF3D2A00),
    secondaryContainer = Color(0xFF5B4300),
    onSecondaryContainer = Color(0xFFF8E7C0),
    tertiary = Color(0xFF8FD5C1),
    onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF005143),
    onTertiaryContainer = Color(0xFFCDEDE4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1320),
    onBackground = Color(0xFFE2E4EC),
    surface = Color(0xFF151A28),
    onSurface = Color(0xFFE2E4EC),
    surfaceVariant = Color(0xFF2A3040),
    onSurfaceVariant = Color(0xFFC4C7D2),
    outline = Color(0xFF8E919C),
    outlineVariant = Color(0xFF3F4452),
    surfaceContainerLowest = Color(0xFF0B0E18),
    surfaceContainerLow = Color(0xFF171C2A),
    surfaceContainer = Color(0xFF1B2130),
    surfaceContainerHigh = Color(0xFF252B3B),
    surfaceContainerHighest = Color(0xFF303646),
)

/** Semantic colours for money: profit, loss, warnings. */
@Immutable
data class ExtendedColors(
    val positive: Color,
    val positiveContainer: Color,
    val negative: Color,
    val negativeContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val gold: Color,
)

private val LightExtended = ExtendedColors(
    positive = Color(0xFF1E7B45),
    positiveContainer = Color(0xFFD7F2E1),
    negative = Color(0xFFB3261E),
    negativeContainer = Color(0xFFFBE0DD),
    warning = Color(0xFF8A5A00),
    warningContainer = Color(0xFFFFEBC2),
    gold = Brand.GoldDark,
)

private val DarkExtended = ExtendedColors(
    positive = Color(0xFF7BD89A),
    positiveContainer = Color(0xFF12391F),
    negative = Color(0xFFFFB4AB),
    negativeContainer = Color(0xFF4A1512),
    warning = Color(0xFFFFCB6B),
    warningContainer = Color(0xFF3E2C00),
    gold = Brand.Gold,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

private val SalonTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Large numeric style for money on cards. */
val MoneyLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)

private val SalonShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SalonTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtendedColors provides if (darkTheme) DarkExtended else LightExtended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = SalonTypography,
            shapes = SalonShapes,
            content = content,
        )
    }
}

object SalonTheme {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}
