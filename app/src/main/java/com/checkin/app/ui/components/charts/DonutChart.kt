package com.checkin.app.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
 * Side of the largest square that fits inside a circle, as a fraction of its diameter (1/√2).
 *
 * The hole is round, so its full diameter is only available along the centre line. A caption bounded
 * by the diameter still overhangs the arc at the top and bottom of its own height — which is what
 * the Attendance donut did, and it was the call site that bounded its content at all.
 */
private const val INSCRIBED_SQUARE = 0.707f

/**
 * A proportional ring over [values], coloured pairwise by [colors]. The hole keeps it readable at
 * small sizes and leaves room for [content] in the middle.
 *
 * [content] is clamped to the square inscribed in the hole, so it cannot reach the arc at any font
 * scale or screen size. That bound is applied here rather than being left to callers deliberately.
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
    strokeWidth: Dp = DonutChartDefaults.StrokeWidth,
    emptyColor: Color = Color.Transparent,
    content: @Composable () -> Unit = {},
) {
    val segments = ChartGeometry.donutSegments(values)

    BoxWithConstraints(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        // The arc is stroked along a circle inset by half the stroke, so the hole is the diameter
        // less a full stroke width. A stroke wider than the box would invert it, hence the floor.
        val diameter = minOf(maxWidth, maxHeight)
        val innerBound = if (diameter == Dp.Infinity) {
            // An unsized caller leaves the constraints unbounded; sizing the content to an infinite
            // square would crash measurement, and a ring with no diameter is meaningless anyway.
            Modifier
        } else {
            Modifier.size((diameter - strokeWidth * 2).coerceAtLeast(0.dp) * INSCRIBED_SQUARE)
        }

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

        Box(modifier = innerBound, contentAlignment = Alignment.Center) {
            content()
        }
    }
}
