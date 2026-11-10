package org.freeperiod.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit.DAYS
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.freeperiod.app.data.AppSettings
import org.freeperiod.app.data.PeriodValidationException
import org.freeperiod.app.data.Repository
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.engine.*
import org.freeperiod.engine.backup.BackupData

data class DayMarks(val period: Boolean, val predicted: Boolean, val logged: Boolean, val today: Boolean,
    val higherChance: Boolean = false, val possible: Boolean = false,
    /** The single most likely day of the higher-chance days: its tick is drawn bolder (never named or explained). */
    val peak: Boolean = false)

/**
 * Entering a period right in the calendar: a long press picks [start] (or, from "Has your period started?", the next
 * tap does), the next tap the last day. With [periodId] the tap sets the last day of that existing period.
 * [canContinue]: the start is recent and no period began after it, so it may still be going on.
 */
data class RangePick(val start: LocalDate?, val periodId: Long? = null, val canContinue: Boolean = false)

/** A question on Today when the period goes on, or stays away, longer than usual. */
enum class HomeQuestion { PERIOD_OVER, PERIOD_STARTED }

data class TodayUiState(
    val today: LocalDate,
    val month: YearMonth = YearMonth.from(today),
    val prediction: PredictionState = PredictionState.NoData,
    val cycleDay: Int? = null,
    val periodEndForToday: LocalDate? = null,
    val periodDay: Int? = null,
    val timeline: CycleTimeline? = null,
    val situation: Situation = Situation(),
    val fertileWindow: ClosedRange<LocalDate>? = null,
    val endSaved: PeriodEndReceipt? = null,
    val startedPeriodId: Long? = null,
    val ongoingPeriodId: Long? = null,
    val days: Map<LocalDate, DayMarks> = emptyMap(),
    val loading: Boolean = true,
    val writing: Boolean = false,
    val error: TodayError? = null,
    /** Shows the cycle ring rather than the calendar. */
    val ring: Boolean = false,
    /** Counts requests to scroll the calendar back to today (tap on the big number or on the Today tab). */
    val todayJump: Int = 0,
    /** The saved-end snackbar for [endSaved] was already shown (not again when Today opens anew). */
    val endSavedShown: Boolean = false,
    val pick: RangePick? = null,
    /** The cycle card is folded up to its buttons. */
    val cardFolded: Boolean = false,
    val question: HomeQuestion? = null,
    /** The period the question is about: the ongoing one, or the last one before the missing start. */
    val questionPeriodId: Long? = null,
    /** Show the short tour once. */
    val showTips: Boolean = false,
    /** The calendar shows the whole year instead of scrolling months. */
    val yearView: Boolean = false,
    /** Where the next calendar jump goes: this month, or today when null. */
    val jumpMonth: YearMonth? = null,
)

data class PeriodEndReceipt(val before: Period, val after: Period)

data class TodayError(val periodError: PeriodError? = null)

class TodayViewModel(
    private val repository: Repository,
    private val settings: SettingsStore,
    private val clock: () -> LocalDate = { LocalDate.now() },
    private val situation: (() -> Situation)? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TodayUiState(clock()))
    private val answered = mutableSetOf<Pair<HomeQuestion, Long?>>()
    val state: StateFlow<TodayUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.periods, repository.dayLogs, repository.domainSettings, settings.settings, repository.situation) {
                    _, _, _, _, _ -> Unit
            }.collect { refresh().join() }
        }
        viewModelScope.launch {
            settings.settings.map { it.homeRing }.distinctUntilChanged().collect { ring -> mutableState.update { it.copy(ring = ring) } }
        }
        viewModelScope.launch {
            settings.settings.map { it.homeCardFolded to !it.tipsSeen }.distinctUntilChanged().collect { (folded, tips) ->
                mutableState.update { it.copy(cardFolded = folded, showTips = tips) }
            }
        }
    }

    fun foldCard(folded: Boolean): Job = viewModelScope.launch {
        mutableState.update { it.copy(cardFolded = folded) }
        settings.update { it.copy(homeCardFolded = folded) }
    }

    fun tipsSeen(): Job = viewModelScope.launch {
        mutableState.update { it.copy(showTips = false) }
        settings.update { it.copy(tipsSeen = true) }
    }

    fun endSavedShown() { mutableState.update { it.copy(endSavedShown = true) } }

    /** Long press on a day: it becomes the first day of a period to enter; inside a saved period, its last day is picked next. */
    fun longPressDay(date: LocalDate): Job = viewModelScope.launch {
        if (date > clock() || mutableState.value.writing) return@launch
        val periods = repository.snapshot().periods
        val period = PeriodRules.periodOn(periods, date, clock())
        val canContinue = date >= clock().minusDays(13) && periods.none { it.start > date }
        mutableState.update {
            it.copy(pick = period?.let { saved -> RangePick(saved.start, saved.id) } ?: RangePick(date, canContinue = canContinue), error = null)
        }
    }

    /** A tap while picking: the first day (when none yet) or the last day, which saves. */
    fun pickDay(date: LocalDate): Job {
        val pick = mutableState.value.pick
        if (pick == null || date > clock()) return Job().apply { complete() }
        val start = pick.start ?: return longPressDay(date)
        return mutate {
            if (pick.periodId != null) {
                val period = requireNotNull(repository.snapshot().periods.find { it.id == pick.periodId })
                repository.updatePeriod(period.copy(end = date)).getOrThrow()
            } else repository.addPeriod(minOf(start, date), maxOf(start, date)).getOrThrow()
            mutableState.update { it.copy(pick = null) }
        }
    }

    /** Only the picked day, or (for a recent start) a period that is still going on. */
    fun pickSingle(ongoing: Boolean): Job {
        val pick = mutableState.value.pick
        val start = pick?.start ?: return Job().apply { complete() }
        if (ongoing && !pick.canContinue) return Job().apply { complete() }
        return mutate {
            repository.addPeriod(start, if (ongoing) null else start).getOrThrow()
            mutableState.update { it.copy(pick = null) }
        }
    }

    fun cancelPick() { mutableState.update { it.copy(pick = null, error = null) } }

    /** Yes picks the last day (or the first one); no is remembered for this period. */
    fun answer(question: HomeQuestion, yes: Boolean): Job = viewModelScope.launch {
        val id = mutableState.value.questionPeriodId ?: return@launch
        if (yes) {
            val period = repository.snapshot().periods.find { it.id == id }
            mutableState.update {
                it.copy(question = null, pick = if (question == HomeQuestion.PERIOD_OVER && period != null)
                    RangePick(period.start, period.id) else RangePick(null))
            }
        } else {
            // Kept here too, so a refresh that read the settings before the write cannot bring the question back.
            answered += question to id
            mutableState.update { it.copy(question = null) }
            settings.update {
                if (question == HomeQuestion.PERIOD_OVER) it.copy(askedEndFor = id) else it.copy(askedStartAfter = id)
            }
        }
    }

    fun jumpToToday() { mutableState.update { it.copy(todayJump = it.todayJump + 1, jumpMonth = null, yearView = false) } }

    fun showYear(year: Boolean) { mutableState.update { it.copy(yearView = year) } }

    /** A month tapped in the year view opens in the scrolling calendar. */
    fun openMonth(month: YearMonth) {
        mutableState.update { it.copy(yearView = false, month = month, jumpMonth = month, todayJump = it.todayJump + 1) }
    }

    fun showRing(ring: Boolean): Job = viewModelScope.launch {
        mutableState.update { it.copy(ring = ring) }
        settings.update { it.copy(homeRing = ring) }
    }

    fun refresh(): Job = viewModelScope.launch {
        try {
            recompute(repository.snapshot(), settings.settings.first())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(loading = false, error = TodayError()) }
        }
    }

    fun onResume(): Job = refresh()

    fun showMonth(month: YearMonth): Job {
        mutableState.update { it.copy(month = month) }
        return refresh()
    }

    fun startPeriodToday(): Job = mutate {
        val today = clock()
        // Ended today or yesterday and bleeding again: that period goes on, instead of a second one right next to it.
        val recent = repository.snapshot().periods
            .filter { period -> period.start <= today && period.end?.let { it >= today.minusDays(1) } == true }.maxByOrNull { it.start }
        val period = recent?.let { repository.updatePeriod(it.copy(end = null)).getOrThrow() }
            ?: repository.addPeriod(today, null).getOrThrow()
        mutableState.update { it.copy(startedPeriodId = period.id, endSaved = null) }
    }

    fun confirmEnd(date: LocalDate): Job = mutate {
        val ongoing = requireNotNull(repository.snapshot().periods.lastOrNull { it.end == null && it.start <= clock() })
        // Reject a stale card after midnight instead of saving the wrong bleeding day.
        require(date == periodEndForTapToday(ongoing, clock()))
        val saved = repository.endPeriod(ongoing.id, date).getOrThrow()
        mutableState.update { it.copy(endSaved = PeriodEndReceipt(ongoing, saved), endSavedShown = false) }
    }

    fun undoPeriodEnd(receipt: PeriodEndReceipt): Job = mutate {
        val current = repository.snapshot().periods.find { it.id == receipt.after.id }
        require(current == receipt.after)
        repository.updatePeriod(receipt.before).getOrThrow()
        mutableState.update { it.copy(endSaved = null) }
    }

    fun pausePredictions(): Job = mutate {
        repository.updateDomainSettings(repository.snapshot().settings.copy(predictionsPaused = true))
    }

    fun dismissError() { mutableState.update { it.copy(error = null) } }

    private fun mutate(action: suspend () -> Unit): Job = viewModelScope.launch {
        if (mutableState.value.writing) return@launch
        mutableState.update { it.copy(writing = true, error = null) }
        try {
            action()
            recompute(repository.snapshot(), settings.settings.first())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            mutableState.update { it.copy(error = TodayError((failure as? PeriodValidationException)?.error)) }
        } finally {
            mutableState.update { it.copy(writing = false) }
        }
    }

    private fun recompute(data: BackupData, device: AppSettings) {
        mutableState.update { previous ->
            todayState(data, clock(), previous.month, situation?.invoke() ?: data.situation, device.askedEndFor,
                device.askedStartAfter).copy(writing = previous.writing,
                error = previous.error, endSaved = previous.endSaved, startedPeriodId = previous.startedPeriodId, ring = previous.ring,
                todayJump = previous.todayJump, endSavedShown = previous.endSavedShown, pick = previous.pick,
                cardFolded = previous.cardFolded, showTips = previous.showTips, yearView = previous.yearView,
                jumpMonth = previous.jumpMonth).let { next ->
                    val asked = next.question?.let { it to next.questionPeriodId }
                    if (asked != null && asked in answered) next.copy(question = null) else next
                }
        }
    }
}

/** The card, timeline and calendar share one snapshot and one injected calendar date. */
internal fun todayState(data: BackupData, today: LocalDate, month: YearMonth = YearMonth.from(today),
    situation: Situation = data.situation, askedEndFor: Long? = null, askedStartAfter: Long? = null): TodayUiState {
    val periods = data.periods.filter { it.start <= today }.sortedBy { it.start }
    val prediction = predict(periods, PredictionSettings(data.settings.typicalCycleLength, data.settings.predictionsPaused), situation, today)
    val chances = periodChances(prediction)
    val breakDays = (prediction as? PredictionState.ScheduledBreak)?.range
    val window = fertileWindow(prediction, situation)
    val peak = peakDay(prediction, situation)
    val long = PeriodRules.periodUnusuallyLong(periods, today)?.takeIf { it.id != askedEndFor }
    // Only while the next period is predicted from history: not with the pill, in menopause or with predictions paused.
    val far = PeriodRules.startUnusuallyFar(periods, today)?.takeIf {
        it.id != askedStartAfter && (prediction is PredictionState.Range || prediction is PredictionState.RangePassed ||
            prediction is PredictionState.Varies)
    }
    val logged = data.dayLogs.filterNot { it.isEmpty() }.map { it.date }.toSet()
    val ongoing = periods.lastOrNull { it.end == null }
    val lengths = PeriodRules.cycles(periods).filter { it.eligible }.takeLast(6).map { it.length }
    val fallback = data.settings.typicalCycleLength ?: lengths.takeIf { it.isNotEmpty() }?.let { Stats.median(it).roundToInt() } ?: 28
    return TodayUiState(today = today, month = month, prediction = prediction,
        cycleDay = PeriodRules.cycleDay(periods, today),
        periodEndForToday = ongoing?.let { periodEndForTapToday(it, today) },
        periodDay = ongoing?.let { Math.toIntExact(DAYS.between(it.start, today) + 1) },
        ongoingPeriodId = ongoing?.id,
        timeline = cycleTimeline(periods, prediction, situation.pill, fallback, today), situation = situation,
        fertileWindow = window,
        question = if (long != null) HomeQuestion.PERIOD_OVER else if (far != null) HomeQuestion.PERIOD_STARTED else null,
        questionPeriodId = (long ?: far)?.id,
        days = calendarMonths(YearMonth.from(today)).flatMap { shown -> (1..shown.lengthOfMonth()).map { shown.atDay(it) } }.associate { date ->
            date to DayMarks(PeriodRules.periodOn(periods, date, today) != null,
                breakDays?.contains(date) == true || chances[date] == PeriodChance.LIKELY, date in logged, date == today,
                window?.contains(date) == true, chances[date] == PeriodChance.POSSIBLE, date == peak)
        }, loading = false)
}
