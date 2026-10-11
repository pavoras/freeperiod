package org.freeperiod.app.ui.nav

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.freeperiod.app.AppContainer
import org.freeperiod.app.R
import org.freeperiod.app.ui.components.FpNavBar
import org.freeperiod.app.ui.today.TodayScreen
import org.freeperiod.app.ui.today.TodayViewModel
import org.freeperiod.app.ui.history.HistoryScreen
import org.freeperiod.app.ui.history.HistoryViewModel
import org.freeperiod.app.ui.day.DayEntryActions
import org.freeperiod.app.ui.day.DayEntrySheet
import org.freeperiod.app.ui.day.DayEntrySheetFrame
import org.freeperiod.app.ui.day.DayEntryViewModel
import org.freeperiod.app.ui.settings.ImportRoute
import org.freeperiod.app.ui.settings.SettingsRoute
import org.freeperiod.app.ui.settings.rememberReminderAccess
import org.freeperiod.app.ui.onboarding.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppNav(container: AppContainer, todayViewModel: TodayViewModel, onboardingDone: Boolean) {
    val scope = rememberCoroutineScope()
    // Recreate the graph when onboarding completes, removing onboarding from back navigation.
    key(onboardingDone) {
        val navigation = rememberNavController()
        val entry by navigation.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        var settingsHome by remember { mutableIntStateOf(0) }
        Scaffold(bottomBar = {
            if (onboardingDone && route != "day/{epochDay}" && route != "settings/day-entry") {
                FpNavBar(route) { destination ->
                    // Today again while on Today: the calendar goes back to today.
                    if (destination == "today" && route == "today") return@FpNavBar todayViewModel.jumpToToday()
                    // Settings again while in Settings: back to the overview.
                    if (destination == "settings" && route == "settings") return@FpNavBar run { settingsHome++ }
                    navigation.navigate(destination) {
                        popUpTo(navigation.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }) { padding ->
            NavHost(navigation, startDestination = if (onboardingDone) "today" else "onboarding",
                modifier = Modifier.padding(padding)) {
                composable("onboarding") {
                    val model: OnboardingViewModel = viewModel(factory = viewModelFactory {
                        initializer { OnboardingViewModel(container.repository, container.settings, container.clock, createSavedStateHandle()) }
                    })
                    val state by model.state.collectAsStateWithLifecycle()
                    ResumeAndMidnightEffect(model::onResume)
                    val access = rememberReminderAccess(container.notifications)
                    val context = LocalContext.current
                    androidx.activity.compose.BackHandler(state.page > 0, onBack = model::back)
                    OnboardingScreen(state, OnboardingActions(
                        track = { key, on -> model.setTracked(key, on) },
                        importPeriods = { navigation.navigate("import") },
                        next = model::next, back = model::back, skip = { model.skip() }, finish = { model.finish() },
                        phase = model::setPhase, method = model::setMethod, pillRhythm = model::setPillRhythm, packStart = model::setPackStart,
                        day = model::toggleDay, range = model::markRange, rangeMode = model::setRangeMode, ongoing = model::setOngoing, dismissOngoing = model::dismissOngoingQuestion,
                        length = model::setTypicalLength, unknown = model::setUnknown,
                        periodReminder = { model.setPeriodReminder(it); if (it) access.requestPermission() },
                        dailyReminder = { model.setDailyReminder(it); if (it) access.requestPermission() },
                        methodReminder = { model.setMethodReminder(it); if (it?.enabled == true) access.requestPermission() },
                        systemSettings = {
                            try { context.startActivity(access.settingsIntent) }
                            catch (_: ActivityNotFoundException) { model.systemSettingsUnavailable() }
                            catch (_: SecurityException) { model.systemSettingsUnavailable() }
                        }), access.available)
                }
                composable("import") { importEntry ->
                    val onboardingEntry = remember(importEntry) { navigation.getBackStackEntry("onboarding") }
                    val model: OnboardingViewModel = viewModel(viewModelStoreOwner = onboardingEntry, factory = viewModelFactory {
                        initializer { OnboardingViewModel(container.repository, container.settings, container.clock, createSavedStateHandle()) }
                    })
                    ImportRoute(container, onRestored = { model.finishRestoredBackup() }) { navigation.popBackStack() }
                }
                composable("today") {
                    val state by todayViewModel.state.collectAsStateWithLifecycle()
                    ResumeAndMidnightEffect(todayViewModel::onResume)
                    TodayScreen(state, { todayViewModel.startPeriodToday() }, { todayViewModel.confirmEnd(it) },
                        { navigation.navigate("day/${it.toEpochDay()}") }, { todayViewModel.pausePredictions() },
                        { todayViewModel.showMonth(it) }, todayViewModel::dismissError,
                        onUndoEnd = { todayViewModel.undoPeriodEnd(it) }, onRingChange = { todayViewModel.showRing(it) },
                        onJumpToToday = { todayViewModel.jumpToToday() })
                }
                composable("history") {
                    val model: HistoryViewModel = viewModel(factory = viewModelFactory {
                        initializer { HistoryViewModel(container.repository, container.clock) }
                    })
                    val state by model.state.collectAsStateWithLifecycle()
                    ResumeAndMidnightEffect(model::onResume)
                    HistoryScreen(state, onInclude = { id, included -> model.setCycleIncluded(id, included) }, onDismissHint = { model.dismissLongCycleHint(it) })
                }
                composable("settings") { SettingsRoute(container, home = settingsHome) }
                composable("settings/day-entry") { SettingsRoute(container, dayEntryOnly = true) { navigation.popBackStack() } }
                composable("day/{epochDay}", arguments = listOf(navArgument("epochDay") { type = NavType.LongType })) { day ->
                    // One sheet stays open; the arrows slide the next or previous day in from the side they point to.
                    var shown by rememberSaveable { mutableLongStateOf(requireNotNull(day.arguments).getLong("epochDay")) }
                    val dismissals = remember { mutableMapOf<Long, () -> Unit>() }
                    Surface(Modifier.fillMaxSize()) {
                        DayEntrySheetFrame({ dismissals[shown]?.invoke() }) {
                            AnimatedContent(shown, transitionSpec = {
                                val forward = targetState > initialState
                                (slideInHorizontally { if (forward) it else -it } togetherWith slideOutHorizontally { if (forward) -it else it })
                                    .using(SizeTransform(clip = true))
                            }, label = "day") { epochDay ->
                                val date = LocalDate.ofEpochDay(epochDay)
                                val model: DayEntryViewModel = viewModel(key = "day-$epochDay", factory = viewModelFactory {
                                    initializer { DayEntryViewModel(date, container.repository, container.clock) }
                                })
                                val state by model.state.collectAsStateWithLifecycle()
                                ResumeAndMidnightEffect(model::onResume)
                                val actions = DayEntryActions(
                                    ovulationTest = { model.setOvulationTest(it) }, flow = { model.setFlow(it) }, mood = { model.setMood(it) }, pain = { model.setPain(it) },
                                    sex = { model.setSex(it) }, discharge = { model.setDischarge(it) }, note = { model.setNote(it) },
                                    symptom = { model.toggleSymptom(it) }, tag = { model.toggleTag(it) },
                                    addTag = { model.addTag(it) }, renameTag = { id, name -> model.renameTag(id, name) },
                                    archiveTag = { model.archiveTag(it) }, startPeriod = { model.startPeriod(it) },
                                    removePeriodStart = { model.removePeriodStart() }, endPeriod = { model.setPeriodEnd(it) },
                                    clear = { model.clearDay() }, undo = { model.undoClear(it) },
                                    count = { id, times -> model.setCount(id, times) },
                                    level = { field, name -> model.setLevel(field, name) },
                                    addItem = { field, category, name, icon, categoryName -> model.addItem(field, category, name, icon, categoryName) },
                                )
                                val dismiss = { scope.launch { model.awaitWrites(); navigation.popBackStack() }; Unit }
                                SideEffect { dismissals[epochDay] = dismiss }
                                DayEntrySheet(state, actions, onDateChange = { selected ->
                                    scope.launch { model.awaitWrites(); shown = selected.toEpochDay() }
                                }, onDismiss = dismiss, events = model.events, onCustomize = {
                                    scope.launch { model.awaitWrites(); navigation.navigate("settings/day-entry") }
                                }, inSheet = false)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumeAndMidnightEffect(onResume: () -> Job) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val refresh by rememberUpdatedState(onResume)
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            refresh().join()
            while (true) {
                // Wall-clock time schedules the wake-up; each model calculates dates with
                // its injected clock, including future-day editability after midnight.
                val now = ZonedDateTime.now()
                val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                delay(Duration.between(now, midnight).toMillis().coerceAtLeast(1))
                refresh().join()
            }
        }
    }
}
