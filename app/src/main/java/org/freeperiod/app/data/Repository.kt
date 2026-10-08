package org.freeperiod.app.data

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.freeperiod.app.data.db.*
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*

class PeriodValidationException(val error: PeriodError) : Exception(error.name)

class Repository(
    private val db: FreePeriodDatabase,
    private val afterRestore: suspend (BackupData) -> Unit = {},
    private val clock: () -> LocalDate = { LocalDate.now() },
) {
    constructor(db: FreePeriodDatabase, clock: () -> LocalDate) : this(db, afterRestore = {}, clock = clock)

    private val writes = Mutex()
    private val periodsDao = db.periodDao()
    private val logsDao = db.dayLogDao()
    private val tagsDao = db.tagDao()
    private val settingsDao = db.domainSettingsDao()
    private val categoriesDao = db.customCategoryDao()
    private val overridesDao = db.uiOverrideDao()
    private val situationDao = db.situationDao()
    private val remindersDao = db.reminderDao()
    private val dismissalsDao = db.hintDismissalDao()

    val periods: Flow<List<Period>> = periodsDao.observeAll().map { rows -> rows.map { it.domain() } }
    val dayLogs: Flow<Map<LocalDate, DayLog>> = logsDao.observeWithTags().map { rows ->
        rows.map { it.domain() }.associateBy { it.date }
    }
    val tags: Flow<List<Tag>> = tagsDao.observeAll().map { rows -> rows.map { it.domain() } }
    val domainSettings: Flow<BackupSettings> = settingsDao.observe().map { it.domain() }
    val situation: Flow<Situation> = situationDao.observe().map { it.domain() }
    val customCategories: Flow<List<CustomCategory>> = categoriesDao.observeAll().map { rows -> rows.map { it.domain() } }
    val overrides: Flow<List<UiOverride>> = overridesDao.observeAll().map { rows -> rows.map { it.domain() } }
    val reminders: Flow<List<Reminder>> = remindersDao.observeAll().map { rows -> rows.map { it.domain() } }
    val hintDismissals: Flow<Set<Long>> = dismissalsDao.observeAll().map { it.toSet() }

    /** Inserts a batch atomically, returning overlapping blocks in input order. Other errors roll back. */
    suspend fun addPeriods(periods: List<Period>): List<Period> = write {
        val accepted = periodsDao.getAll().map { it.domain() }.toMutableList()
        val skipped = mutableListOf<Period>()
        val today = clock()
        for (period in periods) {
            val candidate = period.copy(id = 0)
            when (val error = PeriodRules.validate(accepted, candidate, today)) {
                PeriodError.OVERLAP -> skipped += period
                null -> accepted += candidate.copy(id = periodsDao.insert(candidate.entity()))
                else -> throw PeriodValidationException(error)
            }
        }
        skipped
    }

    suspend fun addPeriod(start: LocalDate, end: LocalDate?): Result<Period> = periodResult {
        write {
            val candidate = Period(0, start, end)
            validatePeriod(candidate)
            candidate.copy(id = periodsDao.insert(candidate.entity()))
        }
    }

    suspend fun endPeriod(id: Long, end: LocalDate): Result<Period> = periodResult {
        write { updateValidated(requireNotNull(periodsDao.get(id)).domain().copy(end = end)) }
    }

    suspend fun updatePeriod(period: Period): Result<Period> = periodResult {
        write {
            requireNotNull(periodsDao.get(period.id))
            updateValidated(period)
        }
    }

    suspend fun deletePeriod(id: Long) { write { periodsDao.delete(id) } }

    suspend fun saveDayLog(log: DayLog) { write { saveLog(log) } }

    suspend fun addTag(name: String, categoryId: Long? = null, iconKey: String = "tag"): Tag = write {
        val tag = Tag(0, name.trim(), categoryId = categoryId, iconKey = iconKey)
        validateTag(tag)
        tag.copy(id = tagsDao.insert(tag.entity()))
    }

    suspend fun updateTag(tag: Tag) { write {
        require(tagsDao.getAll().any { it.id == tag.id })
        val normalized = tag.copy(name = tag.name.trim())
        validateTag(normalized)
        tagsDao.update(normalized.entity())
    } }

    suspend fun renameTag(id: Long, name: String) {
        write {
            val tag = requireNotNull(tagsDao.getAll().find { it.id == id }).domain().copy(name = name.trim())
            validateTag(tag)
            tagsDao.update(tag.entity())
        }
    }

    suspend fun archiveTag(id: Long) { write { tagsDao.archive(id) } }

    suspend fun addCustomCategory(name: String, iconKey: String = "tag", sortOrder: Int = 0): CustomCategory = write {
        val category = CustomCategory(0, name.trim(), iconKey, sortOrder, false)
        validateCategory(category)
        category.copy(id = categoriesDao.insert(category.entity()))
    }

    suspend fun updateCustomCategory(category: CustomCategory) { write {
        require(categoriesDao.getAll().any { it.id == category.id })
        val normalized = category.copy(name = category.name.trim())
        validateCategory(normalized)
        categoriesDao.update(normalized.entity())
    } }

    suspend fun setUiOverride(override: UiOverride) { write {
        val data = snapshot()
        require(validBackup(data.copy(overrides = data.overrides.filter { it.key != override.key } + override), clock()))
        overridesDao.upsert(override.entity())
    } }
    suspend fun deleteUiOverride(key: String) { write { overridesDao.delete(key) } }
    suspend fun updateSituation(situation: Situation) { write { situationDao.upsert(situation.entity()) } }

    suspend fun saveReminder(reminder: Reminder): Reminder = write {
        require(reminder.id >= 0)
        require(if (reminder.kind == ReminderKind.PERIOD_DUE) reminder.daysBefore?.let { it >= 0 } == true else reminder.daysBefore == null)
        if (reminder.id == 0L) reminder.copy(id = remindersDao.insert(reminder.entity())) else {
            val existing = requireNotNull(remindersDao.get(reminder.id))
            remindersDao.update(reminder.entity(existing.lastDeliveredDate))
            reminder
        }
    }
    suspend fun deleteReminder(id: Long) { write { remindersDao.delete(id) } }
    suspend fun markReminderDelivered(id: Long, date: LocalDate) { write { remindersDao.markDelivered(id, date.toEpochDay()) } }
    suspend fun lastDeliveredDate(id: Long): LocalDate? = remindersDao.get(id)?.lastDeliveredDate?.let(LocalDate::ofEpochDay)
    suspend fun dismissLongCycleHint(startPeriodId: Long) { write { dismissalsDao.insert(HintDismissalEntity(startPeriodId)) } }

    /** A retry after a crash between Room commit and the DataStore flag keeps existing rows. */
    suspend fun ensureLegacyReminders(settings: AppSettings) { write {
        val existing = remindersDao.getAll().map { it.domain() }
        if (existing.none { it.kind == ReminderKind.PERIOD_DUE }) remindersDao.insert(
            Reminder(0, ReminderKind.PERIOD_DUE, null, Recurrence.Daily, LocalTime.of(20, 0),
                settings.periodReminder, settings.periodReminderDaysBefore).entity())
        if (existing.none { it.kind == ReminderKind.DAILY_LOG }) remindersDao.insert(
            Reminder(0, ReminderKind.DAILY_LOG, null, Recurrence.Daily, settings.dailyReminderTime, settings.dailyReminder).entity())
    } }

    /** Transitional old controls only update a preset when its corresponding preference changes. */
    suspend fun updateLegacyReminders(before: AppSettings, after: AppSettings) { write {
        val rows = remindersDao.getAll()
        if (before.periodReminder != after.periodReminder || before.periodReminderDaysBefore != after.periodReminderDaysBefore) {
            require(after.periodReminderDaysBefore >= 0)
            val existing = rows.firstOrNull { it.kind == ReminderKind.PERIOD_DUE.name }
            val reminder = existing?.domain()?.copy(enabled = after.periodReminder, daysBefore = after.periodReminderDaysBefore)
                ?: Reminder(0, ReminderKind.PERIOD_DUE, null, Recurrence.Daily, LocalTime.of(20, 0), after.periodReminder, after.periodReminderDaysBefore)
            if (existing == null) remindersDao.insert(reminder.entity()) else remindersDao.update(reminder.entity(existing.lastDeliveredDate))
        }
        if (before.dailyReminder != after.dailyReminder || before.dailyReminderTime != after.dailyReminderTime) {
            val existing = rows.firstOrNull { it.kind == ReminderKind.DAILY_LOG.name }
            val reminder = existing?.domain()?.copy(time = after.dailyReminderTime, enabled = after.dailyReminder)
                ?: Reminder(0, ReminderKind.DAILY_LOG, null, Recurrence.Daily, after.dailyReminderTime, after.dailyReminder)
            if (existing == null) remindersDao.insert(reminder.entity()) else remindersDao.update(reminder.entity(existing.lastDeliveredDate))
        }
    } }

    suspend fun updateDomainSettings(settings: BackupSettings) {
        write {
            validateSettings(settings)
            settingsDao.upsert(settings.entity())
        }
    }

    suspend fun snapshot(): BackupData = db.withTransaction {
        BackupData(periods = periodsDao.getAll().map { it.domain() },
            dayLogs = logsDao.getWithTags().map { it.domain() },
            tags = tagsDao.getAll().map { it.domain() }, settings = settingsDao.get().domain(),
            situation = situationDao.get().domain(), customCategories = categoriesDao.getAll().map { it.domain() },
            overrides = overridesDao.getAll().map { it.domain() }, reminders = remindersDao.getAll().map { it.domain() },
            hintDismissals = dismissalsDao.getAll().toSet())
    }

    suspend fun replaceAll(data: BackupData) {
        write {
            validateReplacement(data)
            dismissalsDao.deleteAll()
            overridesDao.deleteAll()
            remindersDao.deleteAll()
            logsDao.deleteAll()
            tagsDao.deleteAll()
            categoriesDao.deleteAll()
            periodsDao.deleteAll()
            data.periods.forEach { periodsDao.insert(it.entity()) }
            data.customCategories.forEach { categoriesDao.insert(it.entity()) }
            data.tags.forEach { tagsDao.insert(it.entity()) }
            data.dayLogs.forEach { saveLog(it) }
            settingsDao.upsert(data.settings.entity())
            situationDao.upsert(data.situation.entity())
            data.overrides.forEach { overridesDao.upsert(it.entity()) }
            data.reminders.forEach { remindersDao.insert(it.entity()) }
            data.hintDismissals.forEach { dismissalsDao.insert(HintDismissalEntity(it)) }
        }
        afterRestore(snapshot())
    }

    private suspend fun validatePeriod(period: Period) {
        PeriodRules.validate(periodsDao.getAll().map { it.domain() }, period, clock())
            ?.let { throw PeriodValidationException(it) }
    }

    private suspend fun updateValidated(period: Period): Period {
        validatePeriod(period)
        periodsDao.update(period.entity())
        return period
    }

    private suspend fun saveLog(log: DayLog) {
        val date = log.date.toEpochDay()
        if (log.isEmpty()) {
            logsDao.delete(date)
        } else {
            logsDao.upsert(log.entity())
            logsDao.deleteLinks(date)
            logsDao.insertLinks(log.tagIds.map { DayTagEntity(date, it) })
        }
    }

    private fun validateSettings(settings: BackupSettings) {
        require(settings.typicalCycleLength == null || settings.typicalCycleLength in 15..90)
    }

    private fun validateReplacement(data: BackupData) {
        require(validBackup(data, clock())) { "Invalid replacement data" }
    }

    private suspend fun validateTag(tag: Tag) {
        require(tag.name.isNotBlank() && tag.iconKey.isNotBlank())
        require(tag.categoryId == null || categoriesDao.getAll().any { it.id == tag.categoryId })
        require(tagsDao.getAll().none { it.id != tag.id && it.categoryId == tag.categoryId &&
            it.name.lowercase(Locale.ROOT) == tag.name.lowercase(Locale.ROOT) })
    }

    private suspend fun validateCategory(category: CustomCategory) {
        require(category.name.isNotBlank() && category.iconKey.isNotBlank())
        require(categoriesDao.getAll().none { it.id != category.id && it.name.lowercase(Locale.ROOT) == category.name.lowercase(Locale.ROOT) })
    }

    private suspend fun <T> write(block: suspend () -> T): T = writes.withLock { db.withTransaction { block() } }

    private suspend fun <T> periodResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }
}
