package org.freeperiod.app.ui.nav

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import org.freeperiod.app.ui.today.TodayScreen
import org.freeperiod.app.ui.today.TodayViewModel
import org.freeperiod.app.ui.history.HistoryScreen
import org.freeperiod.app.ui.history.HistoryViewModel
import org.freeperiod.app.ui.day.DayEntryActions
import org.freeperiod.app.ui.day.DayEntrySheet
import org.freeperiod.app.ui.day.DayEntryViewModel

@Composable
fun AppNav(container: AppContainer, todayViewModel: TodayViewModel, onboardingDone: Boolean) {
    val scope = rememberCoroutineScope()
    val destinations = listOf("today" to R.string.nav_today, "history" to R.string.nav_history,
        "settings" to R.string.nav_settings)

    // Recreate the graph when onboarding completes, removing onboarding from back navigation.
    key(onboardingDone) {
        val navigation = rememberNavController()
        val entry by navigation.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        Scaffold(bottomBar = {
            if (onboardingDone && route != "day/{epochDay}") {
                NavigationBar {
                    destinations.forEach { (destination, label) ->
                        NavigationBarItem(selected = route == destination,
                            onClick = {
                                navigation.navigate(destination) {
                                    popUpTo(navigation.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }, icon = {
                                Icon(when (destination) {
                                    "today" -> NavigationIcons.Today
                                    "history" -> NavigationIcons.History
                                    else -> NavigationIcons.Settings
                                }, contentDescription = null)
                            }, label = { Text(stringResource(label)) })
                    }
                }
            }
        }) { padding ->
            NavHost(navigation, startDestination = if (onboardingDone) "today" else "onboarding",
                modifier = Modifier.padding(padding)) {
                composable("onboarding") {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                        Button(onClick = { scope.launch { container.settings.update { it.copy(onboardingDone = true) } } }) {
                            Text(stringResource(R.string.onboarding_continue))
                        }
                    }
                }
                composable("today") {
                    val state by todayViewModel.state.collectAsStateWithLifecycle()
                    ResumeAndMidnightEffect(todayViewModel::onResume)
                    TodayScreen(state, { todayViewModel.startPeriodToday() }, { todayViewModel.confirmEnd(it) },
                        { navigation.navigate("day/${it.toEpochDay()}") }, { todayViewModel.pausePredictions() },
                        { todayViewModel.showMonth(it) }, todayViewModel::dismissError)
                }
                composable("history") {
                    val model: HistoryViewModel = viewModel(factory = viewModelFactory {
                        initializer { HistoryViewModel(container.repository, container.clock) }
                    })
                    val state by model.state.collectAsStateWithLifecycle()
                    ResumeAndMidnightEffect(model::onResume)
                    HistoryScreen(state, onInclude = { id, included -> model.setCycleIncluded(id, included) })
                }
                composable("settings") { TitlePlaceholder(R.string.nav_settings) }
                composable("day/{epochDay}", arguments = listOf(navArgument("epochDay") { type = NavType.LongType })) { day ->
                    val date = LocalDate.ofEpochDay(requireNotNull(day.arguments).getLong("epochDay"))
                    val model: DayEntryViewModel = viewModel(factory = viewModelFactory {
                        initializer { DayEntryViewModel(date, container.repository, container.clock) }
                    })
                    val state by model.state.collectAsStateWithLifecycle()
                    ResumeAndMidnightEffect(model::onResume)
                    val actions = DayEntryActions(
                        flow = { model.setFlow(it) }, mood = { model.setMood(it) }, pain = { model.setPain(it) },
                        sex = { model.setSex(it) }, discharge = { model.setDischarge(it) }, note = { model.setNote(it) },
                        symptom = { model.toggleSymptom(it) }, tag = { model.toggleTag(it) },
                        addTag = { model.addTag(it) }, renameTag = { id, name -> model.renameTag(id, name) },
                        archiveTag = { model.archiveTag(it) }, startPeriod = { model.startPeriod(it) },
                        removePeriodStart = { model.removePeriodStart() }, endPeriod = { model.setPeriodEnd(it) },
                        clear = { model.clearDay() }, undo = { model.undoClear(it) },
                    )
                    Surface(Modifier.fillMaxSize()) {
                        DayEntrySheet(state, actions, onDateChange = { selected ->
                            scope.launch {
                                model.awaitWrites()
                                navigation.navigate("day/${selected.toEpochDay()}") {
                                    popUpTo(day.destination.id) { inclusive = true }
                                }
                            }
                        }, onDismiss = {
                            scope.launch { model.awaitWrites(); navigation.popBackStack() }
                        }, events = model.events)
                    }
                }
            }
        }
    }
}

@Composable
private fun TitlePlaceholder(title: Int) {
    Text(stringResource(title), Modifier.padding(24.dp), style = MaterialTheme.typography.headlineMedium)
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
