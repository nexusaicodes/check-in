package com.checkin.app.notify.engagement

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

/**
 * Read/write seam over the `engagement_prefs` namespace, mirroring [com.checkin.app.platform.PromptSettings]
 * so the ViewModel and the dispatcher stay pure-JVM testable with fakes.
 *
 * Deliberately its own namespace: wiping engagement state can never disturb the one-time prompt
 * flags in `prompt_prefs`.
 */
interface EngagementSettings {
    var masterEnabled: Boolean

    fun isEnabled(nudge: Nudge): Boolean
    fun setEnabled(nudge: Nudge, enabled: Boolean)

    /** Empty when the master switch is off, so one toggle disables everything downstream. */
    fun enabledNudges(): Set<Nudge>

    /** Stable per-install id for variant bucketing. Random, local, and never leaves the device. */
    fun installId(): String
}

class SharedPrefsEngagementSettings(private val prefs: SharedPreferences) : EngagementSettings {

    companion object {
        const val NAME = "engagement_prefs"

        private const val KEY_MASTER_ENABLED = "master_enabled"
        private const val KEY_INSTALL_ID = "install_id"
        private const val PREFIX_ENABLED = "nudge_enabled_"

        fun create(context: Context) = SharedPrefsEngagementSettings(
            context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE),
        )
    }

    /**
     * Nudges are **on by default**, master switch and each nudge alike.
     *
     * Opt-in reads as the cautious choice and is not: the only nudge that exists tells a user they
     * have not checked in today, and a user who has not formed the habit is exactly the one who never
     * goes looking through Settings to enable a reminder about it — off by default, the feature
     * reaches nobody it is built for. Turning it off is one tap in Settings, Android's per-channel
     * controls sit behind that, and the daily cap bounds it to one message a day regardless.
     */
    override var masterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_MASTER_ENABLED, value) }

    override fun isEnabled(nudge: Nudge): Boolean = prefs.getBoolean(PREFIX_ENABLED + nudge.name, true)

    override fun setEnabled(nudge: Nudge, enabled: Boolean) {
        prefs.edit { putBoolean(PREFIX_ENABLED + nudge.name, enabled) }
    }

    override fun enabledNudges(): Set<Nudge> =
        if (!masterEnabled) emptySet() else Nudge.entries.filter { isEnabled(it) }.toSet()

    override fun installId(): String = prefs.getString(KEY_INSTALL_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit { putString(KEY_INSTALL_ID, it) }
    }
}
