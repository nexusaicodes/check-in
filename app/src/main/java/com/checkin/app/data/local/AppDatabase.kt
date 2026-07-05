package com.checkin.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CheckInSession::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun checkInSessionDao(): CheckInSessionDao

    companion object {
        @Volatile
        private var _instance: AppDatabase? = null

        /** Adds the presence-pause columns without dropping existing sessions. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN paused_ms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN pause_started_at INTEGER")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return _instance ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "_app"
                ).addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                _instance = instance
                instance
            }
        }
    }
}
