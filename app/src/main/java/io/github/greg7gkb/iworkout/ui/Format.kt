package io.github.greg7gkb.iworkout.ui

import io.github.greg7gkb.iworkout.data.local.ExerciseEntity
import io.github.greg7gkb.iworkout.data.local.ExerciseType
import io.github.greg7gkb.iworkout.data.local.SetEntity

// Pure display/prefill helpers, kept out of composables so they can be unit tested.

// Values to pre-fill for the next Set: the previous Set this session, else the same Set last time
// (or last time's final Set), else the target. Weight only for equipment exercises.
internal fun prefill(exercise: ExerciseEntity, logged: List<SetEntity>, lastTime: List<SetEntity>): Pair<Int?, Double?> {
    val prev = logged.lastOrNull()
    val ref = lastTime.getOrNull(logged.size) ?: lastTime.lastOrNull()
    val reps = prev?.reps ?: ref?.reps ?: leadingInt(exercise.reps)
    val weight = if (exercise.type == ExerciseType.EQUIPMENT) prev?.weight ?: ref?.weight ?: leadingNumber(exercise.weight) else null
    return reps to weight
}

// "10 @ 25", "8", "@ 20", or "✓" for a done-only Set. A timed Set's reps are seconds: "30 s".
internal fun describe(set: SetEntity, timed: Boolean = false): String = when {
    set.reps != null && set.weight != null -> "${set.reps}${if (timed) " s" else ""} @ ${formatWeight(set.weight)}"
    set.reps != null -> if (timed) "${set.reps} s" else "${set.reps}"
    set.weight != null -> "@ ${formatWeight(set.weight)}"
    else -> "✓"
}

internal fun formatWeight(w: Double): String = if (w % 1.0 == 0.0) w.toLong().toString() else w.toString()

// First integer in a free-text target: "8–10" → 8, "6 each leg" → 6.
internal fun leadingInt(s: String?): Int? = s?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }

// First number in a free-text target: "20–25 lb" → 20.0, "light" → null.
internal fun leadingNumber(s: String?): Double? = s?.let { Regex("""\d+(\.\d+)?""").find(it)?.value?.toDoubleOrNull() }

// A timed Exercise's hold, from a reps target that is a duration: "30 sec" → 30, "45 s each side" → 45,
// "1 min" → 60, "30–45 sec" → 30. Null for a rep count ("8–10", "6 each leg").
internal fun targetSeconds(reps: String?): Int? {
    val m = reps?.let { Regex("""(\d+)(?:\s*[–-]\s*\d+)?\s*(s|secs?|seconds?|mins?|minutes?)\b""", RegexOption.IGNORE_CASE).find(it) } ?: return null
    val n = m.groupValues[1].toInt()
    return if (m.groupValues[2].startsWith("m", ignoreCase = true)) n * 60 else n
}

// Timed Sets log seconds as reps.
internal val ExerciseEntity.isTimed get() = targetSeconds(reps) != null

// 30 → "0:30", 90 → "1:30".
internal fun formatClock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
