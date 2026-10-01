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
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.dtpos.salonmanager.services.prefs.ColorTheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** DT Salon brand palette: royal purple with violet glow, lilac and a champagne accent. */
object Brand {
    val Plum = Color(0xFF1E0440)
    val Purple = Color(0xFF2A0757)
    val Royal = Color(0xFF4B1590)
    val Violet = Color(0xFF6A2BD9)
    val Lilac = Color(0xFFC9A4FF)
    val Champagne = Color(0xFFE9C987)
    val Teal = Color(0xFF1F7A68)

    // Names used by older screens.
    val Navy = Purple
    val Gold = Champagne
    val GoldDark = Color(0xFF8A6420)
}

// ---------------------------------------------------------------- Royal Purple (DT brand)
private val PurpleLight = lightColorScheme(
    primary = Color(0xFF4B1590), onPrimary = Color.White,
    primaryContainer = Color(0xFFEADCFF), onPrimaryContainer = Color(0xFF22004F),
    secondary = Color(0xFF8A6420), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBE8C4), onSecondaryContainer = Color(0xFF2D1E00),
    tertiary = Color(0xFF1F7A68), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCDEDE4), onTertiaryContainer = Color(0xFF00201A),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F4FC), onBackground = Color(0xFF1C1724),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1C1724),
    surfaceVariant = Color(0xFFECE5F6), onSurfaceVariant = Color(0xFF4B4459),
    outline = Color(0xFF7B7389), outlineVariant = Color(0xFFD5CCE2),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFBF8FE),
    surfaceContainer = Color(0xFFF4EFFA), surfaceContainerHigh = Color(0xFFEEE8F6),
    surfaceContainerHighest = Color(0xFFE8E1F1),
)

private val PurpleDark = darkColorScheme(
    primary = Color(0xFFD5BEFF), onPrimary = Color(0xFF3A0A78),
    primaryContainer = Color(0xFF4B1590), onPrimaryContainer = Color(0xFFEADCFF),
    secondary = Color(0xFFE9C987), onSecondary = Color(0xFF3F2B00),
    secondaryContainer = Color(0xFF5A4100), onSecondaryContainer = Color(0xFFFBE8C4),
    tertiary = Color(0xFF8FD5C1), onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF005143), onTertiaryContainer = Color(0xFFCDEDE4),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF120A1E), onBackground = Color(0xFFEAE1F2),
    surface = Color(0xFF1A1128), onSurface = Color(0xFFEAE1F2),
    surfaceVariant = Color(0xFF2C2240), onSurfaceVariant = Color(0xFFCDC3DC),
    outline = Color(0xFF968CA6), outlineVariant = Color(0xFF3F3552),
    surfaceContainerLowest = Color(0xFF0D0717), surfaceContainerLow = Color(0xFF1C1329),
    surfaceContainer = Color(0xFF21172F), surfaceContainerHigh = Color(0xFF2B2139),
    surfaceContainerHighest = Color(0xFF362B45),
)

// ---------------------------------------------------------------- Black & Gold
private val GoldLight = lightColorScheme(
    primary = Color(0xFF1C1B19), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF4E6BF), onPrimaryContainer = Color(0xFF261A00),
    secondary = Color(0xFF9C7A1E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBEFC9), onSecondaryContainer = Color(0xFF2E2100),
    tertiary = Color(0xFF5B5F5F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E3E3), onTertiaryContainer = Color(0xFF181C1C),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF9F4), onBackground = Color(0xFF1D1B16),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1D1B16),
    surfaceVariant = Color(0xFFEEE9DD), onSurfaceVariant = Color(0xFF4D4739),
    outline = Color(0xFF7E7766), outlineVariant = Color(0xFFD6CFBE),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFDFBF6),
    surfaceContainer = Color(0xFFF6F2E9), surfaceContainerHigh = Color(0xFFF0ECE2),
    surfaceContainerHighest = Color(0xFFEAE5DA),
)

private val GoldDark = darkColorScheme(
    primary = Color(0xFFE6C15A), onPrimary = Color(0xFF2B2000),
    primaryContainer = Color(0xFF3D3008), onPrimaryContainer = Color(0xFFFFE8A6),
    secondary = Color(0xFFD9C9A3), onSecondary = Color(0xFF3A2F12),
    secondaryContainer = Color(0xFF51452A), onSecondaryContainer = Color(0xFFF6E5BE),
    tertiary = Color(0xFFC8C6C0), onTertiary = Color(0xFF30302C),
    tertiaryContainer = Color(0xFF474742), onTertiaryContainer = Color(0xFFE5E2DA),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0C0C0D), onBackground = Color(0xFFEDE9E1),
    surface = Color(0xFF141415), onSurface = Color(0xFFEDE9E1),
    surfaceVariant = Color(0xFF2A2824), onSurfaceVariant = Color(0xFFD0C8B6),
    outline = Color(0xFF9A927F), outlineVariant = Color(0xFF3D3A33),
    surfaceContainerLowest = Color(0xFF070708), surfaceContainerLow = Color(0xFF161617),
    surfaceContainer = Color(0xFF1B1B1C), surfaceContainerHigh = Color(0xFF252526),
    surfaceContainerHighest = Color(0xFF303031),
)

// ---------------------------------------------------------------- Rose Gold
private val RoseLight = lightColorScheme(
    primary = Color(0xFF9A4B5E), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E1), onPrimaryContainer = Color(0xFF3E0019),
    secondary = Color(0xFFA56E43), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC4), onSecondaryContainer = Color(0xFF2F1500),
    tertiary = Color(0xFF6E5B83), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0DBFF), onTertiaryContainer = Color(0xFF28173B),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F7), onBackground = Color(0xFF22191B),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF22191B),
    surfaceVariant = Color(0xFFF3DDE1), onSurfaceVariant = Color(0xFF524346),
    outline = Color(0xFF847376), outlineVariant = Color(0xFFD7C1C5),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFF5F5),
    surfaceContainer = Color(0xFFFBEDEE), surfaceContainerHigh = Color(0xFFF5E7E8),
    surfaceContainerHighest = Color(0xFFEFE1E2),
)

private val RoseDark = darkColorScheme(
    primary = Color(0xFFFFB1C3), onPrimary = Color(0xFF5E1131),
    primaryContainer = Color(0xFF7B2947), onPrimaryContainer = Color(0xFFFFD9E1),
    secondary = Color(0xFFF0BC92), onSecondary = Color(0xFF4A2806),
    secondaryContainer = Color(0xFF663D1B), onSecondaryContainer = Color(0xFFFFDCC4),
    tertiary = Color(0xFFD9BDF0), onTertiary = Color(0xFF3E2B52),
    tertiaryContainer = Color(0xFF55416A), onTertiaryContainer = Color(0xFFF0DBFF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1113), onBackground = Color(0xFFF0DEE0),
    surface = Color(0xFF22181A), onSurface = Color(0xFFF0DEE0),
    surfaceVariant = Color(0xFF524346), onSurfaceVariant = Color(0xFFD7C1C5),
    outline = Color(0xFFA08C8F), outlineVariant = Color(0xFF524346),
    surfaceContainerLowest = Color(0xFF140C0E), surfaceContainerLow = Color(0xFF22191B),
    surfaceContainer = Color(0xFF271D1F), surfaceContainerHigh = Color(0xFF322729),
    surfaceContainerHighest = Color(0xFF3D3234),
)

fun colorSchemeFor(theme: ColorTheme, dark: Boolean): ColorScheme = when (theme) {
    ColorTheme.ROYAL_PURPLE -> if (dark) PurpleDark else PurpleLight
    ColorTheme.BLACK_GOLD -> if (dark) GoldDark else GoldLight
    ColorTheme.ROSE_GOLD -> if (dark) RoseDark else RoseLight
}

/**
 * Colours of the glassy full-screen pages (login, approval, lock) for each theme. These pages are
 * always dark and luxurious, whatever light/dark mode the rest of the app uses.
 */
@Immutable
data class GlassPalette(
    val backgroundTop: Color,
    val backgroundMiddle: Color,
    val backgroundBottom: Color,
    val glow: Color,
    val glowSecondary: Color,
    val accent: Color,
    val buttonStart: Color,
    val buttonEnd: Color,
    val onButton: Color,
)

fun glassPaletteFor(theme: ColorTheme): GlassPalette = when (theme) {
    ColorTheme.ROYAL_PURPLE -> GlassPalette(
        backgroundTop = Brand.Purple, backgroundMiddle = Color(0xFF240649), backgroundBottom = Brand.Plum,
        glow = Brand.Violet, glowSecondary = Color(0xFFB04DFF), accent = Brand.Lilac,
        buttonStart = Brand.Violet, buttonEnd = Color(0xFF9B4DFF), onButton = Color.White,
    )
    ColorTheme.BLACK_GOLD -> GlassPalette(
        backgroundTop = Color(0xFF1C1914), backgroundMiddle = Color(0xFF0F0E0C), backgroundBottom = Color(0xFF050505),
        glow = Color(0xFF8A6A1F), glowSecondary = Color(0xFFD4AF37), accent = Color(0xFFE6C15A),
        buttonStart = Color(0xFFB8902A), buttonEnd = Color(0xFFE9C766), onButton = Color(0xFF1A1405),
    )
    ColorTheme.ROSE_GOLD -> GlassPalette(
        backgroundTop = Color(0xFF4A1E2A), backgroundMiddle = Color(0xFF3A1520), backgroundBottom = Color(0xFF220A12),
        glow = Color(0xFFC26A86), glowSecondary = Color(0xFFE3A07A), accent = Color(0xFFF5C6B8),
        buttonStart = Color(0xFFC26A86), buttonEnd = Color(0xFFE3A07A), onButton = Color.White,
    )
}

val LocalGlassPalette = staticCompositionLocalOf { glassPaletteFor(ColorTheme.ROYAL_PURPLE) }

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
fun SalonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: ColorTheme = ColorTheme.ROYAL_PURPLE,
    content: @Composable () -> Unit,
) {
    val extended = (if (darkTheme) DarkExtended else LightExtended).let {
        if (colorTheme == ColorTheme.ROSE_GOLD) it.copy(gold = if (darkTheme) Color(0xFFF0BC92) else Color(0xFFA56E43)) else it
    }
    CompositionLocalProvider(
        LocalExtendedColors provides extended,
        LocalGlassPalette provides glassPaletteFor(colorTheme),
    ) {
        MaterialTheme(
            colorScheme = colorSchemeFor(colorTheme, darkTheme),
            typography = SalonTypography,
            shapes = SalonShapes,
            content = content,
        )
    }
}

object SalonTheme {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current

    val glass: GlassPalette
        @Composable get() = LocalGlassPalette.current
}
