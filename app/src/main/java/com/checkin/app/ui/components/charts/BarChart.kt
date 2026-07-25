package com.checkin.app.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Rounded vertical bars over [values], scaled to a rounded ceiling derived from the data. Axis
 * labels belong to the caller, which knows what the bars represent.
 */
@Composable
fun BarChart(
    values: List<Float>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    barColor: Color = Color.Unspecified,
    cornerRadius: Dp = 4.dp
) {
    Canvas(
        modifier = modifier.semantics { this.contentDescription = contentDescription }
    ) {
        if (values.isEmpty()) return@Canvas

        val ceiling = ChartGeometry.niceMaxY(values.maxOrNull() ?: 0f)
        val radius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())

        ChartGeometry.barRects(values, size.width, size.height, ceiling).forEach { bar ->
            val height = bar.bottom - bar.top
            // A zero-height rounded rect renders as a stray lens shape; skip it entirely.
            if (height <= 0f) return@forEach
            drawRoundRect(
                color = barColor,
                topLeft = Offset(bar.left, bar.top),
                size = Size(bar.right - bar.left, height),
                cornerRadius = radius
            )
        }
    }
}
