package com.example.timetable.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/**
 * ICS 日历文件处理工具
 * 支持将课程表导出为标准的 iCalendar (.ics) 格式，以及从 .ics 文件导入课程数据
 * ICS 格式遵循 RFC 5545 标准，可被主流日历应用识别
 */
object IcsCalendar {
    // 日期时间格式化器，格式：yyyyMMdd'T'HHmmss
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val utcFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    // 用于分割行的正则表达式，支持不同平台的换行符
    private val lineSplit = Regex("\\r?\\n")
    private val multiValueKeys = setOf("EXDATE")
    private val systemZone: ZoneId
        get() = ZoneId.systemDefault()
    private const val MAX_FOLDED_LINE_OCTETS = 75
    private const val TIMETABLE_ENTRY_ID_KEY = "X-TIMETABLE-ENTRY-ID"
    private const val TIMETABLE_RECURRENCE_KEY = "X-TIMETABLE-RECURRENCE"
    private const val TIMETABLE_SEMESTER_START_KEY = "X-TIMETABLE-SEMESTER-START"
    private const val TIMETABLE_WEEK_RULE_KEY = "X-TIMETABLE-WEEK-RULE"
    private const val TIMETABLE_CUSTOM_WEEKS_KEY = "X-TIMETABLE-CUSTOM-WEEKS"
    private const val TIMETABLE_SKIP_WEEKS_KEY = "X-TIMETABLE-SKIP-WEEKS"

    private data class ExportedEvent(
        val uid: String,
        val entry: TimetableEntry,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val rrule: String? = null,
        val exDates: List<LocalDateTime> = emptyList(),
    )

    private data class ParsedOccurrence(
        val title: String,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val location: String,
        val note: String,
    )

    private data class TimetableMetadata(
        val entryId: String,
        val recurrenceType: RecurrenceType,
        val semesterStartDate: String = "",
        val weekRule: WeekRule = WeekRule.ALL,
        val customWeekList: String = "",
        val skipWeekList: String = "",
    )

    /**
     * 将课程列表写入 ICS 格式的字符串
     *
     * @param entries 课程条目列表
     * @param calendarName 日历名称，默认为"课程表助手"
     * @return ICS 格式的文本内容
     */
    fun write(entries: List<TimetableEntry>, calendarName: String = "课程表助手"): String {
        val builder = StringBuilder()

        // 写入 ICS 文件头部信息
        appendIcsLine(builder, "BEGIN:VCALENDAR")
        appendIcsLine(builder, "VERSION:2.0")
        appendIcsLine(builder, "PRODID:-//TimetableMinimal//CN")
        appendIcsLine(builder, "CALSCALE:GREGORIAN")
        appendIcsLine(builder, "X-WR-TIMEZONE:${systemZone.id}")
        appendIcsLine(builder, "X-WR-CALNAME:${escapeText(calendarName)}")
        val dtStamp = utcFormatter.format(OffsetDateTime.now(ZoneOffset.UTC))

        // 按星期和时间排序后遍历所有课程
        entries
            .sortedWith(compareBy<TimetableEntry> { it.date }.thenBy { it.startMinutes })
            .flatMap(::buildEventsForExport)
            .sortedWith(compareBy<ExportedEvent> { it.start }.thenBy { it.entry.title })
            .forEach { event ->
                // 写入事件详情
                appendIcsLine(builder, "BEGIN:VEVENT")
                appendIcsLine(builder, "UID:${event.uid}@timetable")
                appendIcsLine(builder, "DTSTAMP:$dtStamp")
                appendIcsLine(builder, "SUMMARY:${escapeText(event.entry.title)}")
                if (event.entry.location.isNotBlank()) {
                    appendIcsLine(builder, "LOCATION:${escapeText(event.entry.location)}")
                }
                if (event.entry.note.isNotBlank()) {
                    appendIcsLine(builder, "DESCRIPTION:${escapeText(event.entry.note)}")
                }
                appendIcsLine(builder, "DTSTART;TZID=${systemZone.id}:${formatter.format(event.start)}")
                appendIcsLine(builder, "DTEND;TZID=${systemZone.id}:${formatter.format(event.end)}")
                appendTimetableMetadata(builder, event.entry)
                event.rrule?.let { appendIcsLine(builder, "RRULE:$it") }
                if (event.exDates.isNotEmpty()) {
                    appendIcsLine(
                        builder,
                        "EXDATE;TZID=${systemZone.id}:${event.exDates.joinToString(",") { formatter.format(it) }}",
                    )
                }
                appendIcsLine(builder, "END:VEVENT")
            }

        // 写入 ICS 文件尾部
        appendIcsLine(builder, "END:VCALENDAR")
        return builder.toString()
    }

    private fun buildEventsForExport(entry: TimetableEntry): List<ExportedEvent> {
        val recurrence = resolveRecurrenceType(entry.recurrenceType) ?: RecurrenceType.NONE
        if (recurrence != RecurrenceType.WEEKLY) {
            return buildSingleEventForExport(entry)?.let(::listOf).orEmpty()
        }

        return when (resolveWeekRule(entry.weekRule) ?: WeekRule.ALL) {
            WeekRule.CUSTOM -> buildCustomWeeklyEventsForExport(entry)
            WeekRule.ALL -> buildRepeatingWeeklyEventForExport(entry, WeekRule.ALL)?.let(::listOf).orEmpty()
            WeekRule.ODD -> buildRepeatingWeeklyEventForExport(entry, WeekRule.ODD)?.let(::listOf).orEmpty()
            WeekRule.EVEN -> buildRepeatingWeeklyEventForExport(entry, WeekRule.EVEN)?.let(::listOf).orEmpty()
        }
    }

    private fun buildSingleEventForExport(
        entry: TimetableEntry,
        uid: String = entry.id,
        occurrenceDate: LocalDate? = null,
    ): ExportedEvent? {
        val resolvedDate = occurrenceDate ?: parseEntryDate(entry.date) ?: return null
        val start = occurrenceStartForExport(resolvedDate, entry.startMinutes) ?: return null
        val end = occurrenceEndForExport(resolvedDate, entry.endMinutes) ?: return null
        return ExportedEvent(
            uid = uid,
            entry = entry,
            start = start,
            end = end,
        )
    }

    private fun buildRepeatingWeeklyEventForExport(
        entry: TimetableEntry,
        weekRule: WeekRule,
    ): ExportedEvent? {
        val byDay = dayOfWeekToken(entry.dayOfWeek) ?: return buildSingleEventForExport(entry)
        val baseEvent = buildSingleEventForExport(entry) ?: return null
        val rule = buildString {
            append("FREQ=WEEKLY;")
            if (weekRule != WeekRule.ALL) {
                append("INTERVAL=2;")
            }
            append("BYDAY=$byDay")
        }
        return baseEvent.copy(
            rrule = rule,
            exDates = skippedOccurrenceDateTimesForExport(entry, weekRule),
        )
    }

    private fun buildCustomWeeklyEventsForExport(entry: TimetableEntry): List<ExportedEvent> {
        return customOccurrenceDatesForExport(entry)
            .mapNotNull { occurrenceDate ->
                buildSingleEventForExport(
                    entry = entry,
                    uid = "${entry.id}#${occurrenceDate}",
                    occurrenceDate = occurrenceDate,
                )
            }
    }

    private fun customOccurrenceDatesForExport(entry: TimetableEntry): List<LocalDate> {
        val firstDate = parseEntryDate(entry.date) ?: return emptyList()
        val semesterStartDate = parseEntryDate(entry.semesterStartDate).takeIf { entry.semesterStartDate.isNotBlank() }
            ?: firstDate
        val semesterWeekStart = semesterStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val customWeeks = parseWeekList(entry.customWeekList).orEmpty()
        val skippedWeeks = parseWeekList(entry.skipWeekList).orEmpty()
        val dayOffset = (entry.dayOfWeek - 1).coerceIn(0, 6)

        return customWeeks
            .asSequence()
            .filter { it !in skippedWeeks }
            .sorted()
            .map { weekNumber ->
                semesterWeekStart
                    .plusWeeks((weekNumber - 1).toLong())
                    .plusDays(dayOffset.toLong())
            }
            .filter { !it.isBefore(firstDate) }
            .toList()
    }

    private fun skippedOccurrenceDateTimesForExport(
        entry: TimetableEntry,
        weekRule: WeekRule,
    ): List<LocalDateTime> {
        val firstDate = parseEntryDate(entry.date) ?: return emptyList()
        val semesterStartDate = parseEntryDate(entry.semesterStartDate).takeIf { entry.semesterStartDate.isNotBlank() }
            ?: firstDate
        val semesterWeekStart = semesterStartDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val skippedWeeks = parseWeekList(entry.skipWeekList).orEmpty()
        val startTime = LocalTime.of(entry.startMinutes / 60, entry.startMinutes % 60)
        val dayOffset = (entry.dayOfWeek - 1).coerceIn(0, 6)

        return skippedWeeks
            .asSequence()
            .sorted()
            .filter { weekNumber -> weekMatchesRuleForExport(weekRule, weekNumber) }
            .map { weekNumber ->
                semesterWeekStart
                    .plusWeeks((weekNumber - 1).toLong())
                    .plusDays(dayOffset.toLong())
            }
            .filter { !it.isBefore(firstDate) }
            .map { occurrenceDate -> LocalDateTime.of(occurrenceDate, startTime) }
            .toList()
    }

    private fun weekMatchesRuleForExport(weekRule: WeekRule, weekNumber: Int): Boolean {
        return when (weekRule) {
            WeekRule.ALL -> true
            WeekRule.ODD -> weekNumber % 2 == 1
            WeekRule.EVEN -> weekNumber % 2 == 0
            WeekRule.CUSTOM -> false
        }
    }

    private fun dayOfWeekToken(dayOfWeek: Int): String? {
        return when (dayOfWeek) {
            1 -> "MO"
            2 -> "TU"
            3 -> "WE"
            4 -> "TH"
            5 -> "FR"
            6 -> "SA"
            7 -> "SU"
            else -> null
        }
    }

    private fun occurrenceStartForExport(date: LocalDate, startMinutes: Int): LocalDateTime? {
        return runCatching {
            date.atTime(startMinutes / 60, startMinutes % 60)
        }.getOrNull()
    }

    private fun occurrenceEndForExport(date: LocalDate, endMinutes: Int): LocalDateTime? {
        return runCatching {
            if (endMinutes == 24 * 60) {
                date.plusDays(1).atStartOfDay()
            } else {
                date.atTime(endMinutes / 60, endMinutes % 60)
            }
        }.getOrNull()
    }

    /**
     * 解析 ICS 格式的文本内容为课程列表
     *
     * @param content ICS 格式的文本内容
     * @return 解析后的课程条目列表
     */
    fun parse(content: String): List<TimetableEntry> {
        val eventFields = mutableListOf<Map<String, String>>()
        val lines = unfoldLines(content)  // 处理续行
        var insideEvent = false
        var current = LinkedHashMap<String, String>()

        // 逐行解析 ICS 内容
        for (line in lines) {
            when {
                // 遇到事件开始标记
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    insideEvent = true
                    current = LinkedHashMap()
                }
                // 遇到事件结束标记
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    if (insideEvent) {
                        eventFields += current.toMap()
                    }
                    insideEvent = false
                }
                // 在事件内部，解析键值对
                insideEvent -> {
                    val separatorIndex = line.indexOf(':')
                    if (separatorIndex <= 0) {
                        continue
                    }
                    val rawKey = line.substring(0, separatorIndex)
                    val rawValue = line.substring(separatorIndex + 1)
                    // 提取属性名（去除参数部分）并转为大写
                    val key = rawKey.substringBefore(';').uppercase()
                    val value = unescapeText(rawValue)

                    val tzid = rawKey
                        .split(';')
                        .drop(1)
                        .firstNotNullOfOrNull { token ->
                            val name = token.substringBefore('=').uppercase()
                            if (name == "TZID") token.substringAfter('=', "").ifBlank { null } else null
                        }

                    current[key] = if (key in multiValueKeys && current[key] != null) {
                        "${current[key]},$value"
                    } else {
                        value
                    }

                    if (tzid != null) {
                        val timezoneKey = "${key}_TZID"
                        current[timezoneKey] = if (key in multiValueKeys && current[timezoneKey] != null) {
                            "${current[timezoneKey]},$tzid"
                        } else {
                            tzid
                        }
                    }
                }
            }
        }

        return buildEntriesFromEventFields(eventFields)
    }

    /**
     * 解析单个 VEVENT 事件字段为课程条目
     *
     * @param fields 事件的所有字段映射
     * @return 解析成功的课程条目，失败返回 null
     */
    private fun parseEventEntries(fields: Map<String, String>): List<TimetableEntry> {
        val occurrence = parseOccurrence(fields) ?: return emptyList()
        val uidBase = fields["UID"]?.trim().orEmpty().ifBlank { UUID.randomUUID().toString() }
        val exDates = parseExDates(fields, fields["DTSTART_TZID"])

        val rrule = parseRRule(fields["RRULE"].orEmpty(), fields["DTSTART_TZID"])
        if (rrule == null) {
            if (normalizeMinute(occurrence.start) in exDates) return emptyList()
            return buildEntry(
                id = uidBase,
                title = occurrence.title,
                start = occurrence.start,
                end = occurrence.end,
                location = occurrence.location,
                note = occurrence.note,
            )?.let(::listOf).orEmpty()
        }

        val interval = rrule.interval.coerceAtLeast(1)
        val result = mutableListOf<TimetableEntry>()
        val durationMinutes = java.time.Duration.between(occurrence.start, occurrence.end).toMinutes().coerceAtLeast(1)

        if (rrule.freq == "WEEKLY" && rrule.byDays.isNotEmpty()) {
            var weekAnchor = occurrence.start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            var emitted = 0
            var reachedUntil = false

            while (
                emitted < MAX_EXPANDED_OCCURRENCES &&
                    (rrule.count == null || emitted < rrule.count)
            ) {
                for (day in rrule.byDays) {
                    if (rrule.count != null && emitted >= rrule.count) break

                    val occurrenceDate = weekAnchor.plusDays((day.value - 1).toLong())
                    val recurringStart = LocalDateTime.of(occurrenceDate, occurrence.start.toLocalTime())
                    if (recurringStart.isBefore(occurrence.start)) continue
                    if (rrule.until != null && recurringStart.isAfter(rrule.until)) {
                        reachedUntil = true
                        break
                    }
                    if (normalizeMinute(recurringStart) in exDates) continue

                    val occurrenceEnd = recurringStart.plusMinutes(durationMinutes)
                    buildEntry(
                        id = "$uidBase#$emitted",
                        title = occurrence.title,
                        start = recurringStart,
                        end = occurrenceEnd,
                        location = occurrence.location,
                        note = occurrence.note,
                    )?.let {
                        result += it
                        emitted++
                    }
                }

                if (reachedUntil) break
                weekAnchor = weekAnchor.plusWeeks(interval.toLong())
                val nextWeekFirstStart = LocalDateTime.of(weekAnchor, occurrence.start.toLocalTime())
                if (rrule.until != null && nextWeekFirstStart.isAfter(rrule.until)) break
            }

            return result
        }

        var occurrenceStart = occurrence.start
        var occurrenceEnd = occurrence.end
        var occurrenceIndex = 0

        // 用次数上限保护异常 RRULE，避免死循环
        while (occurrenceIndex < MAX_EXPANDED_OCCURRENCES) {
            if (rrule.count != null && occurrenceIndex >= rrule.count) break
            if (rrule.until != null && occurrenceStart.isAfter(rrule.until)) break

            if (normalizeMinute(occurrenceStart) !in exDates) {
                buildEntry(
                    id = "$uidBase#$occurrenceIndex",
                    title = occurrence.title,
                    start = occurrenceStart,
                    end = occurrenceEnd,
                    location = occurrence.location,
                    note = occurrence.note,
                )?.let(result::add)
            }

            val nextStart = incrementByFreq(occurrenceStart, rrule.freq, interval) ?: break
            val nextEnd = incrementByFreq(occurrenceEnd, rrule.freq, interval) ?: break
            occurrenceStart = nextStart
            occurrenceEnd = nextEnd
            occurrenceIndex++
        }

        return result
    }

    private fun buildEntry(
        id: String,
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
        location: String,
        note: String,
    ): TimetableEntry? {
        val dateText = start.toLocalDate().toString()
        if (parseEntryDate(dateText) == null) return null

        val startMinutes = start.hour * 60 + start.minute
        val endMinutes = if (end.toLocalDate().isAfter(start.toLocalDate())) {
            24 * 60
        } else {
            end.hour * 60 + end.minute
        }
        if (endMinutes <= startMinutes) return null

        return runCatching {
            TimetableEntry(
                id = id,
                title = title,
                date = dateText,
                dayOfWeek = start.dayOfWeek.value,
                startMinutes = startMinutes,
                endMinutes = endMinutes,
                location = location,
                note = note,
            )
        }.getOrNull()
    }

    private fun incrementByFreq(value: LocalDateTime, freq: String, interval: Int): LocalDateTime? {
        return when (freq) {
            "DAILY" -> value.plusDays(interval.toLong())
            "WEEKLY" -> value.plusWeeks(interval.toLong())
            "MONTHLY" -> value.plusMonths(interval.toLong())
            "YEARLY" -> value.plusYears(interval.toLong())
            else -> null
        }
    }

    private fun parseRRule(rule: String, defaultTzid: String? = null): RecurrenceRule? {
        if (rule.isBlank()) return null

        val parts = rule.split(';')
            .mapNotNull { token ->
                val index = token.indexOf('=')
                if (index <= 0) return@mapNotNull null
                token.substring(0, index).uppercase() to token.substring(index + 1)
            }
            .toMap()

        val freq = parts["FREQ"]?.uppercase() ?: return null
        val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val until = parts["UNTIL"]?.let { parseDateTime(it, defaultTzid) }
        val count = parts["COUNT"]?.toIntOrNull()?.coerceAtLeast(1)
        val byDays = parts["BYDAY"]
            ?.split(',')
            ?.mapNotNull(::parseByDay)
            ?.distinct()
            ?.sortedBy { it.value }
            .orEmpty()
        return RecurrenceRule(freq = freq, interval = interval, until = until, count = count, byDays = byDays)
    }

    private fun parseByDay(token: String): DayOfWeek? {
        return when (token.trim().uppercase()) {
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private fun parseExDates(fields: Map<String, String>, defaultTzid: String?): Set<LocalDateTime> {
        val raw = fields["EXDATE"].orEmpty()
        if (raw.isBlank()) return emptySet()

        val exdateTzid = fields["EXDATE_TZID"]
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
            ?.ifBlank { null }
            ?: defaultTzid

        return raw.split(',')
            .mapNotNull { token -> parseDateTime(token.trim(), exdateTzid) }
            .map(::normalizeMinute)
            .toSet()
    }

    private fun normalizeMinute(value: LocalDateTime): LocalDateTime {
        return LocalDateTime.of(value.toLocalDate(), LocalTime.of(value.hour, value.minute))
    }

    private data class RecurrenceRule(
        val freq: String,
        val interval: Int,
        val until: LocalDateTime?,
        val count: Int?,
        val byDays: List<DayOfWeek>,
    )

    private fun appendTimetableMetadata(builder: StringBuilder, entry: TimetableEntry) {
        appendIcsLine(builder, "$TIMETABLE_ENTRY_ID_KEY:${escapeText(entry.id)}")
        appendIcsLine(builder, "$TIMETABLE_RECURRENCE_KEY:${entry.recurrenceType}")
        val recurrence = resolveRecurrenceType(entry.recurrenceType) ?: RecurrenceType.NONE
        if (recurrence != RecurrenceType.WEEKLY) return

        appendIcsLine(builder, "$TIMETABLE_SEMESTER_START_KEY:${escapeText(entry.semesterStartDate)}")
        appendIcsLine(builder, "$TIMETABLE_WEEK_RULE_KEY:${entry.weekRule}")
        appendIcsLine(builder, "$TIMETABLE_CUSTOM_WEEKS_KEY:${escapeText(entry.customWeekList)}")
        appendIcsLine(builder, "$TIMETABLE_SKIP_WEEKS_KEY:${escapeText(entry.skipWeekList)}")
    }

    private fun appendIcsLine(builder: StringBuilder, line: String) {
        if (line.isEmpty()) {
            builder.append("\r\n")
            return
        }

        var currentOctets = 0
        line.forEach { char ->
            val charOctets = char.toString().toByteArray(Charsets.UTF_8).size
            if (currentOctets + charOctets > MAX_FOLDED_LINE_OCTETS) {
                builder.append("\r\n ")
                currentOctets = 1
            }
            builder.append(char)
            currentOctets += charOctets
        }
        builder.append("\r\n")
    }

    private fun buildEntriesFromEventFields(eventFields: List<Map<String, String>>): List<TimetableEntry> {
        if (eventFields.isEmpty()) return emptyList()

        val entries = mutableListOf<TimetableEntry>()
        val handled = BooleanArray(eventFields.size)
        val indexedGroups = eventFields.withIndex()
            .mapNotNull { indexed ->
                indexed.value[TIMETABLE_ENTRY_ID_KEY]
                    ?.trim()
                    ?.ifBlank { null }
                    ?.let { it to indexed }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })

        indexedGroups.values.forEach { group ->
            val parsed = parseMetadataEntryGroup(group.map { it.value }) ?: return@forEach
            entries += parsed
            group.forEach { handled[it.index] = true }
        }

        eventFields.forEachIndexed { index, fields ->
            if (!handled[index]) {
                entries += parseEventEntries(fields)
            }
        }

        return entries.distinctBy { it.id }
    }

    private fun parseMetadataEntryGroup(fieldGroup: List<Map<String, String>>): TimetableEntry? {
        val metadata = parseTimetableMetadata(fieldGroup.firstOrNull() ?: return null) ?: return null
        if (fieldGroup.size > 1 && metadata.recurrenceType != RecurrenceType.WEEKLY) return null

        val parsedOccurrences = fieldGroup
            .mapNotNull(::parseOccurrence)
            .sortedBy { it.start }
        if (parsedOccurrences.size != fieldGroup.size || parsedOccurrences.isEmpty()) return null

        val firstOccurrence = parsedOccurrences.first()
        val sameIdentity = parsedOccurrences.all { occurrence ->
            occurrence.title == firstOccurrence.title &&
                occurrence.location == firstOccurrence.location &&
                occurrence.note == firstOccurrence.note &&
                occurrence.start.toLocalTime() == firstOccurrence.start.toLocalTime() &&
                occurrence.end.toLocalTime() == firstOccurrence.end.toLocalTime()
        }
        if (!sameIdentity) return null

        return buildEntryFromMetadata(
            metadata = metadata,
            occurrence = firstOccurrence,
        )
    }

    private fun parseTimetableMetadata(fields: Map<String, String>): TimetableMetadata? {
        val entryId = fields[TIMETABLE_ENTRY_ID_KEY]?.trim().orEmpty().ifBlank { return null }
        val recurrenceType = resolveRecurrenceType(fields[TIMETABLE_RECURRENCE_KEY].orEmpty()) ?: return null
        if (recurrenceType != RecurrenceType.WEEKLY) {
            return TimetableMetadata(
                entryId = entryId,
                recurrenceType = recurrenceType,
            )
        }

        val semesterStartDate = fields[TIMETABLE_SEMESTER_START_KEY]?.trim().orEmpty()
        val weekRule = resolveWeekRule(fields[TIMETABLE_WEEK_RULE_KEY].orEmpty()) ?: return null
        val customWeekList = fields[TIMETABLE_CUSTOM_WEEKS_KEY].orEmpty()
        val skipWeekList = fields[TIMETABLE_SKIP_WEEKS_KEY].orEmpty()
        if (semesterStartDate.isBlank() || parseEntryDate(semesterStartDate) == null) return null
        if (parseWeekList(customWeekList) == null || parseWeekList(skipWeekList) == null) return null

        return TimetableMetadata(
            entryId = entryId,
            recurrenceType = recurrenceType,
            semesterStartDate = semesterStartDate,
            weekRule = weekRule,
            customWeekList = customWeekList,
            skipWeekList = skipWeekList,
        )
    }

    private fun buildEntryFromMetadata(
        metadata: TimetableMetadata,
        occurrence: ParsedOccurrence,
    ): TimetableEntry? {
        return runCatching {
            TimetableEntry(
                id = metadata.entryId,
                title = occurrence.title,
                date = occurrence.start.toLocalDate().toString(),
                dayOfWeek = occurrence.start.dayOfWeek.value,
                startMinutes = occurrence.start.hour * 60 + occurrence.start.minute,
                endMinutes = if (occurrence.end.toLocalDate().isAfter(occurrence.start.toLocalDate())) {
                    24 * 60
                } else {
                    occurrence.end.hour * 60 + occurrence.end.minute
                },
                location = occurrence.location,
                note = occurrence.note,
                recurrenceType = metadata.recurrenceType.name,
                semesterStartDate = metadata.semesterStartDate,
                weekRule = metadata.weekRule.name,
                customWeekList = metadata.customWeekList,
                skipWeekList = metadata.skipWeekList,
            )
        }.getOrNull()
    }

    private fun parseOccurrence(fields: Map<String, String>): ParsedOccurrence? {
        val title = fields["SUMMARY"]?.trim().orEmpty()
        val startText = fields["DTSTART"] ?: return null
        val endText = fields["DTEND"]
        if (title.isBlank()) return null

        val startTzid = fields["DTSTART_TZID"]
        val endTzid = fields["DTEND_TZID"] ?: startTzid
        val start = parseDateTime(startText, startTzid) ?: return null
        val end = parseDateTime(endText ?: "", endTzid) ?: start.plusHours(1)
        if (!end.isAfter(start)) return null

        return ParsedOccurrence(
            title = title,
            start = start,
            end = end,
            location = fields["LOCATION"].orEmpty(),
            note = fields["DESCRIPTION"].orEmpty(),
        )
    }

    /**
     * 处理 ICS 文件的续行（unfold lines）
     * ICS 规范中，长行可以用空格或制表符开头的续行来表示
     *
     * @param content 原始 ICS 文本内容
     * @return 处理后的行列表
     */
    private fun unfoldLines(content: String): List<String> {
        val result = mutableListOf<String>()
        content.split(lineSplit).forEach { rawLine ->
            when {
                // 如果是续行（以空格或制表符开头），合并到上一行
                rawLine.startsWith(' ') || rawLine.startsWith('\t') -> {
                    if (result.isNotEmpty()) {
                        result[result.lastIndex] = result.last() + rawLine.trimStart()
                    }
                }
                // 否则作为新行添加
                else -> result += rawLine.trimEnd()
            }
        }
        return result
    }

    /**
     * 解析日期时间字符串
     * 支持多种格式：纯日期（8位）、完整日期时间（15位）、ISO 格式
     *
     * @param value 日期时间字符串
     * @return 解析后的 LocalDateTime，失败返回 null
     */
    private fun parseDateTime(value: String, tzid: String? = null): LocalDateTime? {
        if (value.isBlank()) return null
        val raw = value.trim()
        val cleaned = raw.removeSuffix("Z")  // 移除 UTC 标识
        val sourceZone = resolveZone(tzid) ?: systemZone

        // 处理 RFC5545 UTC 时间（例如 20260413T080000Z）
        if (raw.endsWith("Z") && cleaned.length == 15) {
            return runCatching {
                OffsetDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX"))
                    .atZoneSameInstant(systemZone)
                    .toLocalDateTime()
            }.getOrElse {
                runCatching {
                    LocalDateTime.parse(cleaned, formatter)
                        .atZone(sourceZone)
                        .withZoneSameInstant(systemZone)
                        .toLocalDateTime()
                }.getOrNull()
            }
        }

        // 处理带时区偏移时间（例如 20260413T080000+0800 / +08:00）
        if (Regex("\\d{8}T\\d{6}[+-]\\d{4}").matches(raw)) {
            return runCatching {
                OffsetDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssXX"))
                    .atZoneSameInstant(systemZone)
                    .toLocalDateTime()
            }.getOrNull()
        }
        if (Regex("\\d{8}T\\d{6}[+-]\\d{2}:\\d{2}").matches(raw)) {
            return runCatching {
                OffsetDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssXXX"))
                    .atZoneSameInstant(systemZone)
                    .toLocalDateTime()
            }.getOrNull()
        }

        return when (cleaned.length) {
            // 8位纯日期格式（如 20240101）
            8 -> LocalDate.parse(cleaned, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay()
            // 15位完整日期时间格式（如 20240101T080000）
            15 -> runCatching {
                LocalDateTime.parse(cleaned, formatter)
                    .atZone(sourceZone)
                    .withZoneSameInstant(systemZone)
                    .toLocalDateTime()
            }.getOrNull()
            // 其他格式尝试使用 ISO 格式解析
            else -> runCatching {
                OffsetDateTime.parse(cleaned, DateTimeFormatter.ISO_DATE_TIME)
                    .atZoneSameInstant(systemZone)
                    .toLocalDateTime()
            }.getOrElse {
                runCatching {
                    LocalDateTime.parse(cleaned, DateTimeFormatter.ISO_DATE_TIME)
                        .atZone(sourceZone)
                        .withZoneSameInstant(systemZone)
                        .toLocalDateTime()
                }.getOrNull()
            }
        }
    }

    private fun resolveZone(tzid: String?): ZoneId? {
        val value = tzid?.trim().orEmpty()
        if (value.isBlank()) return null
        return runCatching { ZoneId.of(value) }.getOrNull()
    }

    /**
     * 转义文本中的特殊字符，符合 ICS 规范
     *
     * @param value 原始文本
     * @return 转义后的文本
     */
    private fun escapeText(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
            .replace("\r", "")
    }

    /**
     * 反转义文本中的特殊字符
     *
     * @param value 转义后的文本
     * @return 原始文本
     */
    private fun unescapeText(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
    }

    private const val MAX_EXPANDED_OCCURRENCES = 512
}
