package com.checkin.app.notify.log

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Engagement analytics live in their own database, deliberately separate from the attendance DB
 * (`_app`). Nothing here is user data the app promises to keep: an experiment redesign can drop and
 * recreate this file without migrating, and no schema change here can put the user's attendance
 * records at risk or widen what the CSV export covers.
 */
@Database(entities = [EngagementEvent::class], version = 1, exportSchema = false)
abstract class EngagementDatabase : RoomDatabase() {
    abstract fun engagementEventDao(): EngagementEventDao

    companion object {
        @Volatile
        private var _instance: EngagementDatabase? = null

        fun getDatabase(context: Context): EngagementDatabase {
            return _instance ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EngagementDatabase::class.java,
                    "engagement.db"
                )
                    // Safe here in a way it would not be for `_app`: losing analytics history costs
                    // an experiment's data, not a user's attendance record.
                    .fallbackToDestructiveMigration()
                    .build()
                _instance = instance
                instance
            }
        }
    }
}
