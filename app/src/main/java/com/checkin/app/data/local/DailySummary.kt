package com.checkin.app.data.local

enum class AttendanceStatus {
    PRESENT,
    HALF_DAY_LEAVE,
    FULL_DAY_LEAVE
}

/** Room aggregate query result — not an entity */
data class DailyAggregate(
    val dateKey: String,
    val totalDurationMs: Long,
    val sessionCount: Int,
    val firstCheckIn: Long,
    val lastCheckOut: Long?
)

/** Computed from DailyAggregate with attendance rules applied against that day's target */
data class DailySummary(
    val dateKey: String,
    val totalDurationMs: Long,
    val sessionCount: Int,
    val firstCheckIn: Long,
    val lastCheckOut: Long?,
    val status: AttendanceStatus
) {
    companion object {
        /** Classifies [aggregate] against the target in effect on that day ([targetMs]). */
        fun classify(aggregate: DailyAggregate, targetMs: Long): DailySummary = DailySummary(
            dateKey = aggregate.dateKey,
            totalDurationMs = aggregate.totalDurationMs,
            sessionCount = aggregate.sessionCount,
            firstCheckIn = aggregate.firstCheckIn,
            lastCheckOut = aggregate.lastCheckOut,
            status = AttendanceRules.classify(aggregate.totalDurationMs, targetMs)
        )
    }
}
