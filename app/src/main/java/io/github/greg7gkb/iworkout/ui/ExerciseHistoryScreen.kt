package io.github.greg7gkb.iworkout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.greg7gkb.iworkout.AppContainer

// Every logged set of one exercise (matched by name, across Routines), grouped by workout, newest first.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseHistoryScreen(container: AppContainer, exerciseName: String, onBack: () -> Unit) {
    val rows by remember(exerciseName) { container.log.exerciseHistory(exerciseName) }
        .collectAsStateWithLifecycle(initialValue = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exerciseName) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        val list = rows ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nothing logged yet.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        val bySession = list.groupBy { it.set.sessionUuid }.values.toList()
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(bySession, key = { it.first().set.sessionUuid }) { sets ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(formatDateTime(sets.first().startedAt), style = MaterialTheme.typography.titleMedium)
                        Text(sets.joinToString(", ") { describe(it.set) }, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
