package com.checkin.app

import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.CheckInSessionDao
import com.checkin.app.data.local.DailyAggregate
import com.checkin.app.data.local.DailySummary
import com.checkin.app.data.local.TargetSchedule
import com.checkin.app.di.AttendanceSettings
import com.checkin.app.di.CsvExporter
import com.checkin.app.di.ExportResult
import com.checkin.app.di.ServiceController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Deterministic clock. [day] is mutable so tests can drive a midnight rollover. */
class FixedTime(private val now: Long, date: LocalDate) : TimeSource {
    val day = MutableStateFlow(date)
    override fun nowMillis(): Long = now
    override fun today(): LocalDate = day.value
    override fun currentDay(): Flow<LocalDate> = day
}

/** In-memory, reactive DAO so ViewModel flows emit on mutation. */
class FakeCheckInSessionDao : CheckInSessionDao {
    private val store = MutableStateFlow<List<CheckInSession>>(emptyList())
    val sessions: List<CheckInSession> get() = store.value
    private var nextId = 1L

    fun seedCompleted(dateKey: String, startedAt: Long, durationMs: Long) {
        store.value = store.value + CheckInSession(
            id = nextId++,
            startedAt = startedAt,
            stoppedAt = startedAt + durationMs,
            duration = durationMs,
            dateKey = dateKey
        )
    }

    override suspend fun insertSession(session: CheckInSession): Long {
        val stored = session.copy(id = nextId++)
        store.value = store.value + stored
        return stored.id
    }

    override suspend fun updateSession(session: CheckInSession) {
        store.value = store.value.map { if (it.id == session.id) session else it }
    }

    override suspend fun getActiveSession(): CheckInSession? =
        store.value.firstOrNull { it.stoppedAt == null }

    override fun getActiveSessionFlow(): Flow<CheckInSession?> =
        store.map { list -> list.firstOrNull { it.stoppedAt == null } }

    override suspend fun getSessionById(sessionId: Long): CheckInSession? =
        store.value.firstOrNull { it.id == sessionId }

    override suspend fun getSessionsByDate(dateKey: String): List<CheckInSession> =
        store.value.filter { it.dateKey == dateKey }

    override fun getSessionsByDateFlow(dateKey: String): Flow<List<CheckInSession>> =
        store.map { list -> list.filter { it.dateKey == dateKey } }

    override suspend fun getDailyAggregates(startDate: String, endDate: String): List<DailyAggregate> =
        aggregate(startDate, endDate)

    override fun getDailyAggregatesFlow(startDate: String, endDate: String): Flow<List<DailyAggregate>> =
        store.map { aggregate(startDate, endDate) }

    override suspend fun getAllDateKeys(): List<String> = store.value.map { it.dateKey }.distinct()

    override suspend fun getSessionsByDateRange(startDate: String, endDate: String): List<CheckInSession> =
        store.value.filter { it.dateKey in startDate..endDate && it.stoppedAt != null }

    private fun aggregate(startDate: String, endDate: String): List<DailyAggregate> =
        store.value
            .filter { it.stoppedAt != null && it.dateKey in startDate..endDate }
            .groupBy { it.dateKey }
            .map { (key, list) ->
                DailyAggregate(
                    dateKey = key,
                    totalDurationMs = list.sumOf { it.duration ?: 0L },
                    sessionCount = list.size,
                    firstCheckIn = list.minOf { it.startedAt },
                    lastCheckOut = list.maxOf { it.stoppedAt ?: 0L }
                )
            }
            .sortedBy { it.dateKey }
}

class FakeAttendanceSettings(
    var trackingStart: LocalDate? = null,
    var schedule: List<TargetSchedule.Entry> = emptyList(),
    var targetHoursToday: Int = TargetSchedule.DEFAULT_TARGET_HOURS,
    private val seedDate: LocalDate = LocalDate.of(2026, 6, 15)
) : AttendanceSettings {
    var seedCalls = 0
    var recordedTarget: Int? = null
    var cameraDisclosureSeen = false

    override fun readSchedule(): List<TargetSchedule.Entry> = schedule
    override fun readTrackingStart(): LocalDate = trackingStart ?: seedDate
    override fun readTrackingStartOrNull(): LocalDate? = trackingStart
    override fun dailyTargetHoursToday(): Int = targetHoursToday
    override fun recordTargetChange(hours: Int) {
        recordedTarget = hours
        targetHoursToday = hours
    }
    override fun seedTrackingStartIfNeeded() {
        seedCalls++
        if (trackingStart == null) trackingStart = seedDate
    }
    override fun hasSeenCameraDisclosure(): Boolean = cameraDisclosureSeen
    override fun markCameraDisclosureSeen() { cameraDisclosureSeen = true }
}

class FakeServiceController : ServiceController {
    val started = mutableListOf<Long>()
    val startedAt = mutableListOf<Long>()
    var stopCount = 0
    var rearmCount = 0
    override fun startTimer(sessionId: Long, startedAt: Long) {
        started += sessionId
        this.startedAt += startedAt
    }
    override fun stop() { stopCount++ }
    override fun rearm() { rearmCount++ }
}

class FakeCsvExporter(var result: ExportResult = ExportResult.Success) : CsvExporter {
    var lastRange: Pair<String, String>? = null
    override suspend fun export(
        startKey: String,
        endKey: String,
        summaries: Map<String, DailySummary>
    ): ExportResult {
        lastRange = startKey to endKey
        return result
    }
}
