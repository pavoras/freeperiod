package org.freeperiod.engine.backup

import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit.DAYS
import java.util.Collections
import java.util.Random
import org.freeperiod.engine.*
import org.junit.Assert.*
import org.junit.Test

/** Fictional German diary for manual restore, chart, PDF and diary-list testing. */
class SampleBackupTest {
    @Test fun writesCurrentSampleBackupAndRoundTrips() {
        val today = LocalDate.now()
        val data = sample(today)
        assertEquals("The payload must be repeatable for a given today", data, sample(today))
        assertSample(data, today)

        val password = "freeperiod".toCharArray()
        // Keep the codec's normal work factor and fresh cryptographic salt/nonce.
        val bytes = BackupCodec.encode(data, password)
        val file = Path.of("build", "sample", "freeperiod-sample.fpbackup")
        Files.createDirectories(file.parent)
        Files.write(file, bytes)
        assertEquals(DecodeResult.Ok(data), BackupCodec.decode(Files.readAllBytes(file), password) { today })
        assertTrue(Files.size(file) > 0)
        println("Sample backup: ${data.periods.size} periods, ${data.dayLogs.size} logged days, " +
            "${data.dayLogs.count { it.note != null }} notes, " +
            "${data.dayLogs.first().date} to ${data.dayLogs.last().date}")
    }

    private fun sample(today: LocalDate): BackupData {
        val random = Random(today.toEpochDay())
        val starts = mutableListOf(today.minusDays((8 + random.nextInt(13)).toLong()))
        while (starts.last() > today.minusMonths(14)) {
            // Exactly one longer interval, well inside the history.
            val length = if (starts.size == 7) 38 else 26 + random.nextInt(6)
            starts += starts.last().minusDays(length.toLong())
        }
        val periods = starts.reversed().mapIndexed { index, start ->
            Period(index + 1L, start, start.plusDays((3 + random.nextInt(3)).toLong()))
        }
        val longCycle = periods.zipWithNext().single { (a, b) -> DAYS.between(a.start, b.start) == 38L }.first
        val travelNoteDay = longCycle.start.plusDays(38)
        val dates = generateSequence(periods.first().start) { it.plusDays(1) }.takeWhile { it < today }.toList()
        // Keep every period day, yesterday and the travel note. Omit 18% overall, only two days in the last month.
        val candidates = dates.filter { date ->
            date != today.minusDays(1) && date != travelNoteDay && periods.none { date in it.start..it.end!! }
        }
        val recent = candidates.filter { it >= today.minusDays(30) }.toMutableList()
        val older = candidates.filter { it < today.minusDays(30) }.toMutableList()
        Collections.shuffle(recent, random)
        Collections.shuffle(older, random)
        val gaps = (recent.take(2) + older.take((dates.size * 0.18).toInt() - 2)).toSet()
        val logs = dates.filterNot { it in gaps }.map { date ->
            val index = periods.indexOfLast { it.start <= date }
            val period = periods[index]
            val nextStart = periods.getOrNull(index + 1)?.start ?: period.start.plusDays(29)
            dayLog(date, period, nextStart, longCycle.id, travelNoteDay, random)
        }

        // Repository.addBuiltInItem makes this marker, not a visible custom medication category.
        val categories = listOf(
            CustomCategory(1, "Medikamente", "builtin:medication", 0, false),
            CustomCategory(2, "Wo es weh tut", "person", 101, false, itemSet = "pain_diary:where"),
            CustomCategory(3, "Schmerzen bei", "pulse", 102, false, itemSet = "pain_diary:during"),
            CustomCategory(4, "Auswirkung auf den Alltag", "home", 103, false, itemSet = "pain_diary:daily"),
            CustomCategory(5, "Wasser", "glass", 104, false, counted = true),
            CustomCategory(6, "Schlaf", "sleep", 105, false, singleChoice = true),
        )
        // Pain diary names/icons match ItemSets.kt and values-de/strings.xml exactly.
        val tags = listOf(
            Tag(1, "Sport", iconKey = "sneaker"),
            Tag(2, "Stress", iconKey = "briefcase"),
            Tag(3, "Reise", iconKey = "plane"),
            Tag(4, "Alkohol", iconKey = "wine"),
            Tag(5, "Gut geschlafen", iconKey = "bed"),
            Tag(6, "Spaziergang", iconKey = "footprints"),
            Tag(7, "Ibuprofen", categoryId = 1, iconKey = "pill"),
            Tag(8, "Magnesium", categoryId = 1, iconKey = "pill"),
            Tag(9, "Unterbauch", categoryId = 2, iconKey = "cramps"),
            Tag(10, "Rücken", categoryId = 2, iconKey = "backache"),
            Tag(11, "Becken", categoryId = 2, iconKey = "circle"),
            Tag(12, "Beine", categoryId = 2, iconKey = "footprints"),
            Tag(13, "Kopf", categoryId = 2, iconKey = "headache"),
            Tag(14, "Brust", categoryId = 2, iconKey = "breast"),
            Tag(15, "Sex", categoryId = 3, iconKey = "sex"),
            Tag(16, "Stuhlgang", categoryId = 3, iconKey = "digestion"),
            Tag(17, "Wasserlassen", categoryId = 3, iconKey = "drop"),
            Tag(18, "Arbeit oder Schule verpasst", categoryId = 4, iconKey = "briefcase"),
            Tag(19, "Pläne abgesagt", categoryId = 4, iconKey = "people"),
            Tag(20, "Im Bett geblieben", categoryId = 4, iconKey = "bed"),
            Tag(21, "Gläser", categoryId = 5, iconKey = "glass"),
            // Pick-one chart levels follow item order, lowest first in the day entry.
            Tag(22, "schlecht", categoryId = 6, iconKey = "fatigue"),
            Tag(23, "okay", categoryId = 6, iconKey = "meh"),
            Tag(24, "gut", categoryId = 6, iconKey = "bed"),
            Tag(25, "sehr gut", categoryId = 6, iconKey = "battery_full"),
        )
        // TrackingSettingsViewModel writes an override for every row when positioning the enabled extras.
        val order = listOf("category:mood", "category:flow", "category:pain", "category:medication",
            "customCategory:2", "customCategory:3", "category:symptoms", "customCategory:4", "category:appetite",
            "category:sex", "category:libido", "category:discharge", "category:note", "category:tags",
            "category:ovulation_test", "customCategory:5", "customCategory:6")
        return BackupData(
            periods = periods, dayLogs = logs, tags = tags, settings = BackupSettings(29, false),
            situation = Situation(painDiary = true), customCategories = categories,
            overrides = order.mapIndexed { index, key ->
                UiOverride(key, hidden = key == "category:ovulation_test", sortOrder = index,
                    chart = if (key in setOf("customCategory:5", "customCategory:6")) true else null)
            },
            reminders = listOf(
                Reminder(1, ReminderKind.PERIOD_DUE, null, Recurrence.Daily, LocalTime.of(20, 0), true, 2),
                Reminder(2, ReminderKind.DAILY_LOG, null, Recurrence.Daily, LocalTime.of(21, 0), true),
            ),
        )
    }

    private fun dayLog(date: LocalDate, period: Period, nextStart: LocalDate, longCycleId: Long,
        travelNoteDay: LocalDate, random: Random): DayLog {
        fun chance(probability: Double) = random.nextDouble() < probability
        val cycleDay = DAYS.between(period.start, date).toInt()
        val periodLength = DAYS.between(period.start, period.end!!).toInt() + 1
        val bleeding = cycleDay < periodLength
        val beforePeriod = DAYS.between(date, nextStart) in 1..3
        val midCycle = cycleDay in (DAYS.between(period.start, nextStart).toInt() / 2 - 2)..
            (DAYS.between(period.start, nextStart).toInt() / 2 + 2)
        val travelling = period.id == longCycleId && cycleDay in 24..30
        val pain = when {
            cycleDay < 2 -> if (chance(0.30)) Pain.SEVERE else Pain.MODERATE
            bleeding && cycleDay == 2 -> Pain.MILD
            chance(0.025) -> Pain.MILD
            else -> Pain.NONE
        }
        val symptoms = buildSet {
            if (cycleDay < 2 || beforePeriod && chance(0.35)) add(Symptom.CRAMPS)
            if ((beforePeriod || cycleDay < 2) && chance(0.35)) add(Symptom.HEADACHE)
            if ((beforePeriod || cycleDay < 3) && chance(0.60)) add(Symptom.BLOATING)
            if (beforePeriod && chance(0.65)) add(Symptom.BREAST_TENDERNESS)
            if (beforePeriod && chance(0.40)) add(Symptom.ACNE)
            if ((beforePeriod || cycleDay < 2) && chance(0.65)) add(Symptom.FATIGUE)
        }
        val ids = mutableSetOf<Long>()
        val counts = mutableMapOf<Long, Int>()
        fun counted(id: Long, count: Int) {
            ids += id
            if (count >= 2) counts[id] = count
        }
        val poorSleep = pain == Pain.SEVERE || beforePeriod && chance(0.45) || travelling && chance(0.40)
        val sleep = if (poorSleep) 22L + random.nextInt(2) else 23L + random.nextInt(3)
        if (chance(0.90)) {
            ids += sleep
            if (sleep >= 24) ids += 5L
        }
        if (chance(0.90)) counted(21, 1 + random.nextInt(8))
        if (!bleeding && chance(0.28)) ids += 1L
        if (travelling || date.dayOfWeek.value <= 5 && chance(0.16)) ids += 2L
        if (travelling) ids += 3L
        if (date.dayOfWeek in setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY) && chance(0.28)) ids += 4L
        if (pain != Pain.SEVERE && chance(0.30)) ids += 6L
        if (pain in setOf(Pain.MODERATE, Pain.SEVERE)) {
            counted(7, if (pain == Pain.SEVERE) 2 + random.nextInt(2) else 1)
            if (chance(0.55)) counted(8, 1)
            ids += 9L
            if (chance(0.35)) ids += 10L
            if (chance(0.20)) ids += 11L
            if (chance(0.15)) ids += 16L
        } else if (pain == Pain.MILD && chance(0.35)) {
            counted(8, 1)
        }
        if (Symptom.HEADACHE in symptoms) ids += 13L
        if (Symptom.BREAST_TENDERNESS in symptoms) ids += 14L
        if (pain == Pain.SEVERE) {
            ids += 20L
            if (date.dayOfWeek.value <= 5 && chance(0.45)) ids += 18L
            if (chance(0.60)) ids += 19L
        }
        val note = when {
            date == travelNoteDay -> "Viel Stress und eine Reise diesen Monat.\nDer Zyklus war diesmal länger.\nHeute wieder zu Hause."
            chance(0.20) -> if (chance(0.22)) listOf(
                "Heute langsam in den Tag gestartet.", "Unterwegs eine Pause gemacht.", "Mittags kurz spazieren gewesen.",
                "Am Nachmittag zu Hause geblieben.", "Abends gelesen und früh ins Bett.", "Ein ruhiger Abschluss des Tages.",
            ).take(3 + random.nextInt(4)).joinToString("\n") else when {
                pain == Pain.SEVERE -> "Heute viel im Bett geblieben."
                travelling -> "Unterwegs, der Tag war ziemlich voll."
                beforePeriod -> "Müde und etwas gereizt."
                1L in ids -> "Nach der Arbeit beim Sport gewesen."
                5L in ids -> "Gut geschlafen und entspannt aufgewacht."
                else -> "Ein ruhiger Tag mit einem kleinen Spaziergang."
            }
            else -> null
        }
        return DayLog(
            date = date,
            flow = when {
                !bleeding -> FlowLevel.NONE
                cycleDay < 2 -> if (chance(0.65)) FlowLevel.HEAVY else FlowLevel.MEDIUM
                cycleDay == periodLength - 1 -> FlowLevel.SPOTTING
                cycleDay == periodLength - 2 -> FlowLevel.LIGHT
                else -> FlowLevel.MEDIUM
            },
            mood = if (chance(0.94)) when {
                beforePeriod || pain == Pain.SEVERE -> if (chance(0.40)) Mood.BAD else Mood.LOW
                bleeding -> if (chance(0.45)) Mood.LOW else Mood.OKAY
                poorSleep || travelling -> Mood.OKAY
                else -> listOf(Mood.OKAY, Mood.GOOD, Mood.GOOD, Mood.GREAT)[random.nextInt(4)]
            } else null,
            pain = pain, symptoms = symptoms,
            sex = if (!bleeding && chance(0.13)) {
                if (chance(0.90)) Sex.PROTECTED else Sex.UNPROTECTED
            } else null,
            discharge = if (chance(0.72)) when {
                midCycle -> if (chance(0.55)) Discharge.EGG_WHITE else Discharge.WATERY
                else -> if (chance(0.55)) Discharge.CREAMY else Discharge.STICKY
            } else null,
            appetite = if (chance(0.86)) when {
                beforePeriod -> Appetite.HIGH
                pain == Pain.SEVERE -> Appetite.LOW
                else -> if (chance(0.12)) Appetite.LOW else Appetite.NORMAL
            } else null,
            libido = if (chance(0.86)) when {
                midCycle -> Libido.HIGH
                beforePeriod || bleeding -> Libido.LOW
                else -> Libido.NORMAL
            } else null,
            tagIds = ids, tagCounts = counts, note = note,
        )
    }

    private fun assertSample(data: BackupData, today: LocalDate) {
        val periods = data.periods
        val logs = data.dayLogs
        assertEquals(3, data.schemaVersion)
        assertTrue(periods.first().start <= today.minusMonths(14))
        assertTrue(DAYS.between(periods.last().start, today) in 8..20)
        assertTrue(periods.all { it.end != null && DAYS.between(it.start, it.end) + 1 in 4..6 })
        val cycles = periods.zipWithNext().map { (a, b) -> DAYS.between(a.start, b.start) }
        assertEquals(1, cycles.count { it == 38L })
        assertTrue(cycles.all { it == 38L || it in 26..31 })
        assertEquals(today.minusDays(1), logs.last().date)
        assertFalse(logs.any { it.isEmpty() })
        val days = DAYS.between(periods.first().start, today).toDouble()
        assertTrue(1 - logs.size / days in 0.15..0.20)
        assertEquals(28, logs.count { it.date >= today.minusDays(30) })
        assertTrue(logs.count { it.note != null }.toDouble() / logs.size in 0.12..0.28)
        assertTrue(logs.any { (it.note?.lines()?.size ?: 0) in 3..6 })
        periods.forEach { period ->
            val entries = logs.filter { it.date in period.start..period.end!! }
            assertEquals((DAYS.between(period.start, period.end) + 1).toInt(), entries.size)
            assertTrue(entries.take(2).all { it.flow in setOf(FlowLevel.MEDIUM, FlowLevel.HEAVY) })
            assertEquals(FlowLevel.SPOTTING, entries.last().flow)
        }
        // These also guard the counted/pick-one conventions that the codec alone does not require.
        assertTrue(logs.all { log -> log.tagCounts.all { (id, count) -> id in log.tagIds && count >= 2 } })
        assertTrue(logs.all { log -> log.tagIds.count { it in 22L..25L } <= 1 })
        assertTrue(logs.any { it.tagCounts[7L]?.let { count -> count >= 2 } == true })
        assertTrue(logs.any { it.pain == Pain.SEVERE && 20L in it.tagIds })
        assertTrue(logs.any { it.note?.contains("Stress und eine Reise") == true })
        assertEquals(Situation(painDiary = true), data.situation)
    }
}
