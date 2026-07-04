package com.checkin.app.data.local

/**
 * Single source of truth for classifying a day's net worked time against its target.
 *
 * A day is PRESENT once it reaches the target ("present mark"), HALF_DAY_LEAVE once it reaches
 * half the target, and FULL_DAY_LEAVE below that.
 */
object AttendanceRules {

    fun classify(totalMs: Long, targetMs: Long): AttendanceStatus = when {
        totalMs >= targetMs -> AttendanceStatus.PRESENT
        totalMs >= targetMs / 2 -> AttendanceStatus.HALF_DAY_LEAVE
        else -> AttendanceStatus.FULL_DAY_LEAVE
    }

    /** Leave fraction a day contributes to the rolling deficit. */
    fun leaveFraction(status: AttendanceStatus): Double = when (status) {
        AttendanceStatus.PRESENT -> 0.0
        AttendanceStatus.HALF_DAY_LEAVE -> 0.5
        AttendanceStatus.FULL_DAY_LEAVE -> 1.0
    }
}
