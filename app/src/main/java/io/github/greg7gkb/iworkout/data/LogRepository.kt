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
import io.github.greg7gkb.iworkout.data.remote.links
import io.github.greg7gkb.iworkout.data.remote.bool
import io.github.greg7gkb.iworkout.data.remote.string
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
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
    suspend fun discard(uuid: String) = dao.discard(uuid)

    // Upserts finished/discarded Sessions keyed on UUID, so retries never duplicate rows.
    // Returns how many Sessions were uploaded.
    suspend fun push(): Int {
        val pending = dao.pushable()
        pending.chunked(10).forEach { batch -> // Airtable max per write
            val records = batch.map { UpsertRecord(toFields(it, dao.setsSnapshot(it.uuid))) }
            api.upsertRecords(
                BuildConfig.AIRTABLE_BASE_ID,
                Sessions.TABLE,
                UpsertRequest(PerformUpsert(listOf(Sessions.UUID)), records),
            )
            batch.forEach { dao.markPushed(it.uuid, it.revision) }
        }
        return pending.size
    }

    // Downloads Sessions from Airtable and adds any this phone doesn't have (fresh install, reinstall, new phone).
    // Never touches Sessions already on the phone, so local edits always win; rows marked Deleted are skipped.
    // Returns how many were added.
    suspend fun restore(): Int {
        val known = dao.allUuids().toHashSet()
        var added = 0
        var offset: String? = null
        do {
            val page = api.listRecords(BuildConfig.AIRTABLE_BASE_ID, Sessions.TABLE, offset = offset)
            for (r in page.records) {
                val f = r.fields
                val uuid = f.string(Sessions.UUID) ?: continue
                if (uuid in known || f.bool(Sessions.DELETED)) continue
                val startedAt = f.string(Sessions.STARTED)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: continue
                val session = SessionEntity(
                    uuid = uuid,
                    routineId = f.links(Sessions.ROUTINE).firstOrNull(),
                    startedAt = startedAt,
                    endedAt = f.string(Sessions.ENDED)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: startedAt,
                    notes = f.string(Sessions.NOTES).orEmpty(),
                )
                if (dao.insertRestored(session, parseSetsJson(f.string(Sessions.SETS), uuid))) added++
                known += uuid
            }
            offset = page.offset
        } while (offset != null)
        return added
    }

    private fun toFields(s: SessionEntity, sets: List<SetEntity>) = buildJsonObject {
        put(Sessions.UUID, s.uuid)
        put(Sessions.STARTED, Instant.ofEpochMilli(s.startedAt).toString())
        s.endedAt?.let { put(Sessions.ENDED, Instant.ofEpochMilli(it).toString()) }
        put(Sessions.ROUTINE, JsonArray(listOfNotNull(s.routineId).map(::JsonPrimitive)))
        put(Sessions.NOTES, s.notes)
        put(Sessions.SETS, setsJson(sets).toString())
        put(Sessions.SUMMARY, setsSummary(sets))
        put(Sessions.DELETED, s.deleted)
        // Which app (and so which Sets format) wrote this row. Bump versionName when the format changes.
        put(Sessions.APP_VERSION, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
}
