package org.freeperiod.app.summary

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.freeperiod.app.R
import org.freeperiod.app.ui.day.builtInCategories
import org.freeperiod.app.ui.day.builtInEntryItems
import org.freeperiod.app.ui.day.countedLabel
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupData

/** One day of the summary as text: built-ins in the app language, renames and own items as the user wrote them. */
internal data class SummaryRow(val date: LocalDate, val bleeding: String, val pain: String, val medication: String,
    val other: String, val note: String?)

internal fun summaryRows(summary: Summary, data: BackupData, text: (Int) -> String, notes: Boolean): List<SummaryRow> {
    val categories = data.customCategories.associateBy { it.id }
    fun title(field: String) = resolveEntryAppearance("category:$field",
        text(builtInCategories.first { it.key == field }.label), "", data.overrides).label
    fun builtIn(field: String, name: String): String {
        val item = builtInEntryItems(field).first { it.name == name }
        return resolveEntryAppearance(item.key, text(item.label), "", data.overrides).label
    }
    return summary.days.map { day ->
        val log = day.log
        val tags = data.tags.filter { it.id in log.tagIds }
        fun own(field: String) = tags.filter { categories[it.categoryId]?.builtInField() == field }
            .map { countedLabel(it.name, log.count(it.id)) }
        fun field(field: String, vararg values: String?) =
            (values.filterNotNull() + own(field)).takeIf { it.isNotEmpty() }?.let { "${title(field)}: ${it.joinToString()}" }
        val other = listOfNotNull(
            field("mood", log.mood?.let { builtIn("mood", it.name) }),
            field("symptoms", *log.symptoms.sortedBy { it.ordinal }.map { builtIn("symptoms", it.name) }.toTypedArray()),
            field("sex", log.sex?.let { builtIn("sex", it.name) }),
            field("discharge", log.discharge?.let { builtIn("discharge", it.name) }),
            log.ovulationTest?.let { "${title("ovulation_test")}: ${builtIn("ovulation_test", it.name)}" },
            tags.filter { it.categoryId == null }.map { it.name }.takeIf { it.isNotEmpty() }?.let { "${title("tags")}: ${it.joinToString()}" },
        ) + tags.filter { categories[it.categoryId]?.let { category -> category.builtInField() == null } == true }
            .groupBy { requireNotNull(it.categoryId) }
            .map { (id, items) -> "${categories.getValue(id).name}: ${items.joinToString { countedLabel(it.name, log.count(it.id)) }}" }
        SummaryRow(day.date,
            log.flow?.let { builtIn("flow", it.name) } ?: if (day.periodDay) text(R.string.summary_period_day) else "",
            (listOfNotNull(log.pain?.let { builtIn("pain", it.name) }) + own("pain")).joinToString(),
            own("medication").joinToString(), other.joinToString("; "),
            log.note?.trim()?.takeIf { notes && it.isNotEmpty() })
    }
}

/** A4 pages drawn with Android's PdfDocument; nothing leaves the device until the user saves the file. */
internal object SummaryPdf {
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 40f
    private const val FOOTER = 36f
    private const val GAP = 6f

    fun render(summary: Summary, rows: List<SummaryRow>, text: (Int) -> String, created: LocalDate, locale: Locale): ByteArray {
        val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val writer = Writer(text)
        writer.line(text(R.string.summary_title), writer.title)
        writer.line("${summary.from.format(dates)} – ${summary.to.format(dates)}", writer.heading)
        writer.line(text(R.string.summary_created).format(created.format(dates)), writer.small)
        writer.space(12f)

        writer.line(text(R.string.summary_periods), writer.heading)
        if (summary.periods.isEmpty()) writer.line(text(R.string.summary_no_periods), writer.body)
        else writer.table(listOf(110f, 110f, 80f, 215f),
            listOf(text(R.string.summary_start), text(R.string.summary_end), text(R.string.summary_days), text(R.string.summary_cycle)),
            summary.periods.map { period -> listOf(period.start.format(dates), period.end?.format(dates) ?: text(R.string.summary_ongoing),
                period.days?.toString().orEmpty(), period.cycleDays?.toString().orEmpty()) to null })
        writer.space(12f)

        writer.line(text(R.string.summary_day_by_day), writer.heading)
        if (rows.isEmpty()) writer.line(text(R.string.summary_no_days), writer.body)
        else writer.table(listOf(70f, 66f, 62f, 112f, 205f),
            listOf(text(R.string.summary_date), text(R.string.summary_bleeding), text(R.string.entry_pain),
                text(R.string.entry_medication), text(R.string.summary_other)),
            rows.map { row -> listOf(row.date.format(dates), row.bleeding, row.pain, row.medication, row.other) to
                row.note?.let { text(R.string.summary_note).format(it) } })
        return writer.finish()
    }

    private class Writer(private val text: (Int) -> String) {
        private fun paint(size: Float, bold: Boolean = false, color: Int = Color.rgb(40, 34, 32)) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        val title = paint(18f, bold = true)
        val heading = paint(12f, bold = true)
        val body = paint(9f)
        val bold = paint(9f, bold = true)
        val small = paint(8f, color = Color.rgb(110, 100, 96))
        private val rule = Paint().apply { color = Color.rgb(220, 214, 210); strokeWidth = 0.6f }
        private val shade = Paint().apply { color = Color.rgb(244, 240, 237) }
        private val document = PdfDocument()
        private var page: PdfDocument.Page? = null
        private var number = 0
        private var y = MARGIN

        init { newPage() }

        private fun newPage() {
            page?.let(::close)
            number++
            page = document.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, number).create())
            y = MARGIN
        }

        // Every page says where the content comes from and what FreePeriod. is not.
        private fun close(done: PdfDocument.Page) {
            val footer = layout("${text(R.string.summary_footer)} · ${text(R.string.summary_page).format(number)}", small, WIDTH - 2 * MARGIN)
            done.canvas.save()
            done.canvas.translate(MARGIN, HEIGHT - MARGIN - footer.height + 12f)
            footer.draw(done.canvas)
            done.canvas.restore()
            document.finishPage(done)
        }

        private fun layout(value: String, paint: TextPaint, width: Float): StaticLayout =
            StaticLayout.Builder.obtain(value, 0, value.length, paint, width.toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).build()

        private fun fits(height: Float) = y + height <= HEIGHT - MARGIN - FOOTER

        private fun draw(layout: StaticLayout, x: Float) {
            val canvas = requireNotNull(page).canvas
            canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore()
        }

        fun space(height: Float) { y += height }

        fun line(value: String, paint: TextPaint) {
            val layout = layout(value, paint, WIDTH - 2 * MARGIN)
            if (!fits(layout.height.toFloat())) newPage()
            draw(layout, MARGIN)
            y += layout.height + 4f
        }

        /** Rows never split across pages; the header repeats on each new page. */
        fun table(widths: List<Float>, header: List<String>, rows: List<Pair<List<String>, String?>>) {
            fun drawHeader() {
                val cells = header.mapIndexed { i, value -> layout(value, bold, widths[i] - GAP) }
                val height = cells.maxOf { it.height } + 2 * GAP
                requireNotNull(page).canvas.drawRect(MARGIN, y, WIDTH - MARGIN, y + height, shade)
                y += GAP
                var x = MARGIN + GAP / 2
                cells.forEachIndexed { i, cell -> draw(cell, x); x += widths[i] }
                y += height - GAP
            }
            if (!fits(60f)) newPage()
            drawHeader()
            rows.forEach { (values, note) ->
                val cells = values.mapIndexed { i, value -> layout(value, body, widths[i] - GAP) }
                val noteLayout = note?.let { layout(it, small, WIDTH - 2 * MARGIN - widths[0] - GAP) }
                val height = cells.maxOf { it.height } + (noteLayout?.let { it.height + 2f } ?: 0f) + 2 * GAP
                if (!fits(height)) { newPage(); drawHeader() }
                y += GAP
                var x = MARGIN + GAP / 2
                cells.forEachIndexed { i, cell -> draw(cell, x); x += widths[i] }
                noteLayout?.let { y += cells.maxOf { cell -> cell.height } + 2f; draw(it, MARGIN + GAP / 2 + widths[0]); y -= cells.maxOf { cell -> cell.height } + 2f }
                y += height - GAP
                requireNotNull(page).canvas.drawLine(MARGIN, y, WIDTH - MARGIN, y, rule)
            }
        }

        fun finish(): ByteArray {
            page?.let(::close)
            page = null
            return ByteArrayOutputStream().use { out -> document.writeTo(out); document.close(); out.toByteArray() }
        }
    }
}
