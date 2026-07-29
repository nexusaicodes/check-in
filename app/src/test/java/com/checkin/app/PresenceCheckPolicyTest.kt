package com.checkin.app

import com.checkin.app.service.PresenceCheckPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The retry schedule is the whole of the fix for a check that goes unanswered. It used to be that
 * there was no schedule at all: a flag latched on the first fire and only a successful re-auth ever
 * cleared it, so a session whose check was missed at 3am was never asked again and ran to check-out
 * unverified. These pin that the sequence always advances and never stops.
 */
class PresenceCheckPolicyTest {

    @Test
    fun `delays escalate and then hold`() {
        val first = PresenceCheckPolicy.retryDelayMs(1)
        val second = PresenceCheckPolicy.retryDelayMs(2)
        val third = PresenceCheckPolicy.retryDelayMs(3)

        assertTrue("expected escalation, got $first then $second", second > first)
        assertTrue("expected escalation, got $second then $third", third > second)
    }

    /**
     * The sequence must never run out. A bounded one would reintroduce the exact failure this
     * replaced — silence for the rest of a session that has already stopped counting time.
     */
    @Test
    fun `attempts past the end keep the final delay`() {
        val last = PresenceCheckPolicy.retryDelayMs(3)

        assertEquals(last, PresenceCheckPolicy.retryDelayMs(4))
        assertEquals(last, PresenceCheckPolicy.retryDelayMs(50))
        assertEquals(last, PresenceCheckPolicy.retryDelayMs(Int.MAX_VALUE))
    }

    /** A zero or negative count is a corrupted read, not a reason to schedule an instant repeat. */
    @Test
    fun `a non-positive attempt count still yields a real delay`() {
        val first = PresenceCheckPolicy.retryDelayMs(1)

        assertEquals(first, PresenceCheckPolicy.retryDelayMs(0))
        assertEquals(first, PresenceCheckPolicy.retryDelayMs(-3))
    }

    @Test
    fun `retryAt offsets from when the question was asked`() {
        val firedAt = 1_700_000_000_000L

        assertEquals(firedAt + PresenceCheckPolicy.retryDelayMs(1), PresenceCheckPolicy.retryAt(firedAt, 1))
        assertEquals(firedAt + PresenceCheckPolicy.retryDelayMs(2), PresenceCheckPolicy.retryAt(firedAt, 2))
    }

    @Test
    fun `every scheduled retry lies strictly in the future`() {
        val firedAt = 1_700_000_000_000L

        for (attempt in 1..10) {
            assertTrue(
                "attempt $attempt did not advance",
                PresenceCheckPolicy.retryAt(firedAt, attempt) > firedAt,
            )
        }
    }
}
