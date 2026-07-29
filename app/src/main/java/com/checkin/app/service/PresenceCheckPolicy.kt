package com.checkin.app.service

/**
 * When to ask again after a presence check goes unanswered.
 *
 * The check used to be a single shot: a flag latched the moment it fired and only a successful
 * re-auth ever cleared it, so one missed question meant silence for the rest of the session however
 * long it ran. That is how a check-in forgotten overnight became a sixteen-hour record with nothing
 * to show for it — one notification at 3am, ignored, and then nothing until the user noticed the
 * next afternoon. A session that has stopped accruing time is exactly the session most worth asking
 * about, so an unanswered check now schedules the next one.
 *
 * The delays escalate and then hold: close enough together that a genuine mistake is caught within
 * the hour, far enough apart that a user who is deliberately ignoring it is not harassed all night.
 * They never stop, because the alternative is the failure this replaced — and each retry is posted
 * silently (see the reminder spec), so repeats accumulate on the shade rather than buzzing.
 */
object PresenceCheckPolicy {

    private const val MINUTE_MS = 60L * 1_000L
    private const val FIRST_RETRY_MS = 30L * MINUTE_MS
    private const val SECOND_RETRY_MS = 60L * MINUTE_MS
    private const val STEADY_RETRY_MS = 120L * MINUTE_MS

    /**
     * Gap before the next attempt, indexed by how many have already been made. The last entry
     * repeats for every attempt beyond the list, so the sequence never runs out.
     */
    private val RETRY_DELAYS_MS = listOf(FIRST_RETRY_MS, SECOND_RETRY_MS, STEADY_RETRY_MS)

    /** The gap after [attemptsMade] unanswered checks. Clamped to the final delay, never zero. */
    fun retryDelayMs(attemptsMade: Int): Long =
        RETRY_DELAYS_MS[attemptsMade.coerceIn(1, RETRY_DELAYS_MS.size) - 1]

    /**
     * When to ask again, given the instant the unanswered check [firedAtMs] was posted and how many
     * attempts have been made including that one.
     */
    fun retryAt(firedAtMs: Long, attemptsMade: Int): Long = firedAtMs + retryDelayMs(attemptsMade)
}
