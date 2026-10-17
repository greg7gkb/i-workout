package io.github.greg7gkb.iworkout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.greg7gkb.iworkout.AppContainer
import io.github.greg7gkb.iworkout.data.local.ExerciseEntity
import io.github.greg7gkb.iworkout.data.local.ExerciseType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineScreen(container: AppContainer, routineId: String, onBack: () -> Unit) {
    val routine by remember(routineId) { container.plan.routine(routineId) }.collectAsStateWithLifecycle(initialValue = null)
    val exercises by remember(routineId) { container.plan.exercisesFor(routineId) }.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(routine?.name.orEmpty()) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            routine?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                item {
                    Text(notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Row(Modifier.padding(16.dp)) {
            Text(
                "${exercise.position}",
                modifier = Modifier.width(28.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                targetLine(exercise)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (exercise.instructions.isNotBlank()) {
                    Text(
                        exercise.instructions,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// "2 × 10 · 20–25 lb", "2 × 6 each leg · no load", or null for warm-up/cool-down.
private fun targetLine(e: ExerciseEntity): String? {
    val volume = when {
        e.sets != null && e.reps != null -> "${e.sets} × ${e.reps}"
        e.sets != null -> "${e.sets} sets"
        else -> e.reps
    }
    return listOfNotNull(volume, e.weight).joinToString(" · ").ifBlank { null }
}
