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
    converters.symptomsToNames(symptoms), pain, Sex.entries.firstOrNull { it in sex }, Discharge.entries.firstOrNull { it in discharge },
    note, ovulationTest, appetite, libido, converters.sexValuesToNames(sex), converters.dischargeValuesToNames(discharge))
internal fun DayLogWithTags.domain() = DayLog(LocalDate.ofEpochDay(log.epochDay), log.flow,
    log.mood, converters.namesToSymptoms(log.symptoms), log.pain,
    converters.namesToSexValues(log.sexValues).ifEmpty { setOfNotNull(log.sex) },
    converters.namesToDischargeValues(log.dischargeValues).ifEmpty { setOfNotNull(log.discharge) },
    log.note, links.map { it.tagId }.toSet(), log.ovulationTest,
    links.filter { it.count > 1 }.associate { it.tagId to it.count }, log.appetite, log.libido)
internal fun DomainSettingsEntity?.domain() = BackupSettings(this?.typicalCycleLength,
    this?.predictionsPaused ?: false)
internal fun BackupSettings.entity() = DomainSettingsEntity(0, typicalCycleLength, predictionsPaused)
