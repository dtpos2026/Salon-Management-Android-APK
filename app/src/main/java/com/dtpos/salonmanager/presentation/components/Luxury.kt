package com.dtpos.salonmanager.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.presentation.theme.Brand
import com.dtpos.salonmanager.presentation.theme.SalonTheme
import kotlin.math.cos
import kotlin.math.sin

/** Colours used on the dark, glassy brand screens (intro, login, approval). */
object Glass {
    val Fill = Color.White.copy(alpha = 0.09f)
    val FillStrong = Color.White.copy(alpha = 0.14f)
    val Border = Color.White.copy(alpha = 0.20f)
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.72f)
    val TextMuted = Color.White.copy(alpha = 0.52f)
}

/**
 * Deep purple background with two slowly drifting violet glows, like light on a salon mirror.
 * The animation is cheap (two radial gradients redrawn per frame) and stops when [animated] is false.
 */
@Composable
fun BrandBackground(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val phase = if (animated) {
        val transition = rememberInfiniteTransition(label = "glow")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
            label = "glowPhase",
        )
        value
    } else {
        0.6f
    }
    val palette = SalonTheme.glass
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundMiddle, palette.backgroundBottom)))
            .drawBehind {
                val w = size.width
                val h = size.height
                val c1 = Offset(w * (0.18f + 0.10f * cos(phase)), h * (0.14f + 0.05f * sin(phase)))
                val c2 = Offset(w * (0.86f + 0.08f * sin(phase)), h * (0.78f + 0.06f * cos(phase)))
                drawCircle(
                    Brush.radialGradient(listOf(palette.glow.copy(alpha = 0.55f), Color.Transparent), center = c1, radius = w * 0.75f),
                    radius = w * 0.75f,
                    center = c1,
                )
                drawCircle(
                    Brush.radialGradient(listOf(palette.glowSecondary.copy(alpha = 0.30f), Color.Transparent), center = c2, radius = w * 0.65f),
                    radius = w * 0.65f,
                    center = c2,
                )
            },
        content = content,
    )
}

/** Frosted glass panel. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Glass.FillStrong, Glass.Fill)))
            .border(BorderStroke(1.dp, Glass.Border), shape)
            .padding(22.dp),
        content = content,
    )
}

/**
 * The DT monogram with an optional moving light sweep ([shine] from -0.5 to 1.5 crosses it once).
 */
@Composable
fun BrandMonogram(modifier: Modifier = Modifier, width: Dp = 168.dp, shine: Float? = null) {
    Image(
        painter = painterResource(R.drawable.dt_monogram),
        contentDescription = null,
        modifier = modifier
            .width(width)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                if (shine != null) {
                    val x = size.width * shine
                    drawRect(
                        brush = Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.7f), Color.Transparent),
                            start = Offset(x - size.width * 0.25f, 0f),
                            end = Offset(x + size.width * 0.05f, size.height),
                        ),
                        blendMode = BlendMode.SrcAtop,
                    )
                }
            },
    )
}

/** "DT SALON — MANAGER" wordmark as in the brand artwork. [reveal] 0..1 opens the letter spacing. */
@Composable
fun BrandWordmark(modifier: Modifier = Modifier, reveal: Float = 1f, compact: Boolean = false) {
    val accent = SalonTheme.glass.accent
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "DT SALON",
            color = Color.White,
            fontSize = if (compact) 26.sp else 34.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val lineWidth = (if (compact) 26 else 38).dp * reveal
            Box(Modifier.width(lineWidth).height(2.dp).background(accent.copy(alpha = 0.8f)))
            Spacer(Modifier.width(10.dp))
            Text(
                "MANAGER",
                color = accent,
                fontSize = if (compact) 14.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (2 + 8 * reveal).sp,
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(lineWidth).height(2.dp).background(accent.copy(alpha = 0.8f)))
        }
    }
}

/** Logo block used at the top of the login and approval screens. */
@Composable
fun BrandHeader(tagline: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMonogram(width = if (compact) 112.dp else 150.dp)
        Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
        BrandWordmark(compact = compact)
        Spacer(Modifier.height(8.dp))
        Text(tagline, color = Glass.TextSecondary, fontSize = 13.sp, letterSpacing = 0.6.sp, textAlign = TextAlign.Center)
    }
}

/** White pill "Continue with Google" button following Google's branding. */
@Composable
fun GoogleSignInButton(text: String, loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = !loading,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(27.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F),
            disabledContainerColor = Color.White.copy(alpha = 0.8f),
            disabledContentColor = Color(0xFF1F1F1F),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = SalonTheme.glass.buttonStart)
        } else {
            Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

/** Primary action on glass screens: luminous violet gradient pill. */
@Composable
fun GlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val palette = SalonTheme.glass
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = !loading,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.horizontalGradient(listOf(palette.buttonStart, palette.buttonEnd))),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = palette.onButton)
                    Spacer(Modifier.width(10.dp))
                } else if (icon != null) {
                    Icon(icon, contentDescription = null, tint = palette.onButton, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, color = palette.onButton, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

/** Secondary outlined action on glass screens. */
@Composable
fun GlassOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(23.dp),
        border = BorderStroke(1.dp, Glass.Border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Glass.Fill, contentColor = Color.White),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, maxLines = 1)
    }
}

/** Text field styled for the dark glass screens. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    val accent = SalonTheme.glass.accent
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = accent,
            unfocusedBorderColor = Glass.Border,
            focusedLabelColor = accent,
            unfocusedLabelColor = Glass.TextSecondary,
            cursorColor = accent,
            focusedContainerColor = Glass.Fill,
            unfocusedContainerColor = Glass.Fill,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Small "By Digital Target" footer. */
@Composable
fun PoweredByFooter(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(Modifier.width(18.dp).height(1.dp).background(Glass.TextMuted))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Glass.TextMuted, fontSize = 12.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(18.dp).height(1.dp).background(Glass.TextMuted))
    }
}
