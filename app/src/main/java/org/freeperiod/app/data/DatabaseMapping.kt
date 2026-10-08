package org.freeperiod.app.data

import java.time.LocalDate
import org.freeperiod.app.data.db.*
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupSettings

private val converters = Converters()

internal fun PeriodEntity.domain() = Period(id, LocalDate.ofEpochDay(startEpochDay),
    endEpochDay?.let(LocalDate::ofEpochDay), cycleUse)
internal fun Period.entity() = PeriodEntity(id, start.toEpochDay(), end?.toEpochDay(), cycleUse)
internal fun TagEntity.domain() = Tag(id, name, archived, categoryId, iconKey)
internal fun Tag.entity() = TagEntity(id, name, archived, categoryId, iconKey)
internal fun DayLog.entity() = DayLogEntity(date.toEpochDay(), flow, mood,
    converters.symptomsToNames(symptoms), pain, sex, discharge, note, ovulationTest)
internal fun DayLogWithTags.domain() = DayLog(LocalDate.ofEpochDay(log.epochDay), log.flow,
    log.mood, converters.namesToSymptoms(log.symptoms), log.pain, log.sex, log.discharge,
    log.note, links.map { it.tagId }.toSet(), log.ovulationTest)
internal fun DomainSettingsEntity?.domain() = BackupSettings(this?.typicalCycleLength,
    this?.predictionsPaused ?: false)
internal fun BackupSettings.entity() = DomainSettingsEntity(0, typicalCycleLength, predictionsPaused)
