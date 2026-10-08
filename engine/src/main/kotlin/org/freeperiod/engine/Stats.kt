package org.freeperiod.engine

import java.time.LocalDate
import kotlin.math.abs

/** Statistical helpers shared by predictions and history. */
object Stats {
    /** Returns the median, averaging the middle pair; requires at least one value. */
    fun median(values: List<Int>): Double = medianOf(values.map { it.toDouble() })

    /** Returns the median absolute deviation, preserving fractional medians. */
    fun mad(values: List<Int>): Double {
        val centre = median(values)
        return medianOf(values.map { abs(it - centre) })
    }

    /** Counts each logged symptom on dates in [from, toExclusive). */
    fun symptomCounts(logs: Collection<DayLog>, from: LocalDate, toExclusive: LocalDate): Map<Symptom, Int> =
        logs.asSequence().filter { it.date >= from && it.date < toExclusive }
            .flatMap { it.symptoms.asSequence() }.groupingBy { it }.eachCount()

    private fun medianOf(values: List<Double>): Double {
        require(values.isNotEmpty()) { "A median needs at least one value" }
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}

/** Hints use up to twelve eligible prior cycles, including auto-excluded long candidates. */
fun longCycleHints(cycles: List<Cycle>): Set<Long> {
    val baseline = ArrayDeque<Int>()
    val hints = mutableSetOf<Long>()
    for (cycle in cycles.sortedBy { it.start }) {
        if ((cycle.eligible || cycle.ineligibleReason == IneligibleReason.TOO_LONG) &&
            baseline.size >= 3 && cycle.length > 1.6 * Stats.median(baseline.toList())) {
            hints += cycle.startPeriodId
        }
        if (cycle.eligible) {
            baseline.addLast(cycle.length)
            if (baseline.size > 12) baseline.removeFirst()
        }
    }
    return hints
}
