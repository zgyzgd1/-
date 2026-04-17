package com.example.timetable.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.data.IcsCalendar
import com.example.timetable.data.TimetableEntry
import com.example.timetable.data.formatMinutes
import com.example.timetable.notify.CourseReminderScheduler
import com.example.timetable.data.TimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 课程表视图模型
 * 管理课程数据的状态和业务逻辑，包括增删改查、导入导出等功能
 * 使用 Kotlin Flow + Room 数据库驱动数据流
 */
class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    // 真正的实时课程流直接暴露自 Repository (Room Database)
    val entries: StateFlow<List<TimetableEntry>> = TimetableRepository.getEntriesStream(application)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            // 平滑升级与兜底方案，完成迁移。
            TimetableRepository.ensureMigrated(getApplication())
            
            // 实时订阅 DB 更新，如果有任何增删操作影响了 Flow 数据，
            // 就会自动触发提醒同步模块
            entries.collect { currentEntries ->
                if (currentEntries.isNotEmpty()) {
                    syncReminders(currentEntries)
                }
            }
        }
    }

    /**
     * 添加或更新课程条目
     */
    fun upsertEntry(entry: TimetableEntry) {
        val normalized = normalizeEntry(entry)
        validateEntry(normalized)?.let {
            postMessage("保存失败：$it")
            return
        }
        val conflict = findConflict(normalized, entries.value)

        viewModelScope.launch {
            TimetableRepository.upsertEntry(getApplication(), normalized)
            
            if (conflict == null) {
                postMessage("已保存课程")
            } else {
                postMessage(
                    "已保存课程（与 ${conflict.title} ${formatMinutes(conflict.startMinutes)}-${formatMinutes(conflict.endMinutes)} 时间重叠）"
                )
            }
        }
    }

    /**
     * 删除指定 ID 的课程条目
     */
    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            TimetableRepository.deleteEntry(getApplication(), entryId)
            postMessage("已删除课程")
        }
    }

    /**
     * 导出课程表为 ICS 格式
     */
    fun exportIcs(): String = IcsCalendar.write(entries.value)

    /**
     * 从 ICS 文件导入课程数据
     */
    fun importFromIcs(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            val text = readText(contentResolver, uri)
            if (text.isBlank()) {
                postMessage("导入失败：文件内容为空")
                return@launch
            }

            val imported = runCatching { IcsCalendar.parse(text) }
                .onFailure { postMessage("导入失败：${it.message ?: "日历格式异常"}") }
                .getOrDefault(emptyList())
            if (imported.isEmpty()) {
                postMessage("未识别到可导入的课程")
                return@launch
            }

            applyImportedEntries(imported)
        }
    }

    fun updateReminderMinutes(minutes: Int) {
        CourseReminderScheduler.setReminderMinutes(getApplication(), minutes)
        syncReminders(entries.value)
        postMessage("已设置为提前 $minutes 分钟提醒")
    }

    private suspend fun readText(contentResolver: ContentResolver, uri: Uri): String {
        return withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.readText()
                }.orEmpty()
            }.onFailure {
                postMessage("读取文件失败：${it.message ?: "未知错误"}")
            }.getOrDefault("")
        }
    }

    private suspend fun applyImportedEntries(imported: List<TimetableEntry>) {
        val validEntries = mutableListOf<TimetableEntry>()
        var invalidCount = 0

        imported.map(::normalizeEntry)
            .forEach { entry ->
                if (validateEntry(entry) != null) {
                    invalidCount++
                    return@forEach
                }
                validEntries += entry
            }

        if (validEntries.isEmpty()) {
            postMessage("导入失败：未发现有效课程")
            return
        }

        val conflictCount = countConflictPairs(validEntries)
        
        TimetableRepository.replaceAllEntries(getApplication(), validEntries)
        
        if (invalidCount == 0 && conflictCount == 0) {
            postMessage("已导入 ${validEntries.size} 条课程，并保存为当前课程表")
        } else {
            postMessage("已导入 ${validEntries.size} 条课程，跳过无效 ${invalidCount} 条，检测到冲突 ${conflictCount} 组")
        }
    }

    private fun normalizeEntry(entry: TimetableEntry): TimetableEntry {
        return entry.copy(
            title = entry.title.trim(),
            location = entry.location.trim(),
            note = entry.note.trim(),
        )
    }

    private fun validateEntry(entry: TimetableEntry): String? {
        return when {
            entry.title.isBlank() -> "课程名称不能为空"
            entry.title.length > 64 -> "课程名称不能超过 64 字"
            entry.location.length > 64 -> "地点不能超过 64 字"
            entry.note.length > 256 -> "备注不能超过 256 字"
            entry.startMinutes !in 0 until 24 * 60 -> "开始时间不合法"
            entry.endMinutes !in 1..24 * 60 -> "结束时间不合法"
            entry.startMinutes >= entry.endMinutes -> "结束时间需要晚于开始时间"
            else -> null
        }
    }

    private fun findConflict(target: TimetableEntry, entriesList: List<TimetableEntry>): TimetableEntry? {
        return entriesList.firstOrNull { existing ->
            existing.id != target.id &&
                existing.date == target.date &&
                target.startMinutes < existing.endMinutes &&
                existing.startMinutes < target.endMinutes
        }
    }

    private fun countConflictPairs(entriesList: List<TimetableEntry>): Int {
        var pairs = 0
        entriesList.groupBy { it.date }.values.forEach { sameDay ->
            val sorted = sameDay.sortedBy { it.startMinutes }
            for (index in sorted.indices) {
                val current = sorted[index]
                for (nextIndex in index + 1 until sorted.size) {
                    val next = sorted[nextIndex]
                    if (next.startMinutes >= current.endMinutes) break
                    if (current.startMinutes < next.endMinutes && next.startMinutes < current.endMinutes) {
                        pairs++
                    }
                }
            }
        }
        return pairs
    }

    private fun syncReminders(entriesList: List<TimetableEntry>) {
        viewModelScope.launch(Dispatchers.Default) {
            CourseReminderScheduler.sync(getApplication(), entriesList)
        }
    }

    private fun postMessage(message: String) {
        viewModelScope.launch {
            _messages.emit(message)
        }
    }
}
