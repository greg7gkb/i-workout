package io.github.greg7gkb.iworkout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.greg7gkb.iworkout.AppContainer
import io.github.greg7gkb.iworkout.data.local.ExerciseEntity
import io.github.greg7gkb.iworkout.data.local.ExerciseType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineScreen(container: AppContainer, routineId: String, onBack: () -> Unit, onOpenSession: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val routine by remember(routineId) { container.plan.routine(routineId) }.collectAsStateWithLifecycle(initialValue = null)
    val exercises by remember(routineId) { container.plan.exercisesFor(routineId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val active by remember { container.log.activeSession() }.collectAsStateWithLifecycle(initialValue = null)
    val resumable = active?.takeIf { it.routineId == routineId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(routine?.name.orEmpty()) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    if (resumable != null) onOpenSession(resumable.uuid)
                    else scope.launch { onOpenSession(container.log.start(routineId)) }
                },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            ) { Text(if (resumable != null) "Resume workout" else "Start workout") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            routine?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                item {
                    Text(notes, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(exercises, key = { it.id }) { ExerciseCard(it) }
        }
    }
}

@Composable
private fun ExerciseCard(exercise: ExerciseEntity) {
    val bookend = exercise.type == ExerciseType.WARM_UP || exercise.type == ExerciseType.COOL_DOWN
    Card(
        Modifier.fillMaxWidth(),
        colors = if (bookend) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(24.dp)) {
            Text(
                "${exercise.position}",
                modifier = Modifier.width(40.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.titleLarge)
                targetLine(exercise)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (exercise.instructions.isNotBlank()) {
                    Text(
                        exercise.instructions,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// "2 × 10 · 20–25 lb", "2 × 6 each leg · no load", or null for warm-up/cool-down.
internal fun targetLine(e: ExerciseEntity): String? {
    val volume = when {
        e.sets != null && e.reps != null -> "${e.sets} × ${e.reps}"
        e.sets != null -> "${e.sets} sets"
        else -> e.reps
    }
    return listOfNotNull(volume, e.weight).joinToString(" · ").ifBlank { null }
}
