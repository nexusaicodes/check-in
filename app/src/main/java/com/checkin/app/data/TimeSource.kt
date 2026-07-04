package com.checkin.app.data

import java.time.LocalDate

/** Abstraction over wall-clock reads so date-dependent logic is deterministically testable. */
interface TimeSource {
    fun nowMillis(): Long
    fun today(): LocalDate
}

object SystemTimeSource : TimeSource {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now()
}
