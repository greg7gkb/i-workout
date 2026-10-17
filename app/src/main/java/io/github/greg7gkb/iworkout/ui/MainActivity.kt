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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.greg7gkb.iworkout.IWorkoutApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as IWorkoutApp).container
        setContent {
            IWorkoutTheme {
                // Three screens don't need a navigation library.
                var routineId by rememberSaveable { mutableStateOf<String?>(null) }
                var sessionUuid by rememberSaveable { mutableStateOf<String?>(null) }
                val openRoutine = routineId
                val openSession = sessionUuid
                when {
                    openSession != null -> {
                        BackHandler { sessionUuid = null }
                        SessionScreen(container, openSession, onClose = { sessionUuid = null; routineId = null })
                    }
                    openRoutine != null -> {
                        BackHandler { routineId = null }
                        RoutineScreen(
                            container,
                            openRoutine,
                            onBack = { routineId = null },
                            onOpenSession = { sessionUuid = it },
                        )
                    }
                    else -> RoutinesScreen(container, onOpen = { routineId = it }, onResume = { sessionUuid = it })
                }
            }
        }
    }
}

@Composable
fun IWorkoutTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}
