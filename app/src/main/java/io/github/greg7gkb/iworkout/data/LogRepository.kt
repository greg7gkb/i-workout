package io.github.greg7gkb.iworkout.data

import io.github.greg7gkb.iworkout.data.local.ExerciseEntity
import io.github.greg7gkb.iworkout.data.local.LogDao
import io.github.greg7gkb.iworkout.data.local.SessionEntity
import io.github.greg7gkb.iworkout.data.local.SetEntity
import java.util.UUID

// Sessions + Sets: written only on the phone; every change marks the Session dirty for the next push.
class LogRepository(private val dao: LogDao) {
    fun activeSession() = dao.activeSession()
    fun session(uuid: String) = dao.session(uuid)
    fun setsFor(sessionUuid: String) = dao.setsFor(sessionUuid)
    fun lastTime(exerciseName: String, currentSession: String) = dao.lastTime(exerciseName, currentSession)

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
    suspend fun setDone(sessionUuid: String, exercise: ExerciseEntity, done: Boolean) = dao.setDone(sessionUuid, exercise, done)
    suspend fun setNotes(uuid: String, notes: String) = dao.setNotes(uuid, notes)
    suspend fun finish(uuid: String) = dao.finish(uuid, System.currentTimeMillis())
    suspend fun discard(uuid: String) = dao.softDelete(uuid)
}
