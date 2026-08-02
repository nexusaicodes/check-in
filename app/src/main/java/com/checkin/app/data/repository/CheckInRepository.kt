package com.checkin.app.data.repository

import com.checkin.app.data.SystemTimeSource
import com.checkin.app.data.TimeSource
import com.checkin.app.data.local.CheckInSession
import com.checkin.app.data.local.CheckInSessionDao
import com.checkin.app.data.local.DailyAggregate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CheckInRepository(private val dao: CheckInSessionDao, private val timeSource: TimeSource = SystemTimeSource) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // "yyyy-MM-dd"

    /**
     * Opens a session, or returns the one already open. Two gated paths reach a check-in — the
     * Check-In screen and a nudge tap — and either can resolve while the other's gate is still on
     * screen, so the one-open-session invariant is enforced here rather than at each call site. A
     * second open row would never be closed (`getActiveSession()` returns one) and its hours would
     * never reach `duration`.
     */
    suspend fun checkIn(): CheckInSession {
        dao.getActiveSession()?.let { return it }
        val session = CheckInSession(
            startedAt = timeSource.nowMillis(),
            dateKey = timeSource.today().format(dateFormatter),
        )
        return session.copy(id = dao.insertSession(session))
    }

    suspend fun checkOut(sessionId: Long) = checkOutAt(sessionId, timeSource.nowMillis())

    /**
     * Closes [sessionId] stamped at [atMillis] rather than now.
     *
     * The day-boundary alarm needs this: it is inexact and may land hours late, so the instant it
     * fires is not the instant the session ended. Duration is floored at zero — a stop before the
     * start means a changed system clock or a corrupt row, and a negative duration would poison
     * every total that sums it.
     */
    suspend fun checkOutAt(sessionId: Long, atMillis: Long) {
        val session = dao.getSessionById(sessionId) ?: return
        dao.updateSession(
            session.copy(
                stoppedAt = atMillis,
                duration = (atMillis - session.startedAt).coerceAtLeast(0L),
            ),
        )
    }

    /** Checks out whatever session is open, for callers that don't hold its id. False if none is. */
    suspend fun checkOutActiveSession(): Boolean {
        val active = dao.getActiveSession() ?: return false
        checkOut(active.id)
        return true
    }

    suspend fun getActiveSession(): CheckInSession? = dao.getActiveSession()

    fun activeSessionFlow(): Flow<CheckInSession?> = dao.getActiveSessionFlow()

    fun dailyAggregatesFlow(startDate: String, endDate: String): Flow<List<DailyAggregate>> =
        dao.getDailyAggregatesFlow(startDate, endDate)

    suspend fun getDailySummaries(startDate: String, endDate: String): Map<String, DailyAggregate> =
        byDateKey(dao.getDailyAggregates(startDate, endDate))

    /**
     * Keys aggregates by their day. A day present in the map is a day the user showed up; a day
     * absent from it is one they didn't — which is the whole of what the app now decides about a day.
     */
    fun byDateKey(aggregates: List<DailyAggregate>): Map<String, DailyAggregate> = aggregates.associateBy { it.dateKey }

    fun sessionsForDateFlow(dateKey: String): Flow<List<CheckInSession>> = dao.getSessionsByDateFlow(dateKey)

    suspend fun getSessionsByDate(dateKey: String): List<CheckInSession> = dao.getSessionsByDate(dateKey)

    suspend fun getDailyAggregates(startDate: String, endDate: String): List<DailyAggregate> =
        dao.getDailyAggregates(startDate, endDate)

    suspend fun getAllDateKeys(): List<String> = dao.getAllDateKeys()

    /**
     * The day tracking began — the day of the first session — or null when nothing is recorded yet.
     *
     * Derived rather than stored. It used to be a preference seeded at the first check-in, which
     * made it a second copy of a fact the table already held: a cloud restore could bring the
     * preference back without the rows it indexes, and every day since became a day the user did
     * not show up for, on the one screen they check their consistency in. Read off the sessions it
     * describes, it cannot disagree with them.
     *
     * `distinctUntilChanged` is load-bearing, not tidiness. Room re-runs the query on any write to
     * `sessions` and emits whether or not the value moved, and both History and Reports
     * `flatMapLatest` their whole aggregate pipeline off this flow — so without it every check-in and
     * check-out would tear that pipeline down and re-subscribe it to arrive at the same start it
     * already had. The value only ever changes on the very first session.
     */
    fun trackingStartFlow(): Flow<LocalDate?> = dao.getFirstDateKeyFlow().map(::parseDateKey).distinctUntilChanged()

    suspend fun trackingStart(): LocalDate? = parseDateKey(dao.getFirstDateKey())

    /** Null rather than a throw on a malformed key, matching how the formatters treat one. */
    private fun parseDateKey(dateKey: String?): LocalDate? =
        dateKey?.let { runCatching { LocalDate.parse(it, dateFormatter) }.getOrNull() }

    suspend fun getSessionsByDateRange(startDate: String, endDate: String): List<CheckInSession> =
        dao.getSessionsByDateRange(startDate, endDate)
}
