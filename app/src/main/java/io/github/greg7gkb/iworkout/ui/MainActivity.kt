package io.github.greg7gkb.iworkout.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import io.github.greg7gkb.iworkout.IWorkoutApp

// A tiny back stack of routes ("kind:arg"); a handful of screens doesn't need a navigation library.
private const val ROUTINE = "routine:"
private const val SESSION = "session:"
private const val EXERCISE = "exercise:"
private const val HISTORY = "history"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as IWorkoutApp).container
        setContent {
            IWorkoutTheme {
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

private fun List<String>.toMutableStateList(): SnapshotStateList<String> = mutableStateListOf<String>().also { it.addAll(this) }

@Composable
fun IWorkoutTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}
