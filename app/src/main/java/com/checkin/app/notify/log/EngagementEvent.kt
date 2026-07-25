package com.checkin.app.notify.log

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** What happened to a notification we sent. */
enum class EngagementEventType {
    /** Posted to the system tray. */
    SHOWN,

    /** The user tapped it. */
    OPENED,

    /** The user swiped it away. */
    DISMISSED,

    /** The user checked in soon enough after a SHOWN for it to plausibly be the cause. */
    CONVERTED
}

/**
 * One notification lifecycle event. [nudge] and [event] are stored as names rather than ordinals so
 * reordering an enum can't silently reinterpret history.
 */
@Entity(tableName = "engagement_events")
data class EngagementEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "at")
    val at: Long,

    @ColumnInfo(name = "nudge")
    val nudge: String,

    /** Which copy variant was used, so conversion can be compared across variants. */
    @ColumnInfo(name = "variant")
    val variant: Int,

    @ColumnInfo(name = "event")
    val event: String
)
