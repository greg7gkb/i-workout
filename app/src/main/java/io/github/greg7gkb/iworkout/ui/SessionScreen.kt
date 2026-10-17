package io.github.greg7gkb.iworkout.ui

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val REST_SECONDS = 90

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(container: AppContainer, sessionUuid: String, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val log = container.log
    val session by remember(sessionUuid) { log.session(sessionUuid) }.collectAsStateWithLifecycle(initialValue = null)
    val routineId = session?.routineId
    val routine by remember(routineId) { container.plan.routine(routineId.orEmpty()) }.collectAsStateWithLifecycle(initialValue = null)
    val exercises by remember(routineId) { routineId?.let { container.plan.exercisesFor(it) } ?: flowOf(null) }
        .collectAsStateWithLifecycle(initialValue = null)
    val sets by remember(sessionUuid) { log.setsFor(sessionUuid) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val setsByPosition = sets.groupBy { it.exercisePosition }

    // Rest timer as an absolute end time, so it stays right across recomposition and backgrounding.
    var restEndsAt by rememberSaveable { mutableLongStateOf(0L) }
    var confirmDiscard by remember { mutableStateOf(false) }

    KeepScreenOn()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(routine?.name ?: "Workout") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Back") } },
                actions = {
                    TextButton(onClick = { scope.launch { log.finish(sessionUuid); onClose() } }) { Text("Finish") }
                },
            )
        },
        bottomBar = {
            RestTimerBar(
                endsAt = restEndsAt,
                onAdd = { restEndsAt += 30_000 },
                onSkip = { restEndsAt = 0L },
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
                            restEndsAt = System.currentTimeMillis() + REST_SECONDS * 1000L
                        },
                        onRemove = { set -> scope.launch { log.removeSet(set) } },
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
                    Text("Discard workout", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this workout?") },
            text = { Text("Logged sets will be removed from history.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { log.discard(sessionUuid); onClose() } }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } },
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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
private fun RestTimerBar(endsAt: Long, onAdd: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        if (endsAt == 0L) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (now >= endsAt) {
                buzz(context)
                onSkip()
                break
            }
            delay(250)
        }
    }
    val remaining = ((endsAt - now) / 1000).coerceAtLeast(0)
    if (endsAt == 0L || remaining <= 0) return

    Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Rest %d:%02d".format(remaining / 60, remaining % 60),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAdd) { Text("+30s") }
            TextButton(onClick = onSkip) { Text("Skip") }
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

private fun buzz(context: Context) {
    val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
}

private fun describe(set: SetEntity): String = when {
    set.reps != null && set.weight != null -> "${set.reps} @ ${formatWeight(set.weight)}"
    set.reps != null -> "${set.reps}"
    set.weight != null -> "@ ${formatWeight(set.weight)}"
    else -> "✓"
}

private fun formatWeight(w: Double): String = if (w % 1.0 == 0.0) w.toLong().toString() else w.toString()

private fun leadingInt(s: String?): Int? = s?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }

private fun leadingNumber(s: String?): Double? = s?.let { Regex("""\d+(\.\d+)?""").find(it)?.value?.toDoubleOrNull() }
