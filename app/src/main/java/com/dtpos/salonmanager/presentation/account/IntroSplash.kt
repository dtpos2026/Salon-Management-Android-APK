package com.dtpos.salonmanager.presentation.account

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.presentation.components.BrandBackground
import com.dtpos.salonmanager.presentation.components.BrandMonogram
import com.dtpos.salonmanager.presentation.components.BrandWordmark
import com.dtpos.salonmanager.presentation.components.Glass
import com.dtpos.salonmanager.presentation.components.PoweredByFooter
import com.dtpos.salonmanager.presentation.theme.LocalGlassPalette
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import com.dtpos.salonmanager.presentation.theme.glassPaletteFor
import com.dtpos.salonmanager.services.prefs.ColorTheme

/** Plays once per app start; keeps the system splash -> app hand-off smooth and branded. */
object IntroState {
    var shown = false
}

/** Full intro, and the short one in Fast mode (old phones, rush hours). */
private const val INTRO_MS = 2_000
private const val FAST_INTRO_MS = 800

private fun phase(progress: Float, start: Float, length: Float): Float =
    FastOutSlowInEasing.transform(((progress - start) / length).coerceIn(0f, 1f))

/** Ease-out with a small bounce past the end, so the logo "lands". */
private fun overshoot(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

/**
 * Animated brand intro (about 2 s, 0.8 s in Fast mode; tap to skip): the DT monogram grows in and
 * lands with a soft glow, two light rings spread out, a light sweeps across it like a mirror,
 * the wordmark opens up and a thin line fills at the bottom while the app gets ready. Always in
 * the DT purple brand colours.
 */
@Composable
fun IntroSplash(fast: Boolean = false, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = if (fast) FAST_INTRO_MS else INTRO_MS, easing = LinearEasing))
        onFinished()
    }
    CompositionLocalProvider(LocalGlassPalette provides glassPaletteFor(ColorTheme.ROYAL_PURPLE)) {
        val accent = SalonTheme.glass.accent
        BrandBackground(
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFinished),
        ) {
            val p = progress.value
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(270.dp)) {
                        val base = size.minDimension / 2f
                        // Soft glow behind the logo.
                        val glow = phase(p, 0.08f, 0.4f)
                        if (glow > 0f) {
                            drawCircle(
                                Brush.radialGradient(listOf(accent.copy(alpha = 0.42f * glow), Color.Transparent), center = center, radius = base),
                                radius = base,
                            )
                        }
                        // Two light rings spread out once the logo has landed.
                        listOf(0.3f, 0.46f).forEach { start ->
                            val t = phase(p, start, 0.42f)
                            if (t > 0f && t < 1f) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.38f * (1f - t)),
                                    radius = base * (0.5f + 0.5f * t),
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            }
                        }
                    }
                    BrandMonogram(
                        width = 176.dp,
                        shine = -0.5f + 2f * phase(p, 0.34f, 0.4f),
                        modifier = Modifier.graphicsLayer {
                            val t = ((p - 0f) / 0.38f).coerceIn(0f, 1f)
                            alpha = phase(p, 0f, 0.25f)
                            val scale = 0.6f + 0.4f * overshoot(t)
                            scaleX = scale
                            scaleY = scale
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                BrandWordmark(
                    reveal = phase(p, 0.4f, 0.32f),
                    modifier = Modifier.graphicsLayer {
                        val t = phase(p, 0.36f, 0.32f)
                        alpha = t
                        translationY = (1f - t) * 22.dp.toPx()
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.brand_tagline),
                    color = Glass.TextSecondary,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { alpha = phase(p, 0.58f, 0.25f) },
                )
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Fills while the app gets ready.
                Box(
                    Modifier
                        .width(120.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.16f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(phase(p, 0.05f, 0.9f).coerceAtLeast(0.01f))
                            .background(accent),
                    )
                }
                Spacer(Modifier.height(14.dp))
                PoweredByFooter(
                    stringResource(R.string.brand_by, "Digital Target"),
                    Modifier.graphicsLayer { alpha = phase(p, 0.55f, 0.3f) },
                )
            }
        }
    }
}
