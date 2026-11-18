package app.childrensimulator.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.childrensimulator.AppContainer
import app.childrensimulator.sim.engine.PracticeDrill

/** Shared app-level state for screens. */
class AppState(val container: AppContainer, val nav: NavHostController, val snackbar: SnackbarHostState) {
    val repo get() = container.repository
    val prefs get() = container.prefs
    val content get() = container.content
}

@Composable
fun AppRoot(container: AppContainer) {
    val ui by container.repository.ui.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val state = remember { AppState(container, nav, snackbar) }
    val systemDark = isSystemInDarkTheme()
    val night = systemDark || (ui.view?.let { it.nightNow && !it.ended && !it.paused } ?: false)
    var reducedMotion by remember { mutableStateOf(container.prefs.reducedMotion) }

    LaunchedEffect(Unit) {
        container.repository.messages.collect { snackbar.showSnackbar(it) }
    }

    ChildSimTheme(night = night) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            if (!ui.loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Surface
            }
            val start = if (ui.hasRun) "main" else "onboarding"
            NavHost(nav, startDestination = start) {
                composable("onboarding") {
                    OnboardingScreen(state) {
                        nav.navigate("main") { popUpTo("onboarding") { inclusive = true } }
                    }
                }
                composable("main") { MainTabs(state, ui, reducedMotion) }
                composable("settings") {
                    SettingsScreen(state, ui, onReducedMotion = { reducedMotion = it }) {
                        nav.popBackStack()
                    }
                }
                composable("lesson/{id}") { entry -> LessonScreen(state, entry.arguments?.getString("id") ?: "") { nav.popBackStack() } }
                composable("quiz/{set}") { entry -> QuizScreen(state, ui, post = entry.arguments?.getString("set") == "post") { nav.popBackStack() } }
                composable("practice/{drill}") { entry ->
                    val drill = PracticeDrill.entries.firstOrNull { it.id == entry.arguments?.getString("drill") } ?: PracticeDrill.ONE_WAKE
                    PracticeScreen(state, ui, drill, reducedMotion) { nav.popBackStack() }
                }
            }
        }
    }
}

@Composable
private fun MainTabs(state: AppState, ui: app.childrensimulator.data.RunUi, reducedMotion: Boolean) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (!ui.hasRun) {
        LaunchedEffect(Unit) { state.nav.navigate("onboarding") { popUpTo("main") { inclusive = true } } }
        return
    }
    Scaffold(
        snackbarHost = { SnackbarHost(state.snackbar) },
        bottomBar = {
            NavigationBar {
                val items = listOf("Baby" to Icons.Filled.Face, "Log" to Icons.AutoMirrored.Filled.List, "Learn" to Icons.Filled.Info, "Report" to Icons.Filled.DateRange)
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, contentDescription = null) }, label = { Text(label) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> HomeScreen(state, ui, reducedMotion)
                1 -> LogScreen(state, ui)
                2 -> LearnScreen(state, ui)
                3 -> ReportScreen(state, ui)
            }
        }
    }
}
