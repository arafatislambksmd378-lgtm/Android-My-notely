package com.notely.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.notely.app.NotelyApp
import com.notely.app.ui.editor.EditorScreen
import com.notely.app.ui.home.HomeScreen
import com.notely.app.ui.notebooks.NotebooksScreen
import com.notely.app.ui.settings.SettingsScreen
import com.notely.app.ui.theme.LocalNotelyColors

object Routes {
    const val HOME = "home"
    const val NOTEBOOKS = "notebooks"
    const val SETTINGS = "settings"
    const val EDITOR = "editor/{noteId}?mode={mode}"
    fun editor(noteId: Long, mode: String = "edit") = "editor/$noteId?mode=$mode"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun NotelyNavHost(
    app: NotelyApp,
    pendingNoteId: Long? = null,
    onPendingNoteConsumed: () -> Unit = {}
) {
    val nav = rememberNavController()
    val tabs = listOf(
        Tab(Routes.HOME, "Home", Icons.Filled.Home),
        Tab(Routes.NOTEBOOKS, "Notebooks", Icons.Outlined.Folder),
        Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
    )
    val c = LocalNotelyColors.current
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isEditor = currentRoute?.startsWith("editor/") == true

    // A reminder notification tap asks for a specific note — open it once.
    LaunchedEffect(pendingNoteId) {
        val id = pendingNoteId
        if (id != null && id > 0L) {
            nav.navigate(Routes.editor(id))
            onPendingNoteConsumed()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(navController = nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(app, onOpenNote = { nav.navigate(Routes.editor(it)) })
                }
                composable(Routes.NOTEBOOKS) {
                    NotebooksScreen(app, onOpenNote = { nav.navigate(Routes.editor(it)) })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(app)
                }
                composable(
                    Routes.EDITOR,
                    arguments = listOf(
                        navArgument("noteId") { type = NavType.LongType },
                        navArgument("mode") { type = NavType.StringType; defaultValue = "edit" }
                    )
                ) { entry ->
                    val noteId = entry.arguments?.getLong("noteId") ?: 0L
                    val mode = entry.arguments?.getString("mode") ?: "edit"
                    EditorScreen(
                        app = app,
                        noteId = noteId,
                        readOnly = mode == "view",
                        onBack = { nav.popBackStack() }
                    )
                }
            }
        }
        // The editor is a full-screen workspace: no bottom navigation while writing.
        if (!isEditor) {
            NavigationBar(containerColor = c.surface, tonalElevation = 8.dp) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (currentRoute != tab.route) {
                                nav.navigate(tab.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.primary,
                            selectedTextColor = c.primary,
                            indicatorColor = c.primaryContainer,
                            unselectedIconColor = c.text2,
                            unselectedTextColor = c.text2
                        )
                    )
                }
            }
        }
    }
}
