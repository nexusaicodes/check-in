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

    @ColumnInfo(name = "punch_in_selfie")
    val punchInSelfie: String = "",

    @ColumnInfo(name = "punch_out_selfie")
    val punchOutSelfie: String = ""
)
