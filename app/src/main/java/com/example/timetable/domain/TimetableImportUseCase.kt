package com.example.timetable.domain

import android.app.Application
import com.example.timetable.R
import com.example.timetable.data.EntryValidator
import com.example.timetable.data.MAX_EXPANDED_OCCURRENCES
import com.example.timetable.data.RecurrenceType
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.WeekRule
import com.example.timetable.data.countConflictPairs
import com.example.timetable.data.countConflictPairsBetween
import com.example.timetable.data.normalizeWeekListText
import com.example.timetable.data.resolveRecurrenceType
import com.example.timetable.data.resolveWeekRule
import com.example.timetable.ui.ImportPreview
import com.example.timetable.ui.suggestedImportGroupName

/**
 * Use case for importing timetable entries.
 *
 * This class encapsulates the business logic for parsing, validating, and previewing
 * timetable imports. It separates import logic from the ViewModel, following the
 * Single Responsibility Principle.
 *
 * @param application Application context for string resources
 */
class TimetableImportUseCase(private val application: Application) {

    /**
     * Build an import preview from imported entries.
     *
     * This method normalizes and validates imported entries, checks for conflicts,
     * and returns a preview that can be shown to the user before committing the import.
     *
     * @param imported List of entries to import
     * @param existingEntries List of existing entries to check conflicts against
     * @param sourceName Name of the import source (e.g., calendar name)
     * @param activeGroupId Current active group ID for default assignment
     * @return ImportPreview containing valid entries, conflict counts, and metadata
     */
    fun buildImportPreview(
        imported: List<TimetableEntry>,
        existingEntries: List<TimetableEntry>,
        sourceName: String,
        activeGroupId: String
    ): ImportPreview {
        val validEntries = mutableListOf<TimetableEntry>()
        var invalidCount = 0

        imported.map { normalizeEntry(it, activeGroupId) }
            .forEach { entry ->
                if (validateEntry(entry) != null) {
                    invalidCount++
                    return@forEach
                }
                validEntries += entry
            }

        val internalConflictCount = countConflictPairs(validEntries)
        val existingConflictCount = countConflictPairsBetween(validEntries, existingEntries)
        val conflictCount = internalConflictCount + existingConflictCount
        val truncated = imported.size >= MAX_EXPANDED_OCCURRENCES

        val defaultGroupName = application.getString(R.string.default_imported_group_name)
        return ImportPreview(
            validEntries = validEntries,
            invalidCount = invalidCount,
            conflictCount = conflictCount,
            totalParsed = imported.size,
            truncated = truncated,
            sourceName = sourceName,
            suggestedGroupName = suggestedImportGroupName(sourceName, defaultGroupName),
            internalConflictCount = internalConflictCount,
            existingConflictCount = existingConflictCount,
        )
    }

    /**
     * Normalize an entry's fields for consistent storage.
     *
     * This method trims whitespace, resolves enum values, and normalizes
     * week list text to ensure consistent formatting.
     *
     * @param entry Entry to normalize
     * @param activeGroupId Current active group ID for default assignment
     * @return Normalized entry
     */
    fun normalizeEntry(entry: TimetableEntry, activeGroupId: String): TimetableEntry {
        val normalizedCustomWeekList = normalizeWeekListText(entry.customWeekList)
        val normalizedSkipWeekList = normalizeWeekListText(entry.skipWeekList)
        val recurrence = resolveRecurrenceType(entry.recurrenceType) ?: RecurrenceType.NONE
        val weekRule = resolveWeekRule(entry.weekRule) ?: WeekRule.ALL
        return entry.copy(
            title = entry.title.trim(),
            groupId = entry.groupId.ifBlank { activeGroupId },
            location = entry.location.trim(),
            note = entry.note.trim(),
            recurrenceType = recurrence.name,
            semesterStartDate = entry.semesterStartDate.trim(),
            weekRule = weekRule.name,
            customWeekList = normalizedCustomWeekList,
            skipWeekList = normalizedSkipWeekList,
        )
    }

    /**
     * Validate an entry and return an error message if invalid.
     *
     * @param entry Entry to validate
     * @return Error message string, or null if valid
     */
    fun validateEntry(entry: TimetableEntry): String? {
        val error = EntryValidator.validate(entry) ?: return null
        return application.getString(error.messageResId)
    }
}
