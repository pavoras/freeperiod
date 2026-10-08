package org.freeperiod.app.ui.onboarding

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.freeperiod.app.R
import org.freeperiod.app.data.Repository
import org.freeperiod.app.data.SettingsStore
import org.freeperiod.engine.*

/** Selected calendar days remain the source of truth until the one-transaction import. */
data class OnboardingUiState(
    val page: Int = 0,
    val today: LocalDate,
    val selectedDays: Set<LocalDate> = emptySet(),
    val phase: LifePhase = LifePhase.REGULAR,
    val method: Method = Method.NONE,
    val pillActive: String = "",
    val pillBreak: String = "",
    val packStart: String = "",
    val typicalLength: String = "",
    val unknown: Boolean = false,
    val periodReminder: Boolean = false,
    val dailyReminder: Boolean = false,
    val methodReminder: Reminder? = null,
    val ongoing: Boolean? = null,
    val askOngoing: Boolean = false,
    val rangeMode: Boolean = false,
    val rangeStart: LocalDate? = null,
    val existingPeriods: List<Period> = emptyList(),
    val periodsCommitted: Boolean = false,
    val skippedPeriods: Int = 0,
    val busy: Boolean = false,
    val message: Int? = null,
) {
    val draftPeriods: List<Period> get() = selectedPeriods(selectedDays, ongoing == true)
    val hideCycleLength: Boolean get() {
        val accepted = existingPeriods.toMutableList()
        draftPeriods.forEach { if (PeriodRules.validate(accepted, it, today) == null) accepted += it }
        return PeriodRules.cycles(accepted).any { it.eligible }
    }
    val pill: PillSchedule? get() = runCatching {
        PillSchedule(LocalDate.parse(packStart), pillActive.toInt(), pillBreak.toInt())
    }.getOrNull()
    val validPill: Boolean get() = method != Method.PILL_COMBINED ||
        (pillActive.isBlank() && pillBreak.isBlank() && packStart.isBlank()) || pill != null
    val valid: Boolean get() = validPill && selectedDays.all { it <= today } &&
        (hideCycleLength || unknown || typicalLength.isBlank() || typicalLength.toIntOrNull() in 15..90)
    val situation get() = Situation(phase = phase, method = method, pill = pill)
}

internal fun selectedPeriods(days: Set<LocalDate>, ongoing: Boolean): List<Period> {
    val sorted = days.sorted()
    if (sorted.isEmpty()) return emptyList()
    val result = mutableListOf<Period>()
    var start = sorted.first()
    var end = start
    fun block() { result += Period(0, start, if (end == sorted.last() && ongoing) null else end) }
    for (date in sorted.drop(1)) {
        if (date != end.plusDays(1)) { block(); start = date }
        end = date
    }
    block()
    return result
}

class OnboardingViewModel(private val repository: Repository, private val settings: SettingsStore,
    private val clock: () -> LocalDate, private val savedState: SavedStateHandle) : ViewModel() {
    constructor(repository: Repository, settings: SettingsStore, clock: () -> LocalDate) : this(repository, settings, clock, SavedStateHandle())
    private val mutableState = MutableStateFlow(restoreOnboarding(savedState["draft"], clock()))
    val state = mutableState.asStateFlow()
    init { viewModelScope.launch { repository.periods.collect { periods -> change { it.copy(existingPeriods = periods) } } } }
    private fun change(transform: (OnboardingUiState) -> OnboardingUiState) {
        mutableState.update(transform)
        savedState["draft"] = state.value.savedDraft()
    }
    /** A full restore already contains situation, cycle settings and reminders. */
    fun finishRestoredBackup(): Job = viewModelScope.launch {
        try { settings.update { it.copy(onboardingDone = true) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { change { it.copy(message = R.string.onboarding_save_error) } }
    }
    fun setPhase(value: LifePhase) = change { it.copy(phase = value) }
    fun setMethod(value: Method) = change { it.copy(method = value, methodReminder = if (it.method == value) it.methodReminder else null) }
    fun setPillRhythm(active: String, rest: String) = change { it.copy(pillActive = active, pillBreak = rest) }
    fun setPackStart(value: String) = change { it.copy(packStart = value) }
    fun setTypicalLength(value: String) = change { it.copy(typicalLength = value, unknown = false) }
    fun setUnknown(value: Boolean) = change { it.copy(unknown = value) }
    fun setPeriodReminder(value: Boolean) = change { it.copy(periodReminder = value) }
    fun setDailyReminder(value: Boolean) = change { it.copy(dailyReminder = value) }
    fun setMethodReminder(value: Reminder?) = change { it.copy(methodReminder = value) }
    fun dismissOngoingQuestion() = change { it.copy(askOngoing = false) }
    fun setOngoing(value: Boolean) = change { it.copy(ongoing = value, askOngoing = false) }
    fun setRangeMode(value: Boolean) = change { it.copy(rangeMode = value, rangeStart = null) }
    fun toggleDay(date: LocalDate) {
        if (date > clock() || state.value.periodsCommitted) return
        val current = state.value
        if (current.rangeMode) {
            val start = current.rangeStart
            if (start == null) change { it.copy(rangeStart = date) }
            else { markRange(start, date); change { it.copy(rangeStart = null) } }
        } else change { it.copy(selectedDays = if (date in it.selectedDays) it.selectedDays - date else it.selectedDays + date,
            ongoing = if (date == it.today) null else it.ongoing) }
    }
    fun markRange(from: LocalDate, to: LocalDate) {
        if (state.value.periodsCommitted) return
        val first = minOf(from, to)
        val last = minOf(maxOf(from, to), clock())
        if (first > last) return
        val days = generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toSet()
        change { it.copy(selectedDays = it.selectedDays + days, ongoing = if (it.today in days) null else it.ongoing) }
    }
    fun systemSettingsUnavailable() = change { it.copy(message = R.string.settings_external_error) }
    fun next() {
        val current = state.value
        if (current.page == 2 && !current.validPill) return
        if (current.page == 3 && current.today in current.selectedDays && current.ongoing == null) {
            change { it.copy(askOngoing = true) }; return
        }
        if (current.page == 4 && !current.unknown && current.typicalLength.toIntOrNull() !in 15..90) return
        val page = if (current.page == 3 && current.hideCycleLength) 5 else (current.page + 1).coerceAtMost(5)
        change { it.copy(page = page, message = null) }
    }
    fun back() = change { it.copy(page = if (it.page == 5 && it.hideCycleLength) 3 else (it.page - 1).coerceAtLeast(0), message = null) }
    fun onResume(): Job = viewModelScope.launch { change { it.copy(today = clock()) } }
    fun skip(): Job {
        when (state.value.page) {
            2 -> if (!state.value.validPill) change { it.copy(pillActive = "", pillBreak = "", packStart = "") }
            3 -> if (!state.value.periodsCommitted) change { it.copy(selectedDays = emptySet(), ongoing = null, rangeStart = null) }
            4 -> change { it.copy(unknown = true) }
            5 -> { change { it.copy(periodReminder = false, dailyReminder = false, methodReminder = null) }; return finish() }
        }
        next()
        return viewModelScope.launch { }
    }
    fun finish(): Job = viewModelScope.launch {
        if (state.value.busy) return@launch
        val draft = state.value.copy(today = clock())
        if (!draft.valid) { change { it.copy(message = R.string.onboarding_data_error) }; return@launch }
        if (draft.today in draft.selectedDays && draft.ongoing == null) {
            change { it.copy(askOngoing = true) }; return@launch
        }
        change { it.copy(busy = true, message = null) }
        try {
            if (!draft.periodsCommitted) {
                val skipped = repository.addPeriods(draft.draftPeriods)
                change { it.copy(periodsCommitted = true, skippedPeriods = skipped.size) }
                if (skipped.isNotEmpty()) { change { it.copy(page = 5) }; return@launch }
            }
            repository.updateSituation(draft.situation)
            if (!draft.hideCycleLength) repository.updateDomainSettings(repository.snapshot().settings.copy(
                typicalCycleLength = if (draft.unknown || draft.typicalLength.isBlank()) null else draft.typicalLength.toInt()))
            settings.migrateReminders(repository)
            val reminders = repository.snapshot().reminders
            listOf(ReminderKind.PERIOD_DUE to draft.periodReminder, ReminderKind.DAILY_LOG to draft.dailyReminder).forEach { (kind, enabled) ->
                val existing = reminders.firstOrNull { it.kind == kind }
                repository.saveReminder(existing?.copy(enabled = enabled) ?: Reminder(0, kind, null, Recurrence.Daily,
                    LocalTime.of(20, 0), enabled, if (kind == ReminderKind.PERIOD_DUE) 2 else null))
            }
            draft.methodReminder?.let { reminder ->
                val existing = reminders.find { it.kind == reminder.kind && it.recurrence == reminder.recurrence && it.time == reminder.time }
                repository.saveReminder(reminder.copy(id = existing?.id ?: 0))
            }
            settings.update { it.copy(onboardingDone = true) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { change { it.copy(message = R.string.onboarding_save_error) } }
        finally { change { it.copy(busy = false) } }
    }
}

private fun OnboardingUiState.savedDraft() = Bundle().apply {
    putInt("page", page); putLongArray("days", selectedDays.map { it.toEpochDay() }.toLongArray())
    putString("phase", phase.name); putString("method", method.name)
    putString("active", pillActive); putString("break", pillBreak); putString("pack", packStart)
    putString("length", typicalLength); putBoolean("unknown", unknown)
    putBoolean("periodReminder", periodReminder); putBoolean("dailyReminder", dailyReminder)
    putString("methodReminder", methodReminder?.let { Json.encodeToString(it) })
    ongoing?.let { putBoolean("ongoing", it) }; putBoolean("askOngoing", askOngoing)
    putBoolean("rangeMode", rangeMode); rangeStart?.let { putLong("rangeStart", it.toEpochDay()) }
    putBoolean("committed", periodsCommitted); putInt("skipped", skippedPeriods)
}
private fun restoreOnboarding(bundle: Bundle?, today: LocalDate): OnboardingUiState {
    if (bundle == null) return OnboardingUiState(today = today)
    return OnboardingUiState(page = bundle.getInt("page"), today = today,
        selectedDays = (bundle.getLongArray("days") ?: longArrayOf()).map(LocalDate::ofEpochDay).toSet(),
        phase = LifePhase.valueOf(bundle.getString("phase") ?: LifePhase.REGULAR.name),
        method = Method.valueOf(bundle.getString("method") ?: Method.NONE.name),
        pillActive = bundle.getString("active").orEmpty(), pillBreak = bundle.getString("break").orEmpty(), packStart = bundle.getString("pack").orEmpty(),
        typicalLength = bundle.getString("length").orEmpty(), unknown = bundle.getBoolean("unknown"),
        periodReminder = bundle.getBoolean("periodReminder"), dailyReminder = bundle.getBoolean("dailyReminder"),
        methodReminder = bundle.getString("methodReminder")?.let { Json.decodeFromString<Reminder>(it) },
        ongoing = if (bundle.containsKey("ongoing")) bundle.getBoolean("ongoing") else null, askOngoing = bundle.getBoolean("askOngoing"),
        rangeMode = bundle.getBoolean("rangeMode"), rangeStart = if (bundle.containsKey("rangeStart")) LocalDate.ofEpochDay(bundle.getLong("rangeStart")) else null,
        periodsCommitted = bundle.getBoolean("committed"), skippedPeriods = bundle.getInt("skipped"))
}
