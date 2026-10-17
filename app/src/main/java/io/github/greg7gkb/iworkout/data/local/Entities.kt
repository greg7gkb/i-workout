package io.github.greg7gkb.iworkout.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Vocabulary and ownership rules: docs/FOUNDATION.md.

// ---- The plan (pulled from Airtable; Airtable wins) ----

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String, // Airtable record id
    val name: String,
    val notes: String,
    val active: Boolean,
)

@Entity(
    tableName = "exercises",
    indices = [Index("routineId")],
)
data class ExerciseEntity(
    @PrimaryKey val id: String, // Airtable record id
    val routineId: String,
    val position: Int,
    val name: String,
    val type: ExerciseType,
    val instructions: String,
    val sets: Int?,
    val reps: String?,
    val weight: String?,
)

enum class ExerciseType(val airtableName: String) {
    WARM_UP("warm-up"),
    EQUIPMENT("equipment"),
    BODYWEIGHT("bodyweight"),
    COOL_DOWN("cool-down");

    companion object {
        fun fromAirtable(name: String?): ExerciseType =
            entries.firstOrNull { it.airtableName == name } ?: EQUIPMENT
    }
}

// ---- The log (written on the phone; pushed to Airtable) ----

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val uuid: String,
    val routineId: String?,
    val startedAt: Long, // epoch millis
    val endedAt: Long?,
    val notes: String,
    val deleted: Boolean = false,
    // True when local changes haven't been pushed yet.
    val dirty: Boolean = true,
    // Bumped on every change; a push only clears `dirty` if no change happened while it was in flight.
    @ColumnInfo(defaultValue = "0") val revision: Int = 0,
)

@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["sessionUuid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionUuid"), Index("exerciseName")],
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionUuid: String,
    val exercisePosition: Int,
    val exerciseName: String, // "last time" matches on name
    val setNumber: Int,
    val reps: Int?,
    val weight: Double?, // lb
    val done: Boolean = true,
)
