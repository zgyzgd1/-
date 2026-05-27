package com.example.timetable.data

import androidx.annotation.StringRes
import com.example.timetable.R
import java.time.LocalDate

/**
 * 璇剧▼鏉＄洰楠岃瘉甯搁噺銆? */
object EntryConstants {
    const val MAX_TITLE_LENGTH = 64
    const val MAX_LOCATION_LENGTH = 64
    const val MAX_NOTE_LENGTH = 256
    const val SLOT_COUNT_MIN = 1
    const val SLOT_COUNT_MAX = 20
    const val MINUTES_PER_DAY = 24 * 60
}

/**
 * 璇剧▼鏉＄洰楠岃瘉閿欒绫诲瀷銆? *
 * 姣忎釜閿欒鍏宠仈涓€涓瓧绗︿覆璧勬簮 ID锛岀敤浜庢樉绀洪敊璇彁绀恒€? */
enum class EntryValidationError(@param:StringRes val messageResId: Int) {
    EmptyTitle(R.string.error_empty_course_name),
    TitleTooLong(R.string.error_title_too_long),
    InvalidDate(R.string.error_invalid_date),
    InvalidTime(R.string.error_invalid_time),
    InvalidTimeRange(R.string.error_invalid_time_range),
    LocationTooLong(R.string.error_location_too_long),
    NoteTooLong(R.string.error_note_too_long),
    InvalidSemesterDate(R.string.error_invalid_semester_date),
    InvalidCustomWeeks(R.string.error_invalid_custom_weeks),
    InvalidSkipWeeks(R.string.error_invalid_skip_weeks),
    EmptyCustomWeeks(R.string.error_empty_custom_weeks),
    InvalidRecurrence(R.string.val_invalid_recurrence),
    InvalidWeekRule(R.string.val_invalid_week_rule),
    InvalidStartTime(R.string.val_invalid_start),
    InvalidEndTime(R.string.val_invalid_end),
    EndBeforeStart(R.string.val_end_before_start),
    WeekMismatch(R.string.val_week_mismatch),
    NonWeeklyOddEven(R.string.val_non_weekly_odd_even),
    NonWeeklyCustom(R.string.val_non_weekly_custom),
    NonWeeklySkip(R.string.val_non_weekly_skip),
}

/**
 * 璇剧▼鏉＄洰楠岃瘉鍣ㄣ€? *
 * 缁熶竴绠＄悊璇剧▼鏉＄洰鐨勯獙璇侀€昏緫锛屼緵 UI 灞傦紙瀵硅瘽妗嗭級鍜?ViewModel 鍏辩敤锛? * 閬垮厤楠岃瘉瑙勫垯閲嶅瀹氫箟銆? */
object EntryValidator {
    /**
     * 楠岃瘉宸叉瀯閫犵殑璇剧▼鏉＄洰銆?     *
     * 閫傜敤浜?ViewModel 涓宸茶鑼冨寲鏉＄洰鐨勯獙璇併€?     *
     * @param entry 寰呴獙璇佺殑璇剧▼鏉＄洰
     * @return 楠岃瘉閿欒锛屾垨 null 琛ㄧず楠岃瘉閫氳繃
     */
    fun validate(entry: TimetableEntry): EntryValidationError? {
        val title = entry.title.trim()
        val location = entry.location.trim()
        val note = entry.note.trim()
        val recurrence = resolveRecurrenceType(entry.recurrenceType)
            ?: return EntryValidationError.InvalidRecurrence
        val weekRule = resolveWeekRule(entry.weekRule)
            ?: return EntryValidationError.InvalidWeekRule
        val customWeeks = parseWeekList(entry.customWeekList)
            ?: return EntryValidationError.InvalidCustomWeeks
        val skipWeeks = parseWeekList(entry.skipWeekList)
            ?: return EntryValidationError.InvalidSkipWeeks
        val entryDate = parseEntryDate(entry.date)
            ?: return EntryValidationError.InvalidDate
        val semesterStartDate = entry.semesterStartDate
            .takeIf { it.isNotBlank() }
            ?.let { parseEntryDate(it) }

        return when {
            title.isBlank() -> EntryValidationError.EmptyTitle
            title.length > EntryConstants.MAX_TITLE_LENGTH -> EntryValidationError.TitleTooLong
            location.length > EntryConstants.MAX_LOCATION_LENGTH -> EntryValidationError.LocationTooLong
            note.length > EntryConstants.MAX_NOTE_LENGTH -> EntryValidationError.NoteTooLong
            entry.startMinutes !in 0 until EntryConstants.MINUTES_PER_DAY -> EntryValidationError.InvalidStartTime
            entry.endMinutes !in 1..EntryConstants.MINUTES_PER_DAY -> EntryValidationError.InvalidEndTime
            entry.startMinutes >= entry.endMinutes -> EntryValidationError.EndBeforeStart
            recurrence == RecurrenceType.WEEKLY && semesterStartDate == null -> EntryValidationError.InvalidSemesterDate
            recurrence == RecurrenceType.WEEKLY && weekRule == WeekRule.CUSTOM && customWeeks.isEmpty() -> EntryValidationError.EmptyCustomWeeks
            entryDate.dayOfWeek.value != entry.dayOfWeek -> EntryValidationError.WeekMismatch
            !occursOnDate(entry, entryDate) -> EntryValidationError.WeekMismatch
            recurrence != RecurrenceType.WEEKLY && weekRule != WeekRule.ALL -> EntryValidationError.NonWeeklyOddEven
            recurrence != RecurrenceType.WEEKLY && customWeeks.isNotEmpty() -> EntryValidationError.NonWeeklyCustom
            recurrence != RecurrenceType.WEEKLY && skipWeeks.isNotEmpty() -> EntryValidationError.NonWeeklySkip
            else -> null
        }
    }

    /**
     * 楠岃瘉瀵硅瘽妗嗕腑鐨勫師濮嬭緭鍏ユ暟鎹€?     *
     * 閫傜敤浜?UI 灞傚湪鏋勯€?TimetableEntry 涔嬪墠鐨勯獙璇侊紝
     * 鍖呭惈瑙ｆ瀽澶辫触鐨勬鏌ャ€?     *
     * @param title 鏍囬
     * @param parsedDate 宸茶В鏋愮殑鏃ユ湡锛坣ull 琛ㄧず瑙ｆ瀽澶辫触锛?     * @param parsedStart 宸茶В鏋愮殑寮€濮嬫椂闂达紙null 琛ㄧず瑙ｆ瀽澶辫触锛?     * @param parsedEnd 宸茶В鏋愮殑缁撴潫鏃堕棿锛坣ull 琛ㄧず瑙ｆ瀽澶辫触锛?     * @param location 鍦扮偣
     * @param note 澶囨敞
     * @param recurrenceType 閲嶅绫诲瀷
     * @param parsedSemesterStart 宸茶В鏋愮殑瀛︽湡寮€濮嬫棩鏈燂紙null 琛ㄧず瑙ｆ瀽澶辫触鎴栭潪鍛ㄥ惊鐜級
     * @param customWeekList 鑷畾涔夊懆娆℃枃鏈?     * @param skipWeekList 璺宠繃鍛ㄦ鏂囨湰
     * @param weekRule 鍛ㄨ鍒?     * @return 楠岃瘉閿欒锛屾垨 null 琛ㄧず楠岃瘉閫氳繃
     */
    fun validateDraft(
        title: String,
        parsedDate: LocalDate?,
        parsedStart: Int?,
        parsedEnd: Int?,
        location: String,
        note: String,
        recurrenceType: RecurrenceType,
        parsedSemesterStart: LocalDate?,
        customWeekList: String,
        skipWeekList: String,
        weekRule: WeekRule,
    ): EntryValidationError? {
        val customWeeks = parseWeekList(customWeekList)
        val skipWeeks = parseWeekList(skipWeekList)

        return when {
            title.trim().isBlank() -> EntryValidationError.EmptyTitle
            title.trim().length > EntryConstants.MAX_TITLE_LENGTH -> EntryValidationError.TitleTooLong
            parsedDate == null -> EntryValidationError.InvalidDate
            parsedStart == null || parsedEnd == null -> EntryValidationError.InvalidTime
            parsedStart >= parsedEnd -> EntryValidationError.InvalidTimeRange
            location.trim().length > EntryConstants.MAX_LOCATION_LENGTH -> EntryValidationError.LocationTooLong
            note.trim().length > EntryConstants.MAX_NOTE_LENGTH -> EntryValidationError.NoteTooLong
            recurrenceType == RecurrenceType.WEEKLY && parsedSemesterStart == null -> EntryValidationError.InvalidSemesterDate
            recurrenceType == RecurrenceType.WEEKLY && customWeeks == null -> EntryValidationError.InvalidCustomWeeks
            recurrenceType == RecurrenceType.WEEKLY && skipWeeks == null -> EntryValidationError.InvalidSkipWeeks
            recurrenceType == RecurrenceType.WEEKLY && weekRule == WeekRule.CUSTOM && customWeeks.isNullOrEmpty() -> EntryValidationError.EmptyCustomWeeks
            else -> null
        }
    }
}

