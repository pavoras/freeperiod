package org.freeperiod.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.freeperiod.app.data.db.*
import org.freeperiod.engine.*

internal fun CustomCategoryEntity.domain() = CustomCategory(id, name, iconKey, sortOrder, archived, singleChoice, counted, itemSet)
internal fun CustomCategory.entity() = CustomCategoryEntity(id, name, iconKey, sortOrder, archived, singleChoice, counted, itemSet)
internal fun UiOverrideEntity.domain() = UiOverride(key, hidden, sortOrder, label, iconKey)
internal fun UiOverride.entity() = UiOverrideEntity(key, hidden, sortOrder, label, iconKey)
internal fun SituationEntity?.domain(): Situation = if (this == null) Situation() else Situation(
    LifePhase.valueOf(phase), Method.valueOf(method), pillPackStartEpochDay?.let {
        PillSchedule(LocalDate.ofEpochDay(it), requireNotNull(pillActiveDays), requireNotNull(pillBreakDays))
    }, fertileWindowEnabled, painDiary,
)
internal fun Situation.entity() = SituationEntity(0, phase.name, method.name,
    pill?.packStart?.toEpochDay(), pill?.activeDays, pill?.breakDays, fertileWindowEnabled, painDiary)

internal fun Reminder.entity(lastDeliveredDate: Long? = null): ReminderEntity {
    val base = ReminderEntity(id = id, kind = kind.name, title = title, recurrenceKind = "DAILY",
        time = time.toString(), enabled = enabled, daysBefore = daysBefore, lastDeliveredDate = lastDeliveredDate)
    return when (val rule = recurrence) {
        Recurrence.Daily -> base
        is Recurrence.EveryNDays -> base.copy(recurrenceKind = "EVERY_N_DAYS", recurrenceN = rule.n, anchorEpochDay = rule.anchor.toEpochDay())
        is Recurrence.Weekly -> base.copy(recurrenceKind = "WEEKLY", weekday = rule.day.name)
        is Recurrence.MonthlyOnDay -> base.copy(recurrenceKind = "MONTHLY_ON_DAY", monthDay = rule.day)
        is Recurrence.EveryNMonths -> base.copy(recurrenceKind = "EVERY_N_MONTHS", recurrenceN = rule.n, anchorEpochDay = rule.anchor.toEpochDay())
        is Recurrence.Once -> base.copy(recurrenceKind = "ONCE", onceEpochDay = rule.date.toEpochDay())
    }
}

internal fun ReminderEntity.domain(): Reminder {
    val recurrence = when (recurrenceKind) {
        "DAILY" -> Recurrence.Daily
        "EVERY_N_DAYS" -> Recurrence.EveryNDays(requireNotNull(recurrenceN), LocalDate.ofEpochDay(requireNotNull(anchorEpochDay)))
        "WEEKLY" -> Recurrence.Weekly(DayOfWeek.valueOf(requireNotNull(weekday)))
        "MONTHLY_ON_DAY" -> Recurrence.MonthlyOnDay(requireNotNull(monthDay))
        "EVERY_N_MONTHS" -> Recurrence.EveryNMonths(requireNotNull(recurrenceN), LocalDate.ofEpochDay(requireNotNull(anchorEpochDay)))
        "ONCE" -> Recurrence.Once(LocalDate.ofEpochDay(requireNotNull(onceEpochDay)))
        else -> error("Unknown recurrence kind")
    }
    return Reminder(id, ReminderKind.valueOf(kind), title, recurrence, LocalTime.parse(time), enabled, daysBefore)
}
