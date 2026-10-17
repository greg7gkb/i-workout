package io.github.greg7gkb.iworkout.ui

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.greg7gkb.iworkout.AppContainer
import io.github.greg7gkb.iworkout.data.local.ExerciseEntity
import io.github.greg7gkb.iworkout.data.local.ExerciseType
import io.github.greg7gkb.iworkout.data.local.SetEntity
import io.github.greg7gkb.iworkout.data.sync.SyncWorker
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    container: AppContainer,
    sessionUuid: String,
    onClose: () -> Unit,
    onOpenExercise: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val log = container.log
    val context = LocalContext.current
    val session by remember(sessionUuid) { log.session(sessionUuid) }.collectAsStateWithLifecycle(initialValue = null)
    val routineId = session?.routineId
    val routine by remember(routineId) { container.plan.routine(routineId.orEmpty()) }.collectAsStateWithLifecycle(initialValue = null)
    val exercises by remember(routineId) { routineId?.let { container.plan.exercisesFor(it) } ?: flowOf(null) }
        .collectAsStateWithLifecycle(initialValue = null)
    val sets by remember(sessionUuid) { log.setsFor(sessionUuid) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val setsByPosition = sets.groupBy { it.exercisePosition }

    var confirmDiscard by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SetEntity?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    // A finished Session opened from History is in edit mode: no Finish button.
    val finished = session?.endedAt != null
    // Edits to a finished Session make it dirty again; leaving kicks off the re-upload.
    val close = {
        if (finished) SyncWorker.syncNow(context)
        onClose()
    }

    KeepScreenOn()
    BackHandler(onBack = close)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(routine?.name ?: "Workout") },
                navigationIcon = { TextButton(onClick = close) { Text("Back") } },
                actions = {
                    if (session != null && !finished) {
                        TextButton(onClick = { scope.launch { log.finish(sessionUuid); SyncWorker.syncNow(context); onClose() } }) { Text("Finish") }
                    }
                },
            )
        },
    ) { padding ->
        // Wait for the plan to load; otherwise the list anchors on Notes and opens scrolled to the bottom.
        val loadedExercises = exercises ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            session?.takeIf { finished }?.let { s ->
                item(key = "date") { DateRow(s.startedAt, s.endedAt, onChange = { pickingDate = true }) }
            }
            items(loadedExercises, key = { it.id }) { exercise ->
                val logged = setsByPosition[exercise.position].orEmpty()
                if (exercise.isBookend) {
                    BookendCard(exercise, done = logged.isNotEmpty()) { done ->
                        scope.launch { log.setDone(sessionUuid, exercise, done) }
                    }
                } else {
                    val lastTime by remember(exercise.name, sessionUuid) { log.lastTime(exercise.name, sessionUuid) }
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    ExerciseLogCard(
                        exercise = exercise,
                        logged = logged,
                        lastTime = lastTime,
                        onLog = { reps, weight ->
                            scope.launch { log.logSet(sessionUuid, exercise, reps, weight) }
                        },
                        onRemove = { set -> scope.launch { log.removeSet(set) } },
                        onEdit = { set -> editing = set },
                        onOpenHistory = { onOpenExercise(exercise.name) },
                    )
                }
            }
            session?.let { s ->
                item(key = "notes") {
                    NotesField(s.uuid, initial = s.notes) { notes -> scope.launch { log.setNotes(sessionUuid, notes) } }
                }
            }
            item {
                TextButton(onClick = { confirmDiscard = true }) {
                    Text(if (finished) "Delete workout" else "Discard workout", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(if (finished) "Delete this workout?" else "Discard this workout?") },
            text = { Text("Logged sets will be removed from history.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { log.discard(sessionUuid); SyncWorker.syncNow(context); onClose() } }) {
                    Text(if (finished) "Delete" else "Discard")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } },
        )
    }

    editing?.let { set ->
        val type = exercises?.firstOrNull { it.position == set.exercisePosition }?.type
        EditSetDialog(
            set = set,
            usesWeight = type == ExerciseType.EQUIPMENT || set.weight != null,
            onDismiss = { editing = null },
            onSave = { reps, weight ->
                scope.launch { log.editSet(set, reps, weight) }
                editing = null
            },
        )
    }

    val current = session
    if (pickingDate && current != null) {
        StartTimePicker(
            initial = current.startedAt,
            onDismiss = { pickingDate = false },
            onPick = { newStart ->
                scope.launch { log.moveStart(current, newStart) }
                pickingDate = false
            },
        )
    }
}

private val ExerciseEntity.isBookend get() = type == ExerciseType.WARM_UP || type == ExerciseType.COOL_DOWN

@Composable
private fun BookendCard(exercise: ExerciseEntity, done: Boolean, onDone: (Boolean) -> Unit) {
    Card(
        onClick = { onDone(!done) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = done, onCheckedChange = onDone)
            Column(Modifier.padding(top = 12.dp)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                if (exercise.instructions.isNotBlank()) {
                    Text(exercise.instructions, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ExerciseLogCard(
    exercise: ExerciseEntity,
    logged: List<SetEntity>,
    lastTime: List<SetEntity>,
    onLog: (reps: Int?, weight: Double?) -> Unit,
    onRemove: (SetEntity) -> Unit,
    onEdit: (SetEntity) -> Unit,
    onOpenHistory: () -> Unit,
) {
    val usesWeight = exercise.type == ExerciseType.EQUIPMENT
    val targetSets = exercise.sets ?: 0
    val complete = targetSets > 0 && logged.size >= targetSets

    // Prefill: previous set this session, else the same set last time, else the target.
    val nextIndex = logged.size
    val prev = logged.lastOrNull()
    val ref = lastTime.getOrNull(nextIndex) ?: lastTime.lastOrNull()
    val prefillReps = prev?.reps ?: ref?.reps ?: leadingInt(exercise.reps)
    val prefillWeight = if (usesWeight) prev?.weight ?: ref?.weight ?: leadingNumber(exercise.weight) else null

    var reps by remember(exercise.id, logged.size) { mutableStateOf(prefillReps?.toString().orEmpty()) }
    var weight by remember(exercise.id, logged.size) { mutableStateOf(prefillWeight?.let(::formatWeight).orEmpty()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenHistory) { Text("History") }
                if (targetSets > 0) {
                    Text(
                        "${logged.size}/$targetSets",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            targetLine(exercise)?.let { Text("Target: $it", style = MaterialTheme.typography.bodyMedium) }
            Text(
                "Last time: " + (lastTime.takeIf { it.isNotEmpty() }?.joinToString(", ") { describe(it) } ?: "—"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (exercise.instructions.isNotBlank()) {
                Text(exercise.instructions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            logged.forEach { set ->
                // Tap a logged set to fix its numbers.
                Row(Modifier.clickable { onEdit(set) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("Set ${set.setNumber}", modifier = Modifier.width(56.dp), style = MaterialTheme.typography.bodyLarge)
                    Text(describe(set), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { onRemove(set) }) { Text("Remove") }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it.filter(Char::isDigit).take(3) },
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(88.dp),
                )
                if (usesWeight) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { v -> weight = v.filter { it.isDigit() || it == '.' }.take(5) },
                        label = { Text("lb") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(96.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                val log = { onLog(reps.toIntOrNull(), if (usesWeight) weight.toDoubleOrNull() else null) }
                if (complete) FilledTonalButton(onClick = log) { Text("+ Set") } else Button(onClick = log) { Text("Log set") }
            }
        }
    }
}

@Composable
private fun NotesField(sessionUuid: String, initial: String, onSave: (String) -> Unit) {
    // Seeded once per session; afterwards the field is the source of truth and every edit is saved.
    var text by remember(sessionUuid) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onSave(it) },
        label = { Text("Notes") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

internal fun describe(set: SetEntity): String = when {
    set.reps != null && set.weight != null -> "${set.reps} @ ${formatWeight(set.weight)}"
    set.reps != null -> "${set.reps}"
    set.weight != null -> "@ ${formatWeight(set.weight)}"
    else -> "✓"
}

internal fun formatWeight(w: Double): String = if (w % 1.0 == 0.0) w.toLong().toString() else w.toString()

private fun leadingInt(s: String?): Int? = s?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }

private fun leadingNumber(s: String?): Double? = s?.let { Regex("""\d+(\.\d+)?""").find(it)?.value?.toDoubleOrNull() }

@Composable
private fun DateRow(startedAt: Long, endedAt: Long?, onChange: () -> Unit) {
    val minutes = endedAt?.let { (it - startedAt) / 60_000 }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatDateTime(startedAt) + (minutes?.let { " · $it min" } ?: ""),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onChange) { Text("Change date") }
    }
}

@Composable
private fun EditSetDialog(set: SetEntity, usesWeight: Boolean, onDismiss: () -> Unit, onSave: (Int?, Double?) -> Unit) {
    // Reps starts focused and fully selected, so typing replaces the old value.
    var reps by remember(set.id) {
        val text = set.reps?.toString().orEmpty()
        mutableStateOf(TextFieldValue(text, TextRange(0, text.length)))
    }
    var weight by remember(set.id) { mutableStateOf(set.weight?.let(::formatWeight).orEmpty()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${set.exerciseName} · set ${set.setNumber}") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = reps,
                    onValueChange = { v -> reps = v.copy(text = v.text.filter(Char::isDigit).take(3)) },
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp).focusRequester(focus),
                )
                if (usesWeight) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { v -> weight = v.filter { it.isDigit() || it == '.' }.take(5) },
                        label = { Text("lb") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(96.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(reps.text.toIntOrNull(), if (usesWeight) weight.toDoubleOrNull() else null) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// Date, then time of day. Returns the new start as epoch millis in the phone's time zone.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTimePicker(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(initial).atZone(zone)
    // DatePicker works in UTC midnights.
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    val timeState = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute)
    var pickingTime by remember { mutableStateOf(false) }

    if (!pickingTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { pickingTime = true }) { Text("Next") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) { DatePicker(state = dateState) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Start time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val date = Instant.ofEpochMilli(dateState.selectedDateMillis ?: initial).atZone(ZoneOffset.UTC).toLocalDate()
                    onPick(date.atTime(LocalTime.of(timeState.hour, timeState.minute)).atZone(zone).toInstant().toEpochMilli())
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

internal fun formatDateTime(millis: Long): String =
    DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
