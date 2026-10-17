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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.greg7gkb.iworkout.AppContainer
import io.github.greg7gkb.iworkout.data.sync.SyncAttempt
import io.github.greg7gkb.iworkout.data.sync.SyncStatus

// The last few sync attempts, newest first: when, whether it worked, and what moved.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncHistoryScreen(container: AppContainer, onBack: () -> Unit) {
    val history by container.syncStatus.history.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync history") },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No syncs yet.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(history) { AttemptCard(it) }
            item {
                Text(
                    "Keeps the last ${SyncStatus.MAX_HISTORY} attempts.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AttemptCard(attempt: SyncAttempt) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                (if (attempt.ok) "✓ " else "✗ ") + formatDateTime(attempt.at),
                style = MaterialTheme.typography.titleMedium,
                color = if (attempt.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            describeAttempt(attempt)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            attempt.error?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// "Uploaded 1 workout · 2 routines, 13 exercises", or just the upload count if the pull didn't happen.
internal fun describeAttempt(a: SyncAttempt): String? {
    val uploaded = a.pushed?.let { "Uploaded $it workout${if (it == 1) "" else "s"}" }
    val pulled = if (a.routines != null && a.exercises != null) "${a.routines} routines, ${a.exercises} exercises" else null
    return listOfNotNull(uploaded, pulled).joinToString(" · ").ifEmpty { null }
}
