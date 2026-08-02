package com.checkin.app.notify.log

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Engagement analytics live in their own database, deliberately separate from the sessions DB
 * (`_app`). Nothing here is user data the app promises to keep: an experiment redesign can drop and
 * recreate this file without migrating, and no schema change here can put the user's session
 * records at risk or widen what the CSV export covers.
 */
@Database(entities = [EngagementEvent::class], version = 2, exportSchema = false)
abstract class EngagementDatabase : RoomDatabase() {
    abstract fun engagementEventDao(): EngagementEventDao

    companion object {
        /**
         * Adds the `source` column that separates nudge rows from the rest. The `NUDGE` default is
         * correct for every pre-existing row — all of them were written by the nudge dispatcher — so
         * the frequency cap and attribution queries see an unchanged history.
         *
         * Written out rather than left to the destructive fallback, which would drop the send history
         * the cap counts from: an install upgrading mid-day would get a second nudge it already had.
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
                // history costs an experiment's data, not a user's session record.
                .fallbackToDestructiveMigration()
                .build()
            cached = instance
            instance
        }
    }
}
