package com.checkin.app.ui.components.charts

/**
 * Pure geometry for the hand-rolled charts. Kept free of Compose and Android types so the maths is
 * unit-testable — the Canvas composables in this package only translate these results into draws.
 */
object ChartGeometry {

    /** One arc of a donut, in the degree convention `drawArc` uses (0 = 3 o'clock, clockwise). */
    data class Segment(val index: Int, val startAngle: Float, val sweepAngle: Float)

    private const val FULL_TURN = 360f

    /** Sweeps start at 12 o'clock and run clockwise. */
    const val TOP_OF_CIRCLE = -90f

    /**
     * Proportional arcs for [values], in order, starting at [startAngle]. Zero and negative values
     * are skipped rather than drawn as hairlines, and the final segment absorbs any rounding drift
     * so the arcs always close the full circle. Returns empty when nothing is positive.
     */
    fun donutSegments(values: List<Float>, startAngle: Float = TOP_OF_CIRCLE): List<Segment> {
        val total = values.filter { it > 0f }.sum()
        if (total <= 0f) return emptyList()

        val drawable = values.withIndex().filter { it.value > 0f }
        var cursor = startAngle
        return drawable.mapIndexed { position, (index, value) ->
            val sweep = if (position == drawable.lastIndex) {
                // Close exactly on the start angle instead of accumulating float error.
                startAngle + FULL_TURN - cursor
            } else {
                value / total * FULL_TURN
            }
            Segment(index, cursor, sweep).also { cursor += sweep }
        }
    }
}
