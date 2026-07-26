package com.checkin.app.notify.engagement

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

/**
 * Read/write seam over the `engagement_prefs` namespace, mirroring [com.checkin.app.di.AttendanceSettings]
 * so the ViewModel and the dispatcher stay pure-JVM testable with fakes.
 *
 * Deliberately its own namespace: wiping engagement state can never disturb the tracking start or
 * the target schedule in `attendance_prefs`.
 */
interface EngagementSettings {
    var masterEnabled: Boolean

    fun isEnabled(nudge: Nudge): Boolean
    fun setEnabled(nudge: Nudge, enabled: Boolean)

    /** Empty when the master switch is off, so one toggle disables everything downstream. */
    fun enabledNudges(): Set<Nudge>

    fun lastShownAt(): Map<Nudge, Long>
    fun markShown(nudge: Nudge, atMillis: Long)

    /** Stable per-install id for variant bucketing. Random, local, and never leaves the device. */
    fun installId(): String

    fun clearHistory()
}

class SharedPrefsEngagementSettings(private val prefs: SharedPreferences) : EngagementSettings {

    companion object {
        const val NAME = "engagement_prefs"

        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_INSTALL_ID = "install_id"
        private const val PREFIX_ENABLED = "nudge_enabled_"
        private const val PREFIX_LAST_SHOWN = "last_shown_"

        fun create(context: Context) = SharedPrefsEngagementSettings(
            context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE),
        )
    }

    /**
     * Nudges are opt-in. Shipping the foundation must not start messaging existing users who never
     * asked for it, so both the master switch and every individual nudge default to off.
     */
    override var masterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_MASTER_ENABLED, value) }

    override fun isEnabled(nudge: Nudge): Boolean = prefs.getBoolean(PREFIX_ENABLED + nudge.name, false)

    override fun setEnabled(nudge: Nudge, enabled: Boolean) {
        prefs.edit { putBoolean(PREFIX_ENABLED + nudge.name, enabled) }
    }

    override fun enabledNudges(): Set<Nudge> =
        if (!masterEnabled) emptySet() else Nudge.entries.filter { isEnabled(it) }.toSet()

    override fun lastShownAt(): Map<Nudge, Long> = Nudge.entries.mapNotNull { nudge ->
        val at = prefs.getLong(PREFIX_LAST_SHOWN + nudge.name, 0L)
        if (at > 0L) nudge to at else null
    }.toMap()

    override fun markShown(nudge: Nudge, atMillis: Long) {
        prefs.edit { putLong(PREFIX_LAST_SHOWN + nudge.name, atMillis) }
    }

    override fun installId(): String = prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit { putString(KEY_INSTALL_ID, it) }
    }

    override fun clearHistory() {
        prefs.edit {
            Nudge.entries.forEach { remove(PREFIX_LAST_SHOWN + it.name) }
        }
    }
}
