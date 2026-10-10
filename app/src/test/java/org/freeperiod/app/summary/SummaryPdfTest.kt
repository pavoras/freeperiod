package org.freeperiod.app.summary

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS")
/** Rows only: Robolectric has no working PdfDocument, so drawing is checked on a device. */
class SummaryPdfTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.of(2026, 4, 12)
    private val data = BackupData(periods = listOf(Period(1, today.minusDays(3), today.minusDays(1))),
        dayLogs = listOf(DayLog(today.minusDays(2), flow = FlowLevel.MEDIUM, mood = Mood.GOOD, symptoms = setOf(Symptom.CRAMPS),
            pain = Pain.MILD, note = " Private ", tagIds = setOf(5, 6), tagCounts = mapOf(5L to 2))),
        tags = listOf(Tag(5, "My pills", categoryId = 1), Tag(6, "Back", categoryId = 2)), settings = BackupSettings(null, false),
        customCategories = listOf(CustomCategory(1, "Medication", "builtin:medication", 0, false),
            CustomCategory(2, "Where it hurts", "person", 100, false)),
        overrides = listOf(UiOverride("item:mood:GOOD", false, 3, label = "Content")))
    private val facts = summary(data.periods, data.dayLogs, today.minusDays(30), today)

    @Test fun rowsUseTheUsersNamesAndCountsAndLeaveNotesOutUnlessAsked() {
        val rows = summaryRows(facts, data, context::getString, notes = false)
        assertEquals(listOf(today.minusDays(3), today.minusDays(2), today.minusDays(1)), rows.map { it.date })
        assertEquals("Period", rows[0].bleeding)
        val row = rows[1]
        assertEquals("Medium", row.bleeding)
        assertEquals("Mild", row.pain)
        assertEquals("My pills ×2", row.medication)
        assertEquals("Mood: Content; Symptoms: Cramps; Where it hurts: Back", row.other)
        assertNull(row.note)
        assertEquals("Private", summaryRows(facts, data, context::getString, notes = true)[1].note)
    }
}
