package com.checkin.app.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A proportional ring over [values], coloured pairwise by [colors]. The hole keeps it readable at
 * small sizes and leaves room for [content] in the middle.
 *
 * [contentDescription] must state the values in words — the split is conveyed by colour alone
 * otherwise, which is unreadable to a screen reader and to anyone who can't distinguish the hues.
 */
@Composable
fun DonutChart(
    values: List<Float>,
    colors: List<Color>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 20.dp,
    emptyColor: Color = Color.Transparent,
    content: @Composable () -> Unit = {},
) {
    val segments = ChartGeometry.donutSegments(values)

    Box(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            if (segments.isEmpty()) {
                // Nothing tracked yet — draw the bare ring so the chart still occupies its slot.
                drawArc(
                    color = emptyColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                return@Canvas
            }

            segments.forEach { segment ->
                drawArc(
                    color = colors[segment.index % colors.size],
                    startAngle = segment.startAngle,
                    sweepAngle = segment.sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
            }
        }
        content()
    }
}
