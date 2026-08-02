package com.checkin.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CheckInSession::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun checkInSessionDao(): CheckInSessionDao

    companion object {
        @Volatile
        private var cached: AppDatabase? = null

        /** Adds the presence-pause columns without dropping existing sessions. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN paused_ms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN pause_started_at INTEGER")
            }
        }

        /** Drops the vestigial selfie columns; selfies are transient and never persisted. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions DROP COLUMN punch_in_selfie")
                db.execSQL("ALTER TABLE sessions DROP COLUMN punch_out_selfie")
            }
        }

        /**
         * Drops the presence-pause columns along with the mechanism that wrote them.
         *
         * **Completed** rows are untouched: their `duration` was already stored net of paused time,
         * so only the audit trail of *why* it was shorter than the wall-clock span is lost, and
         * nothing reads that.
         *
         * A session still **open** at upgrade time loses whatever pause it had accumulated, and is
         * recorded at its full wall-clock span when it closes. That is deliberate rather than
         * overlooked: the model this migration belongs to never subtracts from an interval, and the
         * alternatives are worse — closing the row silently ends a session the user may still be in,
         * and folding the pause into `started_at` rewrites the check-in time they see on screen.
         * Over-counting one session beats editing a row the app gives no way to edit. The blast
         * radius is bounded by the day-boundary close, which `SessionWatchdog` arms on the first app
         * open after the upgrade: a session left open past its own midnight is closed *at* that
         * midnight, so what survives is at most a single same-day pause.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions DROP COLUMN paused_ms")
                db.execSQL("ALTER TABLE sessions DROP COLUMN pause_started_at")
            }
        }

        fun getDatabase(context: Context): AppDatabase = cached ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "_app",
            ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
            cached = instance
            instance
        }
    }
}
