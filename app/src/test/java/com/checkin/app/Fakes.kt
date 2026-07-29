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
import com.checkin.app.notify.NotificationSpec
import com.checkin.app.notify.Notifier
import com.checkin.app.notify.engagement.EngagementReporter
import com.checkin.app.notify.engagement.EngagementSettings
import com.checkin.app.notify.engagement.Nudge
import com.checkin.app.notify.engagement.NudgeTrigger
import com.checkin.app.notify.log.AttributionRules
import com.checkin.app.notify.log.EngagementEvent
import com.checkin.app.notify.log.EngagementEventType
import com.checkin.app.notify.log.EngagementLog
import com.checkin.app.notify.log.EngagementSource
import com.checkin.app.notify.log.PRESENCE_CHECK_KEY
import com.checkin.app.notify.log.ServiceEventType
import com.checkin.app.service.PresenceSchedule
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
            dateKey = dateKey,
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

    override suspend fun getActiveSession(): CheckInSession? = store.value.firstOrNull { it.stoppedAt == null }

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

    private fun aggregate(startDate: String, endDate: String): List<DailyAggregate> = store.value
        .filter { it.stoppedAt != null && it.dateKey in startDate..endDate }
        .groupBy { it.dateKey }
        .map { (key, list) ->
            DailyAggregate(
                dateKey = key,
                totalDurationMs = list.sumOf { it.duration ?: 0L },
                sessionCount = list.size,
                firstCheckIn = list.minOf { it.startedAt },
                lastCheckOut = list.maxOf { it.stoppedAt ?: 0L },
            )
        }
        .sortedBy { it.dateKey }
}

class FakeAttendanceSettings(
    var trackingStart: LocalDate? = null,
    var schedule: List<TargetSchedule.Entry> = emptyList(),
    var targetHoursToday: Int = TargetSchedule.DEFAULT_TARGET_HOURS,
    private val seedDate: LocalDate = LocalDate.of(2026, 6, 15),
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
    override fun markCameraDisclosureSeen() {
        cameraDisclosureSeen = true
    }
    override var presenceCheckEnabled: Boolean = true
    override var presenceCheckPauses: Boolean = true
}

class FakeServiceController : ServiceController {
    val started = mutableListOf<Long>()
    val startedAt = mutableListOf<Long>()
    var stopCount = 0
    var rearmCount = 0

    /** One entry per re-arm: true when it came from the notification tap. */
    val rearmedFromNotification = mutableListOf<Boolean>()
    var presenceSettingsChangedCount = 0
    var refreshCount = 0

    /** Set to false to stand in for a platform that refused a background foreground-service start. */
    var startAllowed = true

    /** Revives are tracked apart from check-ins: the two actions must not be interchangeable. */
    val revived = mutableListOf<Long>()

    override fun startTimer(sessionId: Long, startedAt: Long): Boolean {
        if (!startAllowed) return false
        started += sessionId
        this.startedAt += startedAt
        return true
    }

    override fun revive(sessionId: Long, startedAt: Long): Boolean {
        if (!startAllowed) return false
        revived += sessionId
        return true
    }
    override fun stop() {
        stopCount++
    }
    override fun refreshFromDb() {
        refreshCount++
    }
    override fun rearm(fromNotification: Boolean) {
        rearmCount++
        rearmedFromNotification += fromNotification
    }
    override fun presenceSettingsChanged() {
        presenceSettingsChangedCount++
    }
}

/** Records what was posted, and can refuse like a revoked POST_NOTIFICATIONS does. */
class FakeNotifier(var refuse: Boolean = false) : Notifier {
    val shown = mutableListOf<NotificationSpec>()
    val cancelled = mutableListOf<Int>()

    override fun show(spec: NotificationSpec): Boolean {
        if (refuse) return false
        shown += spec
        return true
    }

    override fun cancel(id: Int) {
        cancelled += id
    }
}

class FakeCsvExporter(var result: ExportResult = ExportResult.Success) : CsvExporter {
    var lastRange: Pair<String, String>? = null
    override suspend fun export(startKey: String, endKey: String, summaries: Map<String, DailySummary>): ExportResult {
        lastRange = startKey to endKey
        return result
    }
}

class FakeEngagementSettings(
    override var masterEnabled: Boolean = false,
    private val installId: String = "fake-install",
) : EngagementSettings {
    val enabled = mutableSetOf<Nudge>()
    val shownAt = mutableMapOf<Nudge, Long>()
    var clearHistoryCount = 0

    override fun isEnabled(nudge: Nudge) = nudge in enabled
    override fun setEnabled(nudge: Nudge, enabled: Boolean) {
        if (enabled) this.enabled += nudge else this.enabled -= nudge
    }
    override fun enabledNudges(): Set<Nudge> = if (!masterEnabled) emptySet() else enabled.toSet()
    override fun lastShownAt(): Map<Nudge, Long> = shownAt.toMap()
    override fun markShown(nudge: Nudge, atMillis: Long) {
        shownAt[nudge] = atMillis
    }
    override fun installId(): String = installId
    override fun clearHistory() {
        clearHistoryCount++
        shownAt.clear()
    }
}

/** In-memory stand-in for the engagement database, with the same attribution semantics. */
class FakeEngagementLog : EngagementLog {
    val events = MutableStateFlow<List<EngagementEvent>>(emptyList())
    var clearCount = 0
    var prunedBefore: Long? = null

    override suspend fun record(nudge: Nudge, variant: Int, event: EngagementEventType, atMillis: Long) {
        events.value = events.value + EngagementEvent(
            id = events.value.size + 1L,
            at = atMillis,
            key = nudge.name,
            variant = variant,
            event = event.name,
            source = EngagementSource.NUDGE.name,
        )
    }

    override suspend fun recordPresenceCheck(event: EngagementEventType, atMillis: Long) {
        events.value = events.value + EngagementEvent(
            id = events.value.size + 1L,
            at = atMillis,
            key = PRESENCE_CHECK_KEY,
            variant = 0,
            event = event.name,
            source = EngagementSource.PRESENCE.name,
        )
    }

    override suspend fun recordService(event: ServiceEventType, atMillis: Long, detail: String) {
        events.value = events.value + EngagementEvent(
            id = events.value.size + 1L,
            at = atMillis,
            key = detail,
            variant = 0,
            event = event.name,
            source = EngagementSource.SERVICE.name,
        )
    }

    // Mirrors the Room queries' `source` scoping. Without it the fake would answer the cap and
    // attribution questions differently from production, and a test could only prove the fake right.
    private fun nudgeEvents() = events.value.filter { it.source == EngagementSource.NUDGE.name }

    private fun lastShownWithin(atMillis: Long, windowMs: Long): EngagementEvent? =
        nudgeEvents().filter { it.event == EngagementEventType.SHOWN.name && it.at >= atMillis - windowMs }
            .maxByOrNull { it.at }

    override suspend fun recordConversionIfAttributable(atMillis: Long, windowMs: Long): Nudge? {
        val shown = lastShownWithin(atMillis, windowMs) ?: return null
        // Shares AttributionRules with the Room implementation, so the fake can't drift on the
        // decision — only on storage.
        val latestConverted = nudgeEvents()
            .filter { it.event == EngagementEventType.CONVERTED.name }
            .maxOfOrNull { it.at }
        if (!AttributionRules.canCredit(shown.at, atMillis, windowMs, latestConverted)) return null
        val nudge = Nudge.entries.firstOrNull { it.name == shown.key } ?: return null
        record(nudge, shown.variant, EngagementEventType.CONVERTED, atMillis)
        return nudge
    }

    override suspend fun recordOpenedForLastShown(atMillis: Long, windowMs: Long): Nudge? {
        val shown = lastShownWithin(atMillis, windowMs) ?: return null
        val nudge = Nudge.entries.firstOrNull { it.name == shown.key } ?: return null
        record(nudge, shown.variant, EngagementEventType.OPENED, atMillis)
        return nudge
    }

    override suspend fun shownCountSince(since: Long): Int =
        nudgeEvents().count { it.event == EngagementEventType.SHOWN.name && it.at >= since }

    override fun recent(limit: Int): Flow<List<EngagementEvent>> =
        events.map { list -> list.sortedByDescending { it.at }.take(limit) }

    override suspend fun clear() {
        clearCount++
        events.value = emptyList()
    }

    override suspend fun prune(before: Long) {
        prunedBefore = before
    }
}

class FakeEngagementReporter : EngagementReporter {
    val openedAt = mutableListOf<Long>()
    val checkedInAt = mutableListOf<Long>()

    override suspend fun onNudgeOpened(atMillis: Long) {
        openedAt += atMillis
    }
    override suspend fun onCheckedIn(atMillis: Long) {
        checkedInAt += atMillis
    }
}

class FakeNudgeTrigger : NudgeTrigger {
    var runOnceCount = 0
    val forced = mutableListOf<Pair<Nudge, Int?>>()
    var nextResult: Nudge? = null

    override suspend fun runOnce(): Nudge? {
        runOnceCount++
        return nextResult
    }

    override suspend fun forceSend(nudge: Nudge, variant: Int?): Nudge? {
        forced += nudge to variant
        return nudge
    }
}

/** Records what was armed, so the schedule can be asserted without a platform AlarmManager. */
class FakePresenceSchedule(override var attempts: Int = 0, override var refusals: Int = 0) : PresenceSchedule {
    val scheduled = mutableListOf<Long>()
    var cancelCount = 0

    val lastScheduled: Long? get() = scheduled.lastOrNull()

    override fun scheduleAt(atMillis: Long) {
        scheduled += atMillis
    }

    /** Mirrors the real seam: cancelling drops the alarm and both counters together. */
    override fun cancel() {
        cancelCount++
        attempts = 0
        refusals = 0
    }
}
