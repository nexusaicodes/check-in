package com.checkin.app

import com.checkin.app.ui.settings.NotificationBlock
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the Settings warning card tells the user, given the three switches that can silence a
 * notification. The distinction it draws is load-bearing: the copy for "everything is off" is wrong
 * for "the timer channel alone is muted", and both are states an install can sit in permanently,
 * since Android stops showing the permission dialog after two refusals.
 *
 * The counterpart of [NotificationDeliveryTest] — that one pins whether a post is delivered, this one
 * pins whether the user is told it wasn't.
 */
class NotificationBlockTest {

    private val default = 3 // NotificationManager.IMPORTANCE_DEFAULT
    private val none = 0 // NotificationManagerCompat.IMPORTANCE_NONE

    @Test
    fun `everything enabled warns about nothing`() {
        assertEquals(
            NotificationBlock.NONE,
            NotificationBlock.classify(
                permissionGranted = true,
                appEnabled = true,
                timerChannelImportance = default,
            ),
        )
    }

    @Test
    fun `a revoked permission reports everything off`() {
        assertEquals(
            NotificationBlock.ALL,
            NotificationBlock.classify(
                permissionGranted = false,
                appEnabled = true,
                timerChannelImportance = default,
            ),
        )
    }

    @Test
    fun `notifications off app-wide reports everything off, even with the permission held`() {
        assertEquals(
            NotificationBlock.ALL,
            NotificationBlock.classify(
                permissionGranted = true,
                appEnabled = false,
                timerChannelImportance = default,
            ),
        )
    }

    /** The state behind "I had everything enabled": the permission is held, one channel is not. */
    @Test
    fun `a muted timer channel reports the partial block, not the total one`() {
        assertEquals(
            NotificationBlock.CHANNELS,
            NotificationBlock.classify(
                permissionGranted = true,
                appEnabled = true,
                timerChannelImportance = none,
            ),
        )
    }

    /**
     * A missing channel is not the user's doing — all three are created at startup — so it must not
     * raise a card blaming them for it.
     */
    @Test
    fun `a channel that does not exist warns about nothing`() {
        assertEquals(
            NotificationBlock.NONE,
            NotificationBlock.classify(
                permissionGranted = true,
                appEnabled = true,
                timerChannelImportance = null,
            ),
        )
    }

    /** App-wide off outranks the channel check, so the card never says "some" when it means "all". */
    @Test
    fun `everything off outranks a muted channel`() {
        assertEquals(
            NotificationBlock.ALL,
            NotificationBlock.classify(
                permissionGranted = false,
                appEnabled = false,
                timerChannelImportance = none,
            ),
        )
    }
}
