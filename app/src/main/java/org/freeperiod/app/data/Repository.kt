package org.freeperiod.app.data

import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.freeperiod.app.data.db.*
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*

class PeriodValidationException(val error: PeriodError) : Exception(error.name)

class Repository(private val db: FreePeriodDatabase, private val clock: () -> LocalDate = { LocalDate.now() }) {
    private val writes = Mutex()
    private val periodsDao = db.periodDao()
    private val logsDao = db.dayLogDao()
    private val tagsDao = db.tagDao()
    private val settingsDao = db.domainSettingsDao()

    val periods: Flow<List<Period>> = periodsDao.observeAll().map { rows -> rows.map { it.domain() } }
    val dayLogs: Flow<Map<LocalDate, DayLog>> = logsDao.observeWithTags().map { rows ->
        rows.map { it.domain() }.associateBy { it.date }
    }
    val tags: Flow<List<Tag>> = tagsDao.observeAll().map { rows -> rows.map { it.domain() } }
    val domainSettings: Flow<BackupSettings> = settingsDao.observe().map { it.domain() }

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

    suspend fun addTag(name: String): Tag = write {
        require(name.isNotBlank())
        val tag = Tag(0, name.trim())
        tag.copy(id = tagsDao.insert(tag.entity()))
    }

    suspend fun renameTag(id: Long, name: String) {
        write {
            require(name.isNotBlank())
            tagsDao.rename(id, name.trim())
        }
    }

    suspend fun archiveTag(id: Long) { write { tagsDao.archive(id) } }

    suspend fun updateDomainSettings(settings: BackupSettings) {
        write {
            validateSettings(settings)
            settingsDao.upsert(settings.entity())
        }
    }

    suspend fun snapshot(): BackupData = db.withTransaction {
        BackupData(periods = periodsDao.getAll().map { it.domain() },
            dayLogs = logsDao.getWithTags().map { it.domain() },
            tags = tagsDao.getAll().map { it.domain() }, settings = settingsDao.get().domain())
    }

    suspend fun replaceAll(data: BackupData) {
        write {
            validateReplacement(data)
            logsDao.deleteAll()
            tagsDao.deleteAll()
            periodsDao.deleteAll()
            data.periods.forEach { periodsDao.insert(it.entity()) }
            data.tags.forEach { tagsDao.insert(it.entity()) }
            data.dayLogs.forEach { saveLog(it) }
            settingsDao.upsert(data.settings.entity())
        }
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
        require(data.schemaVersion == 1)
        require(data.periods.all { it.id > 0 } && data.periods.map { it.id }.distinct().size == data.periods.size)
        require(data.tags.all { it.id > 0 && it.name.isNotBlank() } && data.tags.map { it.id }.distinct().size == data.tags.size)
        require(data.dayLogs.map { it.date }.distinct().size == data.dayLogs.size)
        val tagIds = data.tags.map { it.id }.toSet()
        require(data.dayLogs.all { tagIds.containsAll(it.tagIds) })
        validateSettings(data.settings)
        val today = clock()
        data.periods.forEach { period ->
            PeriodRules.validate(data.periods, period, today)?.let { throw PeriodValidationException(it) }
        }
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
