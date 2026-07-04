package com.checkin.app

import com.checkin.app.data.local.AttendanceRules
import com.checkin.app.data.local.AttendanceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AttendanceRulesTest {

    private val twoHours = 2 * 60 * 60 * 1000L

    @Test
    fun `at or above target is present`() {
        assertEquals(AttendanceStatus.PRESENT, AttendanceRules.classify(twoHours, twoHours))
        assertEquals(AttendanceStatus.PRESENT, AttendanceRules.classify(twoHours + 1, twoHours))
    }

    @Test
    fun `between half and target is half day`() {
        assertEquals(AttendanceStatus.HALF_DAY_LEAVE, AttendanceRules.classify(twoHours / 2, twoHours))
        assertEquals(AttendanceStatus.HALF_DAY_LEAVE, AttendanceRules.classify(twoHours - 1, twoHours))
    }

    @Test
    fun `below half is full leave`() {
        assertEquals(AttendanceStatus.FULL_DAY_LEAVE, AttendanceRules.classify(twoHours / 2 - 1, twoHours))
        assertEquals(AttendanceStatus.FULL_DAY_LEAVE, AttendanceRules.classify(0, twoHours))
    }

    @Test
    fun `thresholds scale with the target`() {
        val eightHours = 8 * 60 * 60 * 1000L
        assertEquals(AttendanceStatus.FULL_DAY_LEAVE, AttendanceRules.classify(2 * 60 * 60 * 1000L, eightHours))
        assertEquals(AttendanceStatus.HALF_DAY_LEAVE, AttendanceRules.classify(4 * 60 * 60 * 1000L, eightHours))
        assertEquals(AttendanceStatus.PRESENT, AttendanceRules.classify(8 * 60 * 60 * 1000L, eightHours))
    }

    @Test
    fun `leave fractions per status`() {
        assertEquals(0.0, AttendanceRules.leaveFraction(AttendanceStatus.PRESENT), 0.0)
        assertEquals(0.5, AttendanceRules.leaveFraction(AttendanceStatus.HALF_DAY_LEAVE), 0.0)
        assertEquals(1.0, AttendanceRules.leaveFraction(AttendanceStatus.FULL_DAY_LEAVE), 0.0)
    }
}
