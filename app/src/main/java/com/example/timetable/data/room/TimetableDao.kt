package com.example.timetable.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.timetable.data.TimetableEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {
    @Query("SELECT * FROM timetable_entries ORDER BY date ASC, startMinutes ASC")
    fun getAllEntriesStream(): Flow<List<TimetableEntry>>

    @Query("SELECT * FROM timetable_entries ORDER BY date ASC, startMinutes ASC")
    suspend fun getAllEntries(): List<TimetableEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: TimetableEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntries(entries: List<TimetableEntry>)

    @Query("DELETE FROM timetable_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: String)

    @Query("DELETE FROM timetable_entries")
    suspend fun deleteAll()
}
