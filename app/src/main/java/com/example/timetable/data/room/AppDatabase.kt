package com.example.timetable.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.timetable.data.TimetableEntry

@Database(entities = [TimetableEntry::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun timetableDao(): TimetableDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "timetable_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timetable_entries ADD COLUMN recurrenceType TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE timetable_entries ADD COLUMN semesterStartDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE timetable_entries ADD COLUMN weekRule TEXT NOT NULL DEFAULT 'ALL'")
                db.execSQL("ALTER TABLE timetable_entries ADD COLUMN customWeekList TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE timetable_entries ADD COLUMN skipWeekList TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
