package com.checkin.app

import com.checkin.app.ui.components.charts.ChartGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartGeometryTest {

    private val tolerance = 0.001f

    @Test
    fun `an empty series draws nothing`() {
        assertTrue(ChartGeometry.donutSegments(emptyList()).isEmpty())
    }

    /** A month with no tracked days must not divide by zero — it renders the bare ring instead. */
    @Test
    fun `an all-zero series draws nothing`() {
        assertTrue(ChartGeometry.donutSegments(listOf(0f, 0f, 0f)).isEmpty())
    }

    @Test
    fun `a single value fills the whole circle`() {
        val segments = ChartGeometry.donutSegments(listOf(5f))

        assertEquals(1, segments.size)
        assertEquals(ChartGeometry.TOP_OF_CIRCLE, segments[0].startAngle, tolerance)
        assertEquals(360f, segments[0].sweepAngle, tolerance)
    }

    @Test
    fun `values split proportionally`() {
        val segments = ChartGeometry.donutSegments(listOf(1f, 1f, 2f))

        assertEquals(listOf(0, 1, 2), segments.map { it.index })
        assertEquals(90f, segments[0].sweepAngle, tolerance)
        assertEquals(90f, segments[1].sweepAngle, tolerance)
        assertEquals(180f, segments[2].sweepAngle, tolerance)
    }

    /** Zero slices are skipped, but the surviving segments keep their original colour indices. */
    @Test
    fun `zero values are skipped without shifting colour indices`() {
        val segments = ChartGeometry.donutSegments(listOf(3f, 0f, 1f))

        assertEquals(listOf(0, 2), segments.map { it.index })
        assertEquals(270f, segments[0].sweepAngle, tolerance)
        assertEquals(90f, segments[1].sweepAngle, tolerance)
    }

    @Test
    fun `segments are contiguous and close the circle exactly`() {
        val segments = ChartGeometry.donutSegments(listOf(1f, 1f, 1f))

        segments.zipWithNext { a, b ->
            assertEquals(a.startAngle + a.sweepAngle, b.startAngle, tolerance)
        }
        val last = segments.last()
        assertEquals(
            ChartGeometry.TOP_OF_CIRCLE + 360f,
            last.startAngle + last.sweepAngle,
            tolerance
        )
        assertEquals(360f, segments.sumOf { it.sweepAngle.toDouble() }.toFloat(), tolerance)
    }

    /** Thirds don't divide evenly into 360 in float maths; the last segment absorbs the drift. */
    @Test
    fun `uneven divisions still sum to a full turn`() {
        val segments = ChartGeometry.donutSegments(listOf(1f, 1f, 1f, 1f, 1f, 1f, 1f))

        assertEquals(360f, segments.sumOf { it.sweepAngle.toDouble() }.toFloat(), tolerance)
    }

    @Test
    fun `negative values are ignored`() {
        val segments = ChartGeometry.donutSegments(listOf(-4f, 2f))

        assertEquals(listOf(1), segments.map { it.index })
        assertEquals(360f, segments[0].sweepAngle, tolerance)
    }
}
