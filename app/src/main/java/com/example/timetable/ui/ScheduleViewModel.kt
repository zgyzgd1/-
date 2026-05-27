package com.example.timetable.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.R
import com.example.timetable.data.IcsCalendar
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.TimetableGroup
import com.example.timetable.data.TimetableRepository
import com.example.timetable.data.findConflictForEntry
import com.example.timetable.data.formatMinutes
import com.example.timetable.data.parseEntryDate
import com.example.timetable.data.suggestAdjustedEntryAfterConflicts
import com.example.timetable.domain.TimetableImportUseCase
import com.example.timetable.notify.CourseReminderScheduler
import com.example.timetable.notify.ReminderFallbackWorker
import com.example.timetable.widget.TimetableWidgetUpdater
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val MAX_ICS_IMPORT_BYTES = 1024 * 1024
private const val ENTRY_SIDE_EFFECT_DEBOUNCE_MS = 300L

/**
 * 课程表视图模型。
 *
 * 管理课程表数据的核心视图模型，负责处理课程的添加、编辑、删除、导入/导出等操作。
 *
 * @param application 应用实例
 */
class ScheduleViewModel(
    application: Application,
    private val repository: TimetableRepository = TimetableRepository,
) : AndroidViewModel(application) {

    private val importUseCase = TimetableImportUseCase(application)

    private val _activeGroupId = MutableStateFlow(repository.getActiveGroupId(application))
    val activeGroupId: StateFlow<String> = _activeGroupId

    val timetableGroups: StateFlow<List<TimetableGroup>> = repository.getGroupsStream(application)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf(TimetableGroup.default()),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<TimetableEntry>> = _activeGroupId
        .flatMapLatest { groupId -> repository.getEntriesStream(application, groupId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    private val reminderSyncLock = Any()
    @Volatile private var reminderSyncGeneration = 0L
    private var reminderSyncJob: Job? = null
    private val reminderSyncMutex = Mutex()
    private var lastReminderSyncToken: String? = null
    private val widgetRefreshLock = Any()
    @Volatile private var widgetRefreshGeneration = 0L
    private var widgetRefreshJob: Job? = null
    private val widgetRefreshMutex = Mutex()
    private var lastWidgetRefreshToken: String? = null

    init {
        ReminderFallbackWorker.ensureScheduled(application)
        viewModelScope.launch {
            repository.ensureMigrated(getApplication())
            _activeGroupId.value = repository.resolveActiveGroupId(getApplication())
            entries.debouncedEntrySideEffects().collect { currentEntries ->
                syncReminders(currentEntries)
                refreshWidgets(currentEntries)
            }
        }
    }

    fun selectTimetableGroup(groupId: String) {
        val normalizedGroupId = groupId.ifBlank { TimetableGroup.DEFAULT_ID }
        viewModelScope.launch {
            repository.setActiveGroupId(getApplication(), normalizedGroupId)
            _activeGroupId.value = normalizedGroupId
            val group = timetableGroups.value.firstOrNull { it.id == normalizedGroupId }
            postMessage(
                getApplication<Application>().getString(
                    R.string.vm_timetable_group_selected,
                    group?.name ?: TimetableGroup.DEFAULT_NAME,
                ),
            )
        }
    }

    fun createTimetableGroup(name: String) {
        viewModelScope.launch {
            val group = repository.createGroup(getApplication(), name)
            repository.setActiveGroupId(getApplication(), group.id)
            _activeGroupId.value = group.id
            postMessage(getApplication<Application>().getString(R.string.vm_timetable_group_created, group.name))
        }
    }

    suspend fun previewConflict(entry: TimetableEntry): TimetableEntry? {
        val normalized = normalizeEntry(entry)
        if (validateEntry(normalized) != null) return null
        val entriesSnapshot = entries.value
        return runConflictCalculation {
            findConflictForEntry(normalized, entriesSnapshot)
        }
    }

    suspend fun suggestResolvedEntry(entry: TimetableEntry): TimetableEntry? {
        val normalized = normalizeEntry(entry)
        if (validateEntry(normalized) != null) return null
        val entriesSnapshot = entries.value
        return runConflictCalculation {
            suggestAdjustedEntryAfterConflicts(normalized, entriesSnapshot)
        }
    }

    fun upsertEntry(entry: TimetableEntry, allowConflict: Boolean = false) {
        val normalized = normalizeEntry(entry)
        validateEntry(normalized)?.let {
            postMessage(getApplication<Application>().getString(R.string.vm_save_failed, it))
            return
        }
        val entriesSnapshot = entries.value

        viewModelScope.launch {
            val conflict = runConflictCalculation {
                findConflictForEntry(normalized, entriesSnapshot)
            }
            if (conflict != null && !allowConflict) {
                postMessage(
                    getApplication<Application>().getString(
                        R.string.vm_conflict_detected,
                        conflict.title,
                        formatMinutes(conflict.startMinutes),
                        formatMinutes(conflict.endMinutes),
                    ),
                )
                return@launch
            }

            repository.upsertEntry(getApplication(), normalized)

            if (conflict == null) {
                postMessage(getApplication<Application>().getString(R.string.vm_entry_saved))
            } else {
                postMessage(
                    getApplication<Application>().getString(
                        R.string.vm_entry_saved_with_conflict,
                        conflict.title,
                        formatMinutes(conflict.startMinutes),
                        formatMinutes(conflict.endMinutes),
                    ),
                )
            }
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            repository.deleteEntry(getApplication(), entryId)
            postMessage(getApplication<Application>().getString(R.string.vm_entry_deleted))
        }
    }

    suspend fun exportIcs(): String = withContext(Dispatchers.Default) {
        IcsCalendar.write(entries.value, getApplication<Application>().getString(R.string.default_calendar_name))
    }

    private val _importPreview = MutableSharedFlow<ImportPreview>(extraBufferCapacity = 1)
    val importPreview = _importPreview.asSharedFlow()

    fun importFromIcs(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val text = readText(contentResolver, uri) ?: return@launch
            if (text.isBlank()) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_empty))
                return@launch
            }

            val imported = try {
                withContext(Dispatchers.Default) {
                    IcsCalendar.parse(text)
                }
            } catch (error: Exception) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_parse_failed, error.message ?: getApplication<Application>().getString(R.string.vm_calendar_format_error)))
                emptyList()
            }
            if (imported.isEmpty()) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_no_valid))
                return@launch
            }

            val preview = importUseCase.buildImportPreview(
                imported = imported,
                existingEntries = entries.value,
                sourceName = getApplication<Application>().getString(R.string.default_calendar_name),
                activeGroupId = _activeGroupId.value,
            )
            if (preview.validEntries.isEmpty()) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_no_effective))
                return@launch
            }

            if (preview.truncated) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_truncated, preview.totalParsed))
            }

            _importPreview.tryEmit(preview)
        }
    }

    fun importAcademicEntries(sourceName: String, entries: List<TimetableEntry>) {
        viewModelScope.launch {
            if (entries.isEmpty()) {
                postMessage(getApplication<Application>().getString(R.string.vm_academic_import_empty, sourceName))
                return@launch
            }

            val preview = importUseCase.buildImportPreview(
                imported = entries,
                existingEntries = this@ScheduleViewModel.entries.value,
                sourceName = sourceName,
                activeGroupId = _activeGroupId.value,
            )
            if (preview.validEntries.isEmpty()) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_no_effective))
                return@launch
            }

            if (preview.truncated) {
                postMessage(getApplication<Application>().getString(R.string.vm_import_truncated, preview.totalParsed))
            }

            _importPreview.tryEmit(preview)
        }
    }

    fun confirmImport(preview: ImportPreview, target: ImportTarget) {
        viewModelScope.launch {
            commitImport(preview, target)
        }
    }

    fun cancelImport() {
        // no-op
    }

    private suspend fun commitImport(preview: ImportPreview, target: ImportTarget) {
        val app = getApplication<Application>()
        when (target) {
            ImportTarget.OverwriteCurrent -> {
                val groupId = _activeGroupId.value
                repository.replaceEntriesInGroup(app, groupId, preview.validEntries)
                val groupName = timetableGroups.value.firstOrNull { it.id == groupId }?.name ?: TimetableGroup.DEFAULT_NAME
                postImportMessage(preview, app.getString(R.string.vm_import_target_overwrite, groupName))
            }
            is ImportTarget.CreateGroup -> {
                val group = repository.createGroupWithEntries(app, target.name, preview.validEntries)
                _activeGroupId.value = group.id
                postImportMessage(preview, app.getString(R.string.vm_import_target_new_group, group.name))
            }
        }
    }

    private fun postImportMessage(preview: ImportPreview, targetLabel: String) {
        val app = getApplication<Application>()
        if (preview.invalidCount == 0 && preview.conflictCount == 0) {
            postMessage(app.getString(R.string.vm_import_success_to_target, preview.validEntries.size, targetLabel))
        } else {
            postMessage(
                app.getString(
                    R.string.vm_import_success_partial_to_target,
                    preview.validEntries.size,
                    preview.invalidCount,
                    preview.conflictCount,
                    targetLabel,
                ),
            )
        }
    }

    fun updateReminderMinutes(minutes: Iterable<Int>) {
        val normalizedMinutes = CourseReminderScheduler.normalizeReminderMinutes(minutes)
        if (normalizedMinutes.isEmpty()) return
        val currentMinutes = CourseReminderScheduler.getReminderMinutesSet(getApplication())
        if (normalizedMinutes == currentMinutes) return
        CourseReminderScheduler.setReminderMinutes(getApplication(), normalizedMinutes)
        syncReminders(entries.value)
        postMessage(getApplication<Application>().getString(R.string.vm_reminder_updated, CourseReminderScheduler.formatReminderSelection(normalizedMinutes)))
    }

    fun resyncReminderSchedule() {
        syncReminders(entries.value, force = true)
    }

    private suspend fun readText(contentResolver: ContentResolver, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val declaredSize = queryImportSize(contentResolver, uri)
                if (declaredSize != null && declaredSize > MAX_ICS_IMPORT_BYTES) {
                    throw IOException(importSizeLimitMessage())
                }
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw FileNotFoundException(uri.toString())
                inputStream.use { stream ->
                    readLimitedUtf8Text(stream, MAX_ICS_IMPORT_BYTES)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                postImportReadFailure(error)
                null
            }
        }
    }

    private fun postImportReadFailure(error: Throwable) {
        val app = getApplication<Application>()
        val message = when (val messageResId = importReadFailureMessageResId(error)) {
            R.string.vm_read_file_too_large -> app.getString(messageResId, formatImportSize(MAX_ICS_IMPORT_BYTES))
            R.string.vm_read_file_failed -> app.getString(
                messageResId,
                error.message ?: app.getString(R.string.msg_unknown_error),
            )
            else -> app.getString(messageResId)
        }
        postMessage(message)
    }

    private fun queryImportSize(contentResolver: ContentResolver, uri: Uri): Long? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                    cursor.getLong(sizeIndex)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun syncReminders(entriesList: List<TimetableEntry>, force: Boolean = false) {
        val generation: Long
        val previousJob: Job?
        synchronized(reminderSyncLock) {
            generation = ++reminderSyncGeneration
            previousJob = reminderSyncJob
            reminderSyncJob = null
        }
        previousJob?.cancel()
        reminderSyncJob = viewModelScope.launch(Dispatchers.IO) {
            reminderSyncMutex.withLock {
                if (generation != reminderSyncGeneration) return@launch
                lastReminderSyncToken = runReminderSyncIfNeeded(
                    entries = entriesList,
                    reminderMinutes = CourseReminderScheduler.getReminderMinutesSet(getApplication()),
                    lastSyncToken = lastReminderSyncToken,
                    force = force,
                ) { entries, forceReschedule ->
                    CourseReminderScheduler.sync(
                        context = getApplication(),
                        entries = entries,
                        forceReschedule = forceReschedule,
                    )
                }
            }
        }
    }

    private fun refreshWidgets(entriesList: List<TimetableEntry>) {
        val generation: Long
        val previousJob: Job?
        synchronized(widgetRefreshLock) {
            generation = ++widgetRefreshGeneration
            previousJob = widgetRefreshJob
            widgetRefreshJob = null
        }
        previousJob?.cancel()
        widgetRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            widgetRefreshMutex.withLock {
                if (generation != widgetRefreshGeneration) return@launch
                val refreshToken = widgetRefreshToken(entriesList)
                if (refreshToken == lastWidgetRefreshToken) return@launch
                TimetableWidgetUpdater.refreshAll(getApplication(), entriesList)
                lastWidgetRefreshToken = refreshToken
            }
        }
    }

    private fun postMessage(message: String) {
        viewModelScope.launch {
            _messages.emit(message)
        }
    }
}

internal fun countImportConflicts(
    validEntries: List<TimetableEntry>,
    existingEntries: List<TimetableEntry>,
): Int {
    if (validEntries.isEmpty()) return 0
    val internalConflicts = countConflictPairs(validEntries)
    val existingConflicts = countConflictPairsBetween(validEntries, existingEntries)
    return internalConflicts + existingConflicts
}

internal suspend fun <T> runConflictCalculation(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    block: () -> T,
): T {
    return withContext(dispatcher) {
        block()
    }
}

@OptIn(FlowPreview::class)
internal fun Flow<List<TimetableEntry>>.debouncedEntrySideEffects(
    debounceMillis: Long = 300L,
): Flow<List<TimetableEntry>> {
    return debounce(debounceMillis)
}

internal fun readLimitedUtf8Text(inputStream: InputStream, maxBytes: Int): String {
    require(maxBytes > 0)
    val buffer = ByteArray(8192)
    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    var totalRead = 0
    while (true) {
        val read = inputStream.read(buffer)
        if (read < 0) break
        if (read == 0) continue
        totalRead += read
        if (totalRead > maxBytes) {
            throw IOException("ICS file exceeds the import limit of  bytes.")
        }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
}

internal fun importSizeLimitMessage(maxBytes: Int = 1024 * 1024): String {
    return "ICS file exceeds the import limit of 1 MB."
}

internal fun formatImportSize(maxBytes: Int): String {
    return "1 MB"
}

@StringRes
internal fun importReadFailureMessageResId(error: Throwable): Int {
    return when (error) {
        is FileNotFoundException,
        is SecurityException -> R.string.vm_read_file_access_denied
        is IOException -> {
            if (error.message?.contains("limit") == true) R.string.vm_read_file_too_large else R.string.vm_read_file_failed
        }
        else -> R.string.vm_read_file_failed
    }
}

private fun isImportSizeLimitError(error: IOException): Boolean {
    return error.message?.contains("limit") == true
}

internal fun reminderSyncToken(
    entries: List<TimetableEntry>,
    reminderMinutes: List<Int>,
): String {
    val reminderHash = CourseReminderScheduler.normalizeReminderMinutes(reminderMinutes).hashCode()
    var entriesHash = 0L
    for (entry in entries) {
        entriesHash = 31 * entriesHash + entry.syncHashCode()
    }
    return "r${reminderHash}e$entriesHash"
}

internal suspend fun runReminderSyncIfNeeded(
    entries: List<TimetableEntry>,
    reminderMinutes: List<Int>,
    lastSyncToken: String?,
    force: Boolean,
    syncAction: suspend (List<TimetableEntry>, Boolean) -> Unit,
): String? {
    val syncToken = reminderSyncToken(entries, reminderMinutes)
    if (!force && syncToken == lastSyncToken) return lastSyncToken
    syncAction(entries, force)
    return syncToken
}

internal fun widgetRefreshToken(entries: List<TimetableEntry>): String {
    var entriesHash = 0L
    for (entry in entries) {
        entriesHash = 31 * entriesHash + entry.syncHashCode()
    }
    return entriesHash.toString()
}

private fun TimetableEntry.syncHashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + groupId.hashCode()
    result = 31 * result + title.hashCode()
    result = 31 * result + location.hashCode()
    result = 31 * result + date.hashCode()
    result = 31 * result + dayOfWeek
    result = 31 * result + startMinutes
    result = 31 * result + endMinutes
    result = 31 * result + recurrenceType.hashCode()
    result = 31 * result + semesterStartDate.hashCode()
    result = 31 * result + weekRule.hashCode()
    result = 31 * result + customWeekList.hashCode()
    result = 31 * result + skipWeekList.hashCode()
    return result
}

data class ImportPreview(
    val validEntries: List<TimetableEntry>,
    val invalidCount: Int,
    val conflictCount: Int,
    val totalParsed: Int,
    val truncated: Boolean = false,
    val sourceName: String = "",
    val suggestedGroupName: String = "",
    val internalConflictCount: Int = conflictCount,
    val existingConflictCount: Int = 0,
)

sealed interface ImportTarget {
    data object OverwriteCurrent : ImportTarget
    data class CreateGroup(val name: String) : ImportTarget
}

internal fun suggestedImportGroupName(sourceName: String, defaultName: String = "导入课表"): String {
    val base = sourceName.trim().ifBlank { defaultName }
    val today = java.time.LocalDate.now().toString()
    return "$base $today".take(40)
}
