package org.freeperiod.app.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import org.freeperiod.engine.CycleUse
import org.freeperiod.engine.FlowLevel
import org.freeperiod.engine.Mood
import org.freeperiod.engine.Pain
import org.freeperiod.engine.Sex
import org.freeperiod.engine.Discharge
import org.freeperiod.engine.Symptom

class Converters {
    @TypeConverter fun dateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun epochDayToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
    @TypeConverter fun cycleUseToName(value: CycleUse): String = value.name
    @TypeConverter fun nameToCycleUse(value: String): CycleUse = CycleUse.valueOf(value)
    @TypeConverter fun flowToName(value: FlowLevel?): String? = value?.name
    @TypeConverter fun nameToFlow(value: String?): FlowLevel? = value?.let(FlowLevel::valueOf)
    @TypeConverter fun moodToName(value: Mood?): String? = value?.name
    @TypeConverter fun nameToMood(value: String?): Mood? = value?.let(Mood::valueOf)
    @TypeConverter fun painToName(value: Pain?): String? = value?.name
    @TypeConverter fun nameToPain(value: String?): Pain? = value?.let(Pain::valueOf)
    @TypeConverter fun sexToName(value: Sex?): String? = value?.name
    @TypeConverter fun nameToSex(value: String?): Sex? = value?.let(Sex::valueOf)
    @TypeConverter fun dischargeToName(value: Discharge?): String? = value?.name
    @TypeConverter fun nameToDischarge(value: String?): Discharge? = value?.let(Discharge::valueOf)

    @TypeConverter fun symptomsToNames(value: Set<Symptom>): String = value.map { it.name }.sorted().joinToString(";")
    @TypeConverter fun namesToSymptoms(value: String): Set<Symptom> = if (value.isEmpty()) emptySet()
        else value.split(';').map(Symptom::valueOf).toSet()
}
