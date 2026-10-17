package io.github.greg7gkb.iworkout.data

import io.github.greg7gkb.iworkout.BuildConfig
import io.github.greg7gkb.iworkout.data.local.ExerciseEntity
import io.github.greg7gkb.iworkout.data.local.ExerciseType
import io.github.greg7gkb.iworkout.data.local.PlanDao
import io.github.greg7gkb.iworkout.data.local.RoutineEntity
import io.github.greg7gkb.iworkout.data.remote.AirtableApi
import io.github.greg7gkb.iworkout.data.remote.AirtableSchema.Exercises
import io.github.greg7gkb.iworkout.data.remote.AirtableSchema.Routines
import io.github.greg7gkb.iworkout.data.remote.Record
import io.github.greg7gkb.iworkout.data.remote.bool
import io.github.greg7gkb.iworkout.data.remote.int
import io.github.greg7gkb.iworkout.data.remote.links
import io.github.greg7gkb.iworkout.data.remote.string

// Routines + Exercises: read from Room, refreshed from Airtable (Airtable wins).
class PlanRepository(
    private val dao: PlanDao,
    private val api: AirtableApi,
) {
    fun activeRoutines() = dao.activeRoutines()
    fun routine(id: String) = dao.routine(id)
    fun exercisesFor(routineId: String) = dao.exercisesFor(routineId)

    suspend fun pull() {
        val routines = fetchAll(Routines.TABLE).map { r ->
            RoutineEntity(
                id = r.id,
                name = r.fields.string(Routines.NAME).orEmpty(),
                notes = r.fields.string(Routines.NOTES).orEmpty(),
                active = r.fields.bool(Routines.ACTIVE),
            )
        }
        val routineIds = routines.mapTo(HashSet()) { it.id }
        val exercises = fetchAll(Exercises.TABLE).mapNotNull { r ->
            val f = r.fields
            // Skip orphans (no Routine, or a Routine we didn't get) rather than failing the pull.
            val routineId = f.links(Exercises.ROUTINE).firstOrNull()?.takeIf { it in routineIds }
                ?: return@mapNotNull null
            ExerciseEntity(
                id = r.id,
                routineId = routineId,
                position = f.int(Exercises.POSITION) ?: Int.MAX_VALUE,
                name = f.string(Exercises.NAME).orEmpty(),
                type = ExerciseType.fromAirtable(f.string(Exercises.TYPE)),
                instructions = f.string(Exercises.INSTRUCTIONS).orEmpty(),
                sets = f.int(Exercises.SETS),
                reps = f.string(Exercises.REPS),
                weight = f.string(Exercises.WEIGHT),
            )
        }
        dao.replaceAll(routines, exercises)
    }

    private suspend fun fetchAll(table: String): List<Record> {
        val out = mutableListOf<Record>()
        var offset: String? = null
        do {
            val page = api.listRecords(BuildConfig.AIRTABLE_BASE_ID, table, offset = offset)
            out += page.records
            offset = page.offset
        } while (offset != null)
        return out
    }
}
