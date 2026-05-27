package com.example.timetable.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Interface for timetable data repository.
 *
 * This abstraction enables dependency injection and mocking for unit tests.
 * The concrete implementation (TimetableRepository) handles Room database operations.
 */
interface ITimetableRepository {
    suspend fun ensureMigrated(context: Context)
    fun getGroupsStream(context: Context): Flow<List<TimetableGroup>>
    fun getActiveGroupId(context: Context): String
    suspend fun resolveActiveGroupId(context: Context): String
    fun setActiveGroupId(context: Context, groupId: String)
    fun getEntriesStream(context: Context, groupId: String = TimetableGroup.DEFAULT_ID): Flow<List<TimetableEntry>>
    suspend fun getEntriesNow(context: Context): List<TimetableEntry>
    suspend fun upsertEntry(context: Context, entry: TimetableEntry)
    suspend fun deleteEntry(context: Context, entryId: String)
    suspend fun replaceAllEntries(context: Context, entries: List<TimetableEntry>)
    suspend fun replaceEntriesInGroup(context: Context, groupId: String, entries: List<TimetableEntry>)
    suspend fun mergeEntries(context: Context, entries: List<TimetableEntry>)
    suspend fun mergeEntries(context: Context, groupId: String, entries: List<TimetableEntry>)
    suspend fun createGroup(context: Context, name: String): TimetableGroup
    suspend fun createGroupWithEntries(context: Context, name: String, entries: List<TimetableEntry>): TimetableGroup
}
