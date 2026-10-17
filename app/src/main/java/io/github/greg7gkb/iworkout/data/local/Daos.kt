package io.github.greg7gkb.iworkout.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlanDao {
    @Query("SELECT * FROM routines WHERE active = 1 ORDER BY name")
    abstract fun activeRoutines(): Flow<List<RoutineEntity>>

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
interface LogDao {
    @Query("SELECT * FROM sessions WHERE deleted = 0 ORDER BY startedAt DESC")
    fun sessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sets WHERE sessionUuid = :sessionUuid ORDER BY exercisePosition, setNumber")
    fun setsFor(sessionUuid: String): Flow<List<SetEntity>>

    @Query("SELECT * FROM sessions WHERE dirty = 1")
    suspend fun dirtySessions(): List<SessionEntity>

    @Upsert
    suspend fun upsertSession(session: SessionEntity)

    @Upsert
    suspend fun upsertSet(set: SetEntity)
}
