package com.checkin.app.notify.log

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Engagement analytics live in their own database, deliberately separate from the attendance DB
 * (`_app`). Nothing here is user data the app promises to keep: an experiment redesign can drop and
 * recreate this file without migrating, and no schema change here can put the user's attendance
 * records at risk or widen what the CSV export covers.
 */
@Database(entities = [EngagementEvent::class], version = 2, exportSchema = false)
abstract class EngagementDatabase : RoomDatabase() {
    abstract fun engagementEventDao(): EngagementEventDao

    companion object {
        /**
         * Adds the `source` column that separates nudge rows from presence-check rows. Every row that
         * predates it was written by the nudge dispatcher, which is exactly what the default records —
         * so the frequency cap and attribution queries keep seeing the same history they saw before.
         *
         * Written out rather than left to the destructive fallback because the fallback would drop a
         * user's send history, and the cap counts from that history: an install upgrading mid-day
         * would silently get a second nudge it had already been sent.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE engagement_events " +
                        "ADD COLUMN source TEXT NOT NULL DEFAULT 'NUDGE'",
                )
            }
        }

        @Volatile
        private var cached: EngagementDatabase? = null

        fun getDatabase(context: Context): EngagementDatabase = cached ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                EngagementDatabase::class.java,
                "engagement.db",
            )
                .addMigrations(MIGRATION_1_2)
                // Backstop only. Safe here in a way it would not be for `_app`: losing analytics
                // history costs an experiment's data, not a user's attendance record.
                .fallbackToDestructiveMigration()
                .build()
            cached = instance
            instance
        }
    }
}
