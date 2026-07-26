package com.checkin.app.notify.log

import com.checkin.app.notify.engagement.Nudge

/** What a dismissal broadcast turned out to be about. */
sealed interface DismissTarget {
    data class NudgeDismissal(val nudge: Nudge, val variant: Int) : DismissTarget
    data object PresenceDismissal : DismissTarget
}

/**
 * Maps a dismissal broadcast's payload back to the notification that was dismissed.
 *
 * Kept pure and separate from the receiver, which is Android-only and would otherwise be the one
 * place a mis-route could hide: a presence check resolved as a nudge would be written through the
 * nudge entry point, where it would count against the daily cap and sit at the head of the
 * attribution queries — exactly the interference the `source` column exists to prevent.
 */
object DismissRouting {

    /**
     * Null when the payload no longer names anything the app can act on: a malformed extra, or a
     * nudge that has since been renamed or removed. Dropping it matches how
     * [RoomEngagementLog]'s own name lookup already handles a retired experiment.
     */
    fun resolve(source: String?, key: String?, variant: Int): DismissTarget? = when (source) {
        EngagementSource.PRESENCE.name ->
            if (key == PRESENCE_CHECK_KEY) DismissTarget.PresenceDismissal else null

        EngagementSource.NUDGE.name ->
            Nudge.entries.firstOrNull { it.name == key }
                ?.let { DismissTarget.NudgeDismissal(it, variant) }

        else -> null
    }
}
