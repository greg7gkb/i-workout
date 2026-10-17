package io.github.greg7gkb.iworkout.data

import io.github.greg7gkb.iworkout.data.local.SetEntity
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

// How a Session's Sets are written to Airtable. This is the contract Claude reads; see docs/FOUNDATION.md, "Data contract".

// Sessions.Sets: one object per Set, in order. Warm-up/cool-down (no reps or weight) carry only `done`.
internal fun setsJson(sets: List<SetEntity>): JsonArray = buildJsonArray {
    sets.forEach { set ->
        addJsonObject {
            put("pos", set.exercisePosition)
            put("exercise", set.exerciseName)
            if (set.reps == null && set.weight == null) {
                put("done", true)
            } else {
                put("set", set.setNumber)
                set.reps?.let { put("reps", it) }
                set.weight?.let { put("weight", if (it % 1.0 == 0.0) JsonPrimitive(it.toLong()) else JsonPrimitive(it)) }
            }
        }
    }
}

// Sessions.Summary: human-readable copy for browsing in Airtable, one exercise per line:
// "Warm-up ✓", "Goblet Squat: 10 @ 25, 10 @ 25", "Dead Bug: 8, 8". Sets JSON stays the source of truth.
internal fun setsSummary(sets: List<SetEntity>): String =
    sets.groupBy { it.exercisePosition }.toSortedMap().values.joinToString("\n") { group ->
        val name = group.first().exerciseName
        if (group.all { it.reps == null && it.weight == null }) {
            "$name ✓"
        } else {
            "$name: " + group.joinToString(", ") { s ->
                listOfNotNull(s.reps?.toString(), s.weight?.let { w -> "@ " + if (w % 1.0 == 0.0) w.toLong() else w }).joinToString(" ")
            }
        }
    }
