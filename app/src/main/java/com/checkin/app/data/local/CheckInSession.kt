package com.checkin.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class CheckInSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,  // Unix timestamp in milliseconds

    @ColumnInfo(name = "description")
    val description: String = "",  // Session description

    @ColumnInfo(name = "stopped_at")
    val stoppedAt: Long? = null,  // Nullable - null means session is active

    @ColumnInfo(name = "duration")
    val duration: Long? = null  // Duration in milliseconds
)
