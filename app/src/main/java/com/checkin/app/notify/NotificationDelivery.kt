package com.checkin.app.notify

/**
 * Whether a post would actually be seen.
 *
 * Three separate switches can silence a notification and `notify` reports none of them — it returns
 * void and drops the post. Checking only the runtime permission was not enough: a user can hold
 * `POST_NOTIFICATIONS` and still have notifications off for the whole app, or have one channel set to
 * "None". For the presence check that is not merely bad analytics — it opens the pause that stops the
 * user's clock, over a question that was never asked, on a row the app gives no way to edit.
 *
 * Pure, and separate from [AndroidNotifier], because that class is Android-only and so untestable on
 * this project's JVM-only suite — which would leave the decision every caller trusts as the one part
 * of the path nothing exercises. Same reason [com.checkin.app.notify.log.EngagementRouting] sits outside
 * its receiver.
 */
object NotificationDelivery {

    /** `NotificationManagerCompat.IMPORTANCE_NONE`, restated so this file needs no Android imports. */
    const val IMPORTANCE_NONE = 0

    /**
     * [channelImportance] is null when the channel does not exist, which counts as blocked: from
     * Android 8 a post to a channel that was never created is discarded, and minSdk here is well past
     * that. Channels are created at startup, so a null means something is already wrong — and a
     * notification nobody can see is the wrong thing to report as sent.
     */
    fun canDeliver(permissionGranted: Boolean, appEnabled: Boolean, channelImportance: Int?): Boolean =
        permissionGranted && appEnabled && channelImportance != null && channelImportance != IMPORTANCE_NONE
}
