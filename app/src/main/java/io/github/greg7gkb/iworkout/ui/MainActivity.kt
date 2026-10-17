package io.github.greg7gkb.iworkout.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.github.greg7gkb.iworkout.IWorkoutApp

// A tiny back stack of routes ("kind:arg"); a handful of screens doesn't need a navigation library.
private const val ROUTINE = "routine:"
private const val SESSION = "session:"
private const val EXERCISE = "exercise:"
private const val HISTORY = "history"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as IWorkoutApp).container
        setContent {
            val chosen by container.theme.dark.collectAsStateWithLifecycle()
            val dark = chosen ?: isSystemInDarkTheme()
            // Status/nav bar icons must follow the app's theme, not the system's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            IWorkoutTheme(dark) {
                val stack = rememberSaveable(saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) {
                    mutableStateListOf<String>()
                }
                val pop: () -> Unit = { stack.removeLastOrNull() }
                BackHandler(enabled = stack.isNotEmpty(), onBack = pop)

                val route = stack.lastOrNull()
                when {
                    route == null -> RoutinesScreen(
                        container,
                        onOpen = { stack += ROUTINE + it },
                        onResume = { stack += SESSION + it },
                        onHistory = { stack += HISTORY },
                        dark = dark,
                        onToggleTheme = { container.theme.setDark(!dark) },
                    )
                    route.startsWith(ROUTINE) -> RoutineScreen(
                        container,
                        route.removePrefix(ROUTINE),
                        onBack = pop,
                        // Replace the Routine so leaving the workout returns home.
                        onOpenSession = { stack[stack.lastIndex] = SESSION + it },
                    )
                    route.startsWith(SESSION) -> SessionScreen(
                        container,
                        route.removePrefix(SESSION),
                        onClose = pop,
                        onOpenExercise = { stack += EXERCISE + it },
                    )
                    route.startsWith(EXERCISE) -> ExerciseHistoryScreen(container, route.removePrefix(EXERCISE), onBack = pop)
                    route == HISTORY -> HistoryScreen(container, onOpenSession = { stack += SESSION + it }, onBack = pop)
                }
            }
        }
    }
}

internal fun List<String>.toMutableStateList(): SnapshotStateList<String> = mutableStateListOf<String>().also { it.addAll(this) }
