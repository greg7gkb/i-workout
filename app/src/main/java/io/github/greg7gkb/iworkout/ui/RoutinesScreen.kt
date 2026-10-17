package io.github.greg7gkb.iworkout.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.greg7gkb.iworkout.AppContainer
import io.github.greg7gkb.iworkout.data.local.RoutineEntity
import io.github.greg7gkb.iworkout.data.sync.SyncState
import io.github.greg7gkb.iworkout.data.sync.SyncWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(container: AppContainer, onOpen: (String) -> Unit, onResume: (String) -> Unit) {
    val context = LocalContext.current
    val routines by remember { container.plan.activeRoutines() }.collectAsStateWithLifecycle(initialValue = null)
    val active by remember { container.log.activeSession() }.collectAsStateWithLifecycle(initialValue = null)
    val sync by container.syncStatus.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Routines") },
                actions = { TextButton(onClick = { SyncWorker.syncNow(context) }) { Text("Sync") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SyncLine(sync)
            active?.let { session ->
                val name = routines?.firstOrNull { it.id == session.routineId }?.name ?: "workout"
                Card(
                    onClick = { onResume(session.uuid) },
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Text("Resume $name →", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            val list = routines
            when {
                list == null -> Unit
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No routines yet. Tap Sync.", style = MaterialTheme.typography.bodyLarge)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { RoutineCard(it, onClick = { onOpen(it.id) }) }
                }
            }
        }
    }
}

@Composable
private fun SyncLine(sync: SyncState) {
    val text = when {
        sync.lastError != null -> "Sync failed: ${sync.lastError}"
        sync.lastSyncedAt != null -> "Synced " + DateUtils.getRelativeTimeSpanString(sync.lastSyncedAt)
        else -> "Not synced yet"
    }
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.labelMedium,
        color = if (sync.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RoutineCard(routine: RoutineEntity, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(routine.name, style = MaterialTheme.typography.titleLarge)
            if (routine.notes.isNotBlank()) {
                Text(
                    routine.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
