package io.github.greg7gkb.iworkout.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlanDao {
    @Query("SELECT * FROM routines WHERE active = 1 ORDER BY createdAt DESC, name")
    abstract fun activeRoutines(): Flow<List<RoutineEntity>>

    // Includes inactive Routines, so History can still name old workouts.
    @Query("SELECT * FROM routines")
    abstract fun allRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id")
    abstract fun routine(id: String): Flow<RoutineEntity?>

    @Query("SELECT * FROM exercises WHERE routineId = :routineId ORDER BY position")
    abstract fun exercisesFor(routineId: String): Flow<List<ExerciseEntity>>

    // The plan is small and Airtable-owned, so a pull replaces it wholesale (also picks up deletions).
    @Transaction
    open suspend fun replaceAll(routines: List<RoutineEntity>, exercises: List<ExerciseEntity>) {
        deleteExercises()
        deleteRoutines()
        insertRoutines(routines)
        insertExercises(exercises)
    }

    @Query("DELETE FROM routines")
    protected abstract suspend fun deleteRoutines()

    @Query("DELETE FROM exercises")
    protected abstract suspend fun deleteExercises()

    @Insert
    protected abstract suspend fun insertRoutines(routines: List<RoutineEntity>)

    @Insert
    protected abstract suspend fun insertExercises(exercises: List<ExerciseEntity>)
}

@Dao
abstract class LogDao {
    @Query("SELECT * FROM sessions WHERE deleted = 0 ORDER BY startedAt DESC")
    abstract fun sessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE uuid = :uuid")
    abstract fun session(uuid: String): Flow<SessionEntity?>

    // The workout in progress, if any (survives the app being killed mid-session).
    @Query("SELECT * FROM sessions WHERE endedAt IS NULL AND deleted = 0 ORDER BY startedAt DESC LIMIT 1")
    abstract fun activeSession(): Flow<SessionEntity?>

    @Query("SELECT * FROM sets WHERE sessionUuid = :sessionUuid ORDER BY exercisePosition, setNumber")
    abstract fun setsFor(sessionUuid: String): Flow<List<SetEntity>>

    @Query(
        """
        SELECT sessions.*, (SELECT COUNT(DISTINCT exercisePosition) FROM sets WHERE sets.sessionUuid = sessions.uuid) AS exerciseCount
        FROM sessions WHERE deleted = 0 AND endedAt IS NOT NULL ORDER BY startedAt DESC
        """,
    )
    abstract fun finishedSessions(): Flow<List<SessionSummary>>

    @Query(
        """
        SELECT sets.*, sessions.startedAt AS startedAt FROM sets JOIN sessions ON sessions.uuid = sets.sessionUuid
        WHERE sets.exerciseName = :name AND sessions.deleted = 0
        ORDER BY sessions.startedAt DESC, sets.setNumber
        """,
    )
    abstract fun exerciseHistory(name: String): Flow<List<SetWithDate>>

    // "Last time": all sets for this exercise from the most recent earlier non-deleted session that has any.
    // "Earlier" is relative to the given session, so backdated workouts compare against their own past.
    @Query(
        """
        SELECT * FROM sets WHERE exerciseName = :name AND sessionUuid = (
            SELECT st.sessionUuid FROM sets st JOIN sessions se ON se.uuid = st.sessionUuid
            WHERE st.exerciseName = :name AND se.deleted = 0 AND se.uuid != :excludeSession
                AND se.startedAt < (SELECT startedAt FROM sessions WHERE uuid = :excludeSession)
            ORDER BY se.startedAt DESC LIMIT 1
        ) ORDER BY setNumber
        """,
    )
    abstract fun lastTime(name: String, excludeSession: String): Flow<List<SetEntity>>

    // Ready to push: changed since last push, and finished or discarded (in-progress workouts stay local).
    @Query("SELECT * FROM sessions WHERE dirty = 1 AND (endedAt IS NOT NULL OR deleted = 1)")
    abstract suspend fun pushable(): List<SessionEntity>

    @Query("SELECT COUNT(*) FROM sessions WHERE dirty = 1 AND (endedAt IS NOT NULL OR deleted = 1)")
    abstract fun pendingCount(): Flow<Int>

    @Query("SELECT * FROM sets WHERE sessionUuid = :sessionUuid ORDER BY exercisePosition, setNumber")
    abstract suspend fun setsSnapshot(sessionUuid: String): List<SetEntity>

    @Query("UPDATE sessions SET dirty = 0 WHERE uuid = :uuid AND revision = :revision")
    protected abstract suspend fun markClean(uuid: String, revision: Int)

    @Query("UPDATE sessions SET uploaded = 1 WHERE uuid = :uuid")
    protected abstract suspend fun markUploaded(uuid: String)

    // After a successful push: Airtable now has the row; clean unless edited mid-push.
    @Transaction
    open suspend fun markPushed(uuid: String, revision: Int) {
        markUploaded(uuid)
        markClean(uuid, revision)
    }

    @Query("SELECT * FROM sessions WHERE uuid = :uuid")
    protected abstract suspend fun sessionNow(uuid: String): SessionEntity?

    @Query("DELETE FROM sets WHERE sessionUuid = :uuid")
    protected abstract suspend fun deleteSetsForSession(uuid: String)

    @Query("DELETE FROM sessions WHERE uuid = :uuid")
    protected abstract suspend fun deleteSession(uuid: String)

    // Never uploaded: just remove it locally. Otherwise soft-delete so the deletion syncs.
    @Transaction
    open suspend fun discard(uuid: String) {
        val session = sessionNow(uuid) ?: return
        if (session.uploaded) {
            softDelete(uuid)
        } else {
            deleteSetsForSession(uuid)
            deleteSession(uuid)
        }
    }

    @Insert
    abstract suspend fun insertSession(session: SessionEntity)

    @Query("UPDATE sessions SET endedAt = :endedAt, dirty = 1, revision = revision + 1 WHERE uuid = :uuid")
    abstract suspend fun finish(uuid: String, endedAt: Long)

    // Only one workout is in progress at a time; starting a new one closes any left open.
    @Query("UPDATE sessions SET endedAt = :endedAt, dirty = 1, revision = revision + 1 WHERE endedAt IS NULL AND deleted = 0")
    abstract suspend fun finishOpen(endedAt: Long)

    @Query("UPDATE sessions SET deleted = 1, dirty = 1, revision = revision + 1 WHERE uuid = :uuid")
    protected abstract suspend fun softDelete(uuid: String)

    @Query("UPDATE sessions SET notes = :notes, dirty = 1, revision = revision + 1 WHERE uuid = :uuid")
    abstract suspend fun setNotes(uuid: String, notes: String)

    // Moves a Session in time, keeping its duration (used to backdate a logged workout).
    @Query(
        """
        UPDATE sessions SET startedAt = startedAt + :deltaMs, endedAt = endedAt + :deltaMs,
            dirty = 1, revision = revision + 1 WHERE uuid = :uuid
        """,
    )
    abstract suspend fun shift(uuid: String, deltaMs: Long)

    @Query("UPDATE sets SET reps = :reps, weight = :weight WHERE id = :id")
    protected abstract suspend fun updateSetValues(id: Long, reps: Int?, weight: Double?)

    @Transaction
    open suspend fun editSet(set: SetEntity, reps: Int?, weight: Double?) {
        updateSetValues(set.id, reps, weight)
        markDirty(set.sessionUuid)
    }

    @Query("UPDATE sessions SET dirty = 1, revision = revision + 1 WHERE uuid = :uuid")
    protected abstract suspend fun markDirty(uuid: String)

    @Query("SELECT * FROM sets WHERE sessionUuid = :sessionUuid AND exercisePosition = :position ORDER BY setNumber")
    protected abstract suspend fun setsForExercise(sessionUuid: String, position: Int): List<SetEntity>

    @Insert
    protected abstract suspend fun insertSet(set: SetEntity)

    @Upsert
    protected abstract suspend fun upsertSets(sets: List<SetEntity>)

    @Query("DELETE FROM sets WHERE id = :id")
    protected abstract suspend fun deleteSet(id: Long)

    @Query("DELETE FROM sets WHERE sessionUuid = :sessionUuid AND exercisePosition = :position")
    protected abstract suspend fun deleteSetsForExercise(sessionUuid: String, position: Int)

    @Transaction
    open suspend fun appendSet(sessionUuid: String, exercise: ExerciseEntity, reps: Int?, weight: Double?) {
        val n = setsForExercise(sessionUuid, exercise.position).size + 1
        insertSet(SetEntity(0, sessionUuid, exercise.position, exercise.name, n, reps, weight, done = true))
        markDirty(sessionUuid)
    }

    // Removes a set and renumbers the remaining sets of that exercise 1..n.
    @Transaction
    open suspend fun removeSet(set: SetEntity) {
        deleteSet(set.id)
        val rest = setsForExercise(set.sessionUuid, set.exercisePosition)
        upsertSets(rest.mapIndexed { i, s -> s.copy(setNumber = i + 1) })
        markDirty(set.sessionUuid)
    }

    // Warm-up / cool-down: a single set with only `done`.
    @Transaction
    open suspend fun setDone(sessionUuid: String, exercise: ExerciseEntity, done: Boolean) {
        deleteSetsForExercise(sessionUuid, exercise.position)
        if (done) insertSet(SetEntity(0, sessionUuid, exercise.position, exercise.name, 1, null, null, done = true))
        markDirty(sessionUuid)
    }
}
