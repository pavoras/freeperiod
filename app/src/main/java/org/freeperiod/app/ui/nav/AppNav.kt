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
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.freeperiod.app.AppContainer
import org.freeperiod.app.R
import org.freeperiod.app.ui.today.TodayScreen
import org.freeperiod.app.ui.today.TodayViewModel

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
            if (onboardingDone) {
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
                    val lifecycle = LocalLifecycleOwner.current.lifecycle
                    LaunchedEffect(lifecycle, todayViewModel) {
                        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            todayViewModel.onResume().join()
                            while (true) {
                                // Wall-clock time schedules the wake-up; the injected date clock
                                // remains the sole source of Today domain calculations.
                                val now = ZonedDateTime.now()
                                val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                                delay(Duration.between(now, midnight).toMillis().coerceAtLeast(1))
                                todayViewModel.onResume().join()
                            }
                        }
                    }
                    TodayScreen(state, { todayViewModel.startPeriodToday() }, { todayViewModel.confirmEnd(it) },
                        { navigation.navigate("day/${it.toEpochDay()}") }, { todayViewModel.pausePredictions() },
                        { todayViewModel.showMonth(it) }, todayViewModel::dismissError)
                }
                composable("history") { TitlePlaceholder(R.string.nav_history) }
                composable("settings") { TitlePlaceholder(R.string.nav_settings) }
                composable("day/{epochDay}", arguments = listOf(navArgument("epochDay") { type = NavType.LongType })) { day ->
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(stringResource(R.string.day_entry), style = MaterialTheme.typography.headlineMedium)
                        val date = LocalDate.ofEpochDay(requireNotNull(day.arguments).getLong("epochDay"))
                        val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                        Text(date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(locale)))
                        TextButton(onClick = { navigation.popBackStack() }) { Text(stringResource(R.string.back)) }
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
