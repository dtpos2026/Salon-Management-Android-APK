package com.dtpos.salonmanager.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class ChartBar(val label: String, val value: Long, val highlight: Boolean = false)

/**
 * Lightweight bar chart drawn directly on a Canvas (no chart library). Values are shown
 * above the tallest bar and labels underneath.
 */
@Composable
fun BarChart(
    bars: List<ChartBar>,
    valueLabel: (Long) -> String,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    highlightColor: Color = MaterialTheme.colorScheme.secondary,
    height: Int = 140,
) {
    if (bars.isEmpty()) return
    val max = bars.maxOf { it.value }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val description = bars.joinToString { "${it.label}: ${valueLabel(it.value)}" }
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Text(
            valueLabel(max),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(Modifier.fillMaxWidth().height(height.dp).padding(top = 4.dp)) {
            val slot = size.width / bars.size
            val barWidth = (slot * 0.62f).coerceAtMost(48.dp.toPx())
            val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            bars.forEachIndexed { i, bar ->
                val left = i * slot + (slot - barWidth) / 2f
                drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), radius)
                val h = size.height * (bar.value.toFloat() / max)
                if (h > 0f) {
                    drawRoundRect(
                        if (bar.highlight) highlightColor else barColor,
                        Offset(left, size.height - h),
                        Size(barWidth, h),
                        radius,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            bars.forEach { bar ->
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Horizontal share bars, e.g. expenses by category or payment methods. */
@Composable
fun ShareBars(
    items: List<Pair<String, Long>>,
    valueLabel: (Long) -> String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val total = items.sumOf { it.second }.coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, value) ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(valueLabel(value), style = MaterialTheme.typography.bodyMedium)
                }
                ProgressBar(fraction = value.toFloat() / total, color = color, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
