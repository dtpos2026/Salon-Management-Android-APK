package com.dtpos.salonmanager.presentation.account

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.dtpos.salonmanager.presentation.theme.glassPaletteFor
import com.dtpos.salonmanager.services.prefs.ColorTheme

/** Plays once per app start; keeps the system splash -> app hand-off smooth and branded. */
object IntroState {
    var shown = false
}

private fun phase(progress: Float, start: Float, length: Float): Float =
    FastOutSlowInEasing.transform(((progress - start) / length).coerceIn(0f, 1f))

/**
 * Animated brand intro (about 1.6 s, tap to skip): the DT monogram rises in, a light sweeps across
 * it like a mirror reflection, then the wordmark opens up. Always in the DT purple brand colours.
 */
@Composable
fun IntroSplash(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 1600, easing = LinearEasing))
        onFinished()
    }
    CompositionLocalProvider(LocalGlassPalette provides glassPaletteFor(ColorTheme.ROYAL_PURPLE)) {
        BrandBackground(
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFinished),
        ) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMonogram(
                    width = 176.dp,
                    shine = -0.5f + 2f * phase(progress.value, 0.32f, 0.5f),
                    modifier = Modifier.graphicsLayer {
                        val t = phase(progress.value, 0f, 0.42f)
                        alpha = t
                        scaleX = 0.84f + 0.16f * t
                        scaleY = 0.84f + 0.16f * t
                        translationY = (1f - t) * 30.dp.toPx()
                    },
                )
                Spacer(Modifier.height(22.dp))
                BrandWordmark(
                    reveal = phase(progress.value, 0.38f, 0.42f),
                    modifier = Modifier.graphicsLayer {
                        val t = phase(progress.value, 0.36f, 0.36f)
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
                    modifier = Modifier.graphicsLayer { alpha = phase(progress.value, 0.6f, 0.3f) },
                )
            }
            PoweredByFooter(
                stringResource(R.string.brand_by, "Digital Target"),
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
                    .graphicsLayer { alpha = phase(progress.value, 0.65f, 0.3f) },
            )
        }
    }
}
