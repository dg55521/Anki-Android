package com.example.anki

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.anki.data.preferences.UserPreferences
import com.example.anki.navigation.BrowseRoute
import com.example.anki.navigation.CardInfoRoute
import com.example.anki.navigation.DecksRoute
import com.example.anki.navigation.MoreRoute
import com.example.anki.navigation.NoteEditorRoute
import com.example.anki.navigation.ReviewerRoute
import com.example.anki.navigation.StatsRoute
import com.example.anki.navigation.StudyOptionsRoute
import com.example.anki.ui.screens.browser.BrowserScreen
import com.example.anki.ui.screens.browser.BrowserViewModel
import com.example.anki.ui.screens.browser.CardInfoScreen
import com.example.anki.ui.screens.decks.DecksScreen
import com.example.anki.ui.screens.decks.DecksViewModel
import com.example.anki.ui.screens.editor.NoteEditorScreen
import com.example.anki.ui.screens.more.MoreScreen
import com.example.anki.ui.screens.reviewer.ReviewerScreen
import com.example.anki.ui.screens.reviewer.ReviewerViewModel
import com.example.anki.ui.screens.stats.StatsScreen
import com.example.anki.ui.screens.study.StudyOptionsScreen
import com.example.anki.ui.theme.AnkiDroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AnkiApplication

        setContent {
            val prefs by app.preferencesRepository.userPreferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())

            AnkiDroidTheme(themeMode = prefs.themeMode) {
                AnkiAppRoot(app = app)
            }
        }
    }
}

private enum class TopLevelTab(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    DECKS(DecksRoute, "Decks", Icons.Filled.Layers, Icons.Outlined.Layers, "nav_decks"),
    BROWSE(BrowseRoute, "Browse", Icons.Filled.ViewList, Icons.Outlined.ViewList, "nav_browse"),
    STATS(StatsRoute, "Stats", Icons.Filled.BarChart, Icons.Outlined.BarChart, "nav_stats"),
    MORE(MoreRoute, "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz, "nav_more"),
}

@Composable
private fun AnkiAppRoot(app: AnkiApplication) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val isTopLevel = currentDest == null ||
        currentDest.hasRoute<DecksRoute>() ||
        currentDest.hasRoute<BrowseRoute>() ||
        currentDest.hasRoute<StatsRoute>() ||
        currentDest.hasRoute<MoreRoute>()

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (isTopLevel && !useNavRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        TopLevelTab.entries.forEach { tab ->
                            val selected = when (tab) {
                                TopLevelTab.DECKS -> currentDest?.hasRoute<DecksRoute>() != false
                                TopLevelTab.BROWSE -> currentDest?.hasRoute<BrowseRoute>() == true
                                TopLevelTab.STATS -> currentDest?.hasRoute<StatsRoute>() == true
                                TopLevelTab.MORE -> currentDest?.hasRoute<MoreRoute>() == true
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (isTopLevel && useNavRail) {
                    NavigationRail {
                        TopLevelTab.entries.forEach { tab ->
                            val selected = when (tab) {
                                TopLevelTab.DECKS -> currentDest?.hasRoute<DecksRoute>() != false
                                TopLevelTab.BROWSE -> currentDest?.hasRoute<BrowseRoute>() == true
                                TopLevelTab.STATS -> currentDest?.hasRoute<StatsRoute>() == true
                                TopLevelTab.MORE -> currentDest?.hasRoute<MoreRoute>() == true
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag),
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = DecksRoute,
                    modifier = Modifier.weight(1f),
                ) {
                    composable<DecksRoute> {
                        val vm: DecksViewModel = viewModel(
                            factory = DecksViewModel.Factory(app.repository, app.preferencesRepository),
                        )
                        DecksScreen(
                            viewModel = vm,
                            onStudyDeck = { deckId -> navController.navigate(ReviewerRoute(deckId)) },
                            onOpenDeckOptions = { deckId -> navController.navigate(StudyOptionsRoute(deckId)) },
                            onAddNote = { deckId -> navController.navigate(NoteEditorRoute(noteId = null, initialDeckId = deckId)) },
                        )
                    }

                    composable<BrowseRoute> {
                        val vm: BrowserViewModel = viewModel(
                            factory = BrowserViewModel.Factory(app.repository),
                        )
                        BrowserScreen(
                            viewModel = vm,
                            onEditNote = { noteId -> navController.navigate(NoteEditorRoute(noteId = noteId)) },
                            onOpenCardInfo = { cardId -> navController.navigate(CardInfoRoute(cardId)) },
                            onAddNote = { deckId -> navController.navigate(NoteEditorRoute(noteId = null, initialDeckId = deckId)) },
                        )
                    }

                    composable<StatsRoute> {
                        StatsScreen(repository = app.repository)
                    }

                    composable<MoreRoute> {
                        MoreScreen(
                            repository = app.repository,
                            preferencesRepository = app.preferencesRepository,
                        )
                    }

                    composable<StudyOptionsRoute> { entry ->
                        val args = entry.toRoute<StudyOptionsRoute>()
                        StudyOptionsScreen(
                            deckId = args.deckId,
                            repository = app.repository,
                            onBack = { navController.popBackStack() },
                            onStartStudy = { deckId -> navController.navigate(ReviewerRoute(deckId)) },
                        )
                    }

                    composable<ReviewerRoute> { entry ->
                        val args = entry.toRoute<ReviewerRoute>()
                        val vm: ReviewerViewModel = viewModel(
                            factory = ReviewerViewModel.Factory(
                                deckId = args.deckId,
                                repository = app.repository,
                                preferencesRepository = app.preferencesRepository,
                            ),
                        )
                        ReviewerScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onEditNote = { noteId -> navController.navigate(NoteEditorRoute(noteId = noteId)) },
                            onOpenCardInfo = { cardId -> navController.navigate(CardInfoRoute(cardId)) },
                            onOpenStudyOptions = { navController.navigate(StudyOptionsRoute(args.deckId)) },
                        )
                    }

                    composable<NoteEditorRoute> { entry ->
                        val args = entry.toRoute<NoteEditorRoute>()
                        NoteEditorScreen(
                            noteId = args.noteId,
                            initialDeckId = args.initialDeckId,
                            repository = app.repository,
                            onBack = { navController.popBackStack() },
                        )
                    }

                    composable<CardInfoRoute> { entry ->
                        val args = entry.toRoute<CardInfoRoute>()
                        CardInfoScreen(
                            cardId = args.cardId,
                            repository = app.repository,
                            onBack = { navController.popBackStack() },
                            onEditNote = { noteId -> navController.navigate(NoteEditorRoute(noteId = noteId)) },
                        )
                    }
                }
            }
        }
    }
}
