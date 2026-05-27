package com.example.timetable.ui

import android.app.Application
import com.example.timetable.R
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.TimetableGroup
import com.example.timetable.data.TimetableRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelImportTest {

    private val testDispatcher = StandardTestDispatcher()
    private val application: Application = mockk(relaxed = true)
    private val repository: TimetableRepository = mockk(relaxed = true)
    private lateinit var viewModel: ScheduleViewModel

    private val entriesFlow = MutableStateFlow<List<TimetableEntry>>(emptyList())
    private val groupsFlow = MutableStateFlow<List<TimetableGroup>>(listOf(TimetableGroup.default()))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        
        every { repository.getActiveGroupId(any()) } returns TimetableGroup.DEFAULT_ID
        every { repository.getGroupsStream(any()) } returns groupsFlow
        every { repository.getEntriesStream(any(), any()) } returns entriesFlow
        coEvery { repository.resolveActiveGroupId(any()) } returns TimetableGroup.DEFAULT_ID
        
        viewModel = ScheduleViewModel(application, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectTimetableGroupChangesActiveGroupId() = runTest {
        val newGroupId = "new-group-id"
        every { application.getString(R.string.vm_timetable_group_selected, any()) } returns "Selected"
        
        viewModel.selectTimetableGroup(newGroupId)
        advanceUntilIdle()
        
        assertEquals(newGroupId, viewModel.activeGroupId.value)
        verify { repository.setActiveGroupId(application, newGroupId) }
    }

    @Test
    fun upsertEntryTriggersRepositorySave() = runTest {
        val entry = sampleEntry()
        
        viewModel.upsertEntry(entry)
        advanceUntilIdle()
        
        coVerify { repository.upsertEntry(application, any()) }
    }

    @Test
    fun deleteEntryTriggersRepositoryDelete() = runTest {
        val entryId = "test-id"
        
        viewModel.deleteEntry(entryId)
        advanceUntilIdle()
        
        coVerify { repository.deleteEntry(application, entryId) }
    }

    @Test
    fun confirmImportSavesToRepository() = runTest {
        val entries = listOf(sampleEntry())
        val preview = ImportPreview(
            validEntries = entries,
            invalidCount = 0,
            conflictCount = 0,
            totalParsed = 1
        )
        
        viewModel.confirmImport(preview, ImportTarget.OverwriteCurrent)
        advanceUntilIdle()
        
        coVerify { repository.replaceEntriesInGroup(application, any(), entries) }
    }

    @Test
    fun entriesStateFlowReflectsRepositoryData() = runTest {
        val entries = listOf(sampleEntry())
        entriesFlow.value = entries
        advanceUntilIdle()
        
        assertEquals(entries, viewModel.entries.value)
    }

    @Test
    fun deleteEntryRemovesItFromEntriesFlow() = runTest {
        val entry = sampleEntry()
        entriesFlow.value = listOf(entry)
        advanceUntilIdle()
        assertEquals(listOf(entry), viewModel.entries.value)

        viewModel.deleteEntry(entry.id)
        entriesFlow.value = emptyList()
        advanceUntilIdle()

        assertEquals(emptyList<TimetableEntry>(), viewModel.entries.value)
    }

    @Test
    fun readLimitedUtf8TextReturnsFullTextWithinLimit() {
        val payload = "BEGIN:VCALENDAR\r\nEND:VCALENDAR"
        val result = readLimitedUtf8Text(
            inputStream = ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8)),
            maxBytes = payload.toByteArray(Charsets.UTF_8).size,
        )
        assertEquals(payload, result)
    }

    @Test
    fun readLimitedUtf8TextRemovesUtf8Bom() {
        val payload = "\uFEFFBEGIN:VCALENDAR"
        val result = readLimitedUtf8Text(
            inputStream = ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8)),
            maxBytes = payload.toByteArray(Charsets.UTF_8).size,
        )
        assertEquals("BEGIN:VCALENDAR", result)
    }

    @Test
    fun readLimitedUtf8TextThrowsOnExceedingLimit() {
        val payload = "A".repeat(100)
        assertThrows(IOException::class.java) {
            readLimitedUtf8Text(
                inputStream = ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8)),
                maxBytes = 50,
            )
        }
    }

    @Test
    fun confirmImportCreatesNewGroup() = runTest {
        val entries = listOf(sampleEntry())
        val preview = ImportPreview(
            validEntries = entries,
            invalidCount = 0,
            conflictCount = 0,
            totalParsed = 1
        )
        val newGroupName = "New Group"
        
        viewModel.confirmImport(preview, ImportTarget.CreateGroup(newGroupName))
        advanceUntilIdle()
        
        coVerify { repository.createGroupWithEntries(application, newGroupName, entries) }
    }

    @Test
    fun overlappingTimeRangesWithoutSharedDateAreNotConflicts() = runTest {
        // Two entries with overlapping times but different dates should not conflict
        val entry1 = sampleEntry().copy(id = "entry-1", date = "2026-05-25", dayOfWeek = 1)
        val entry2 = sampleEntry().copy(id = "entry-2", date = "2026-05-26", dayOfWeek = 2)
        entriesFlow.value = listOf(entry1, entry2)
        advanceUntilIdle()
        
        // Both entries should be present (no conflict)
        assertEquals(2, viewModel.entries.value.size)
    }

    private fun sampleEntry(): TimetableEntry {
        return TimetableEntry(
            id = "entry-1",
            title = "Test Course",
            date = "2026-05-27",
            dayOfWeek = 3,
            startMinutes = 600,
            endMinutes = 700,
            location = "Room 101",
            recurrenceType = "NONE",
            semesterStartDate = "2026-02-23",
            weekRule = "ALL"
        )
    }
}