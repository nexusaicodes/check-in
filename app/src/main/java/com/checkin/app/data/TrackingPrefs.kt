package com.checkin.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Central definition of the `tracking_prefs` SharedPreferences namespace, its keys, and readers. */
object TrackingPrefs {
    const val NAME = "tracking_prefs"
    const val KEY_TRACKING_START_DATE = "tracking_start_date"
    const val KEY_CAMERA_DISCLOSURE_SEEN = "camera_disclosure_seen"
    const val KEY_NOTIFICATIONS_ASKED = "notifications_asked"

    /** The namespace this replaced, read once per install by [migrateFromLegacy]. */
    private const val LEGACY_NAME = "attendance_prefs"

    /** Set in the new file once the copy has run, so the legacy file is never read again. */
    private const val KEY_MIGRATED = "migrated_from_attendance_prefs"

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Copies the surviving keys out of `attendance_prefs` and deletes it.
     *
     * Only three keys carry over. **`tracking_start_date` is the one that must not be lost** — it
     * anchors every window the app computes, and losing it silently resets a user's entire history
     * to "tracking started today". The rest of the old file is deliberately dropped:
     * `daily_target_hours` and `target_schedule` belonged to the target that no longer exists, and
     * `presence_check_enabled` / `presence_check_pauses` to the mid-session check that no longer
     * runs. Carrying them would preserve settings for features nothing reads.
     *
     * Guarded by a flag in the *new* file rather than by the old file's existence, so a legacy file
     * that reappears (a restore from an old backup) can't re-run the copy over fresher values.
     * Idempotent, and safe to call on a fresh install where the legacy file was never created.
     */
    fun migrateFromLegacy(context: Context) {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        val legacy = context.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        // Only values actually present are copied, so a re-run can never overwrite a newer value
        // with a default.
        legacy.getString(KEY_TRACKING_START_DATE, null)?.let { editor.putString(KEY_TRACKING_START_DATE, it) }
        if (legacy.getBoolean(KEY_CAMERA_DISCLOSURE_SEEN, false)) {
            editor.putBoolean(KEY_CAMERA_DISCLOSURE_SEEN, true)
        }
        if (legacy.getBoolean(KEY_NOTIFICATIONS_ASKED, false)) {
            editor.putBoolean(KEY_NOTIFICATIONS_ASKED, true)
        }
        editor.putBoolean(KEY_MIGRATED, true)

        // `commit`, and its result is checked, because the delete that follows is irreversible. A
        // stranded legacy file costs nothing and the next launch retries; deleting after a failed
        // write would lose the tracking start for good, resetting the user's whole history to
        // "started today". One synchronous write of three keys, once per install.
        if (editor.commit()) {
            context.deleteSharedPreferences(LEGACY_NAME)
        }
    }

    /** Whether the camera prominent-disclosure screen has already been shown and accepted. */
    fun hasSeenCameraDisclosure(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_CAMERA_DISCLOSURE_SEEN, false)

    /**
     * Whether the launch-time notification permission request has already been made.
     *
     * Persisted rather than derived from the grant state, because "refused" and "not yet asked" look
     * identical through [android.content.pm.PackageManager] — without this the app would re-request
     * on every cold start, and Android silently drops the dialog after two refusals, so the user
     * would see nothing while the app kept asking.
     */
    fun hasAskedNotifications(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_ASKED, false)

    /** The stored tracking start, or null before the first authenticated check-in seeds it. */
    fun readTrackingStartOrNull(prefs: SharedPreferences): LocalDate? =
        prefs.getString(KEY_TRACKING_START_DATE, null)?.let { LocalDate.parse(it, dateFormatter) }

    /** The tracking start, falling back to today when tracking hasn't begun. */
    fun readTrackingStart(prefs: SharedPreferences): LocalDate = readTrackingStartOrNull(prefs) ?: LocalDate.now()
}
