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
    val firstPunchIn: Long,
    val lastPunchOut: Long?
)

/** Computed from DailyAggregate with attendance rules applied */
data class DailySummary(
    val dateKey: String,
    val totalDurationMs: Long,
    val sessionCount: Int,
    val firstPunchIn: Long,
    val lastPunchOut: Long?,
    val status: AttendanceStatus
) {
    companion object {
        private const val TWO_HOURS_MS = 2 * 60 * 60 * 1000L
        private const val ONE_HOUR_MS = 1 * 60 * 60 * 1000L

        fun fromAggregate(aggregate: DailyAggregate): DailySummary {
            val status = when {
                aggregate.totalDurationMs >= TWO_HOURS_MS -> AttendanceStatus.PRESENT
                aggregate.totalDurationMs >= ONE_HOUR_MS -> AttendanceStatus.HALF_DAY_LEAVE
                else -> AttendanceStatus.FULL_DAY_LEAVE
            }
            return DailySummary(
                dateKey = aggregate.dateKey,
                totalDurationMs = aggregate.totalDurationMs,
                sessionCount = aggregate.sessionCount,
                firstPunchIn = aggregate.firstPunchIn,
                lastPunchOut = aggregate.lastPunchOut,
                status = status
            )
        }
    }
}
