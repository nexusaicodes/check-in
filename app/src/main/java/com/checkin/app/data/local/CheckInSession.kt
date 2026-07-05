package com.checkin.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class CheckInSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "stopped_at")
    val stoppedAt: Long? = null,

    @ColumnInfo(name = "duration")
    val duration: Long? = null,

    @ColumnInfo(name = "date_key")
    val dateKey: String,

    // Presence-pause accounting: [pausedMs] is the total unverified time folded out of this session,
    // and [pauseStartedAt] marks an open pause (a fired-but-unacknowledged presence check). Net worked
    // time is `stopped_at - started_at - paused_ms`, so a paused clock stops accruing time.
    @ColumnInfo(name = "paused_ms")
    val pausedMs: Long = 0,

    @ColumnInfo(name = "pause_started_at")
    val pauseStartedAt: Long? = null
)
