package com.checkin.app.notify

import com.checkin.app.notify.log.EngagementSource

/**
 * Identifies a notification in the engagement log, so what the user did with it is attributed to the
 * notification itself rather than inferred from whichever was posted most recently.
 *
 * Carried on both intents a notification hands out: the tap intent and the delete intent. The two use
 * different extra keys because the delete intent's are frozen — a notification posted by an earlier
 * release survives an update holding the `PendingIntent` it was built with, and renaming what the
 * receiver reads drops that notification's dismissal on the floor.
 *
 * Only the delete intent carries [source]. A tap is routed by its own launch extra long before the
 * tag is read, so the handler already knows which subsystem it is holding.
 */
data class EngagementTag(val source: EngagementSource, val key: String, val variant: Int) {
    companion object {
        const val EXTRA_KEY = "engagement_key"
        const val EXTRA_VARIANT = "engagement_variant"
    }
}
