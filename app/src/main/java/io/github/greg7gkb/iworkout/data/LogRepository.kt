package io.github.greg7gkb.iworkout.data

import io.github.greg7gkb.iworkout.BuildConfig
import io.github.greg7gkb.iworkout.data.local.ExerciseEntity
import io.github.greg7gkb.iworkout.data.local.LogDao
import io.github.greg7gkb.iworkout.data.local.SessionEntity
import io.github.greg7gkb.iworkout.data.local.SetEntity
import io.github.greg7gkb.iworkout.data.remote.AirtableApi
import io.github.greg7gkb.iworkout.data.remote.AirtableSchema.Sessions
import io.github.greg7gkb.iworkout.data.remote.PerformUpsert
import io.github.greg7gkb.iworkout.data.remote.UpsertRecord
import io.github.greg7gkb.iworkout.data.remote.UpsertRequest
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Sessions + Sets: written only on the phone; every change marks the Session dirty for the next push.
class LogRepository(
    private val dao: LogDao,
    private val api: AirtableApi,
) {
    fun activeSession() = dao.activeSession()
    fun session(uuid: String) = dao.session(uuid)
    fun setsFor(sessionUuid: String) = dao.setsFor(sessionUuid)
    fun lastTime(exerciseName: String, currentSession: String) = dao.lastTime(exerciseName, currentSession)
    fun pendingCount() = dao.pendingCount()
    fun finishedSessions() = dao.finishedSessions()
    fun exerciseHistory(name: String) = dao.exerciseHistory(name)

    suspend fun start(routineId: String): String {
        val uuid = UUID.randomUUID().toString()
        dao.finishOpen(System.currentTimeMillis())
        dao.insertSession(
            SessionEntity(uuid = uuid, routineId = routineId, startedAt = System.currentTimeMillis(), endedAt = null, notes = ""),
        )
        return uuid
    }

    suspend fun logSet(sessionUuid: String, exercise: ExerciseEntity, reps: Int?, weight: Double?) =
        dao.appendSet(sessionUuid, exercise, reps, weight)

    suspend fun removeSet(set: SetEntity) = dao.removeSet(set)
    suspend fun editSet(set: SetEntity, reps: Int?, weight: Double?) = dao.editSet(set, reps, weight)
    suspend fun moveStart(session: SessionEntity, newStartedAt: Long) = dao.shift(session.uuid, newStartedAt - session.startedAt)
    suspend fun setDone(sessionUuid: String, exercise: ExerciseEntity, done: Boolean) = dao.setDone(sessionUuid, exercise, done)
    suspend fun setNotes(uuid: String, notes: String) = dao.setNotes(uuid, notes)
    suspend fun finish(uuid: String) = dao.finish(uuid, System.currentTimeMillis())
    suspend fun discard(uuid: String) = dao.softDelete(uuid)

    // Upserts finished/discarded Sessions keyed on UUID, so retries never duplicate rows.
    suspend fun push() {
        dao.pushable().chunked(10).forEach { batch -> // Airtable max per write
            val records = batch.map { UpsertRecord(toFields(it, dao.setsSnapshot(it.uuid))) }
            api.upsertRecords(
                BuildConfig.AIRTABLE_BASE_ID,
                Sessions.TABLE,
                UpsertRequest(PerformUpsert(listOf(Sessions.UUID)), records),
            )
            batch.forEach { dao.markClean(it.uuid, it.revision) }
        }
    }

    private fun toFields(s: SessionEntity, sets: List<SetEntity>) = buildJsonObject {
        put(Sessions.UUID, s.uuid)
        put(Sessions.STARTED, Instant.ofEpochMilli(s.startedAt).toString())
        s.endedAt?.let { put(Sessions.ENDED, Instant.ofEpochMilli(it).toString()) }
        put(Sessions.ROUTINE, JsonArray(listOfNotNull(s.routineId).map(::JsonPrimitive)))
        put(Sessions.NOTES, s.notes)
        put(Sessions.SETS, setsJson(sets).toString())
        put(Sessions.DELETED, s.deleted)
    }

    // Format: docs/FOUNDATION.md §6. Warm-up/cool-down (no reps or weight) carry only `done`.
    private fun setsJson(sets: List<SetEntity>) = buildJsonArray {
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
}
