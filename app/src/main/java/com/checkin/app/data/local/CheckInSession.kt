package com.checkin.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "check_in_sessions")
data class CheckInSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "start_timestamp")
    val startTimestamp: Long,  // Unix timestamp in milliseconds

    @ColumnInfo(name = "end_timestamp")
    val endTimestamp: Long? = null,  // Nullable - null means session is active

    @ColumnInfo(name = "duration_millis")
    val durationMillis: Long? = null,  // Duration in milliseconds

    @ColumnInfo(name = "description")
    val description: String = ""  // Session description
)
