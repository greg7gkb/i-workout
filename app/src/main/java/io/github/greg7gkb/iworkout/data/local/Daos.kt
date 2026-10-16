package io.github.greg7gkb.iworkout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {
    @Query("SELECT * FROM routines WHERE active = 1 ORDER BY name")
    fun activeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM exercises WHERE routineId = :routineId ORDER BY position")
    fun exercisesFor(routineId: String): Flow<List<ExerciseEntity>>

    @Upsert
    suspend fun upsertRoutines(routines: List<RoutineEntity>)

    @Upsert
    suspend fun upsertExercises(exercises: List<ExerciseEntity>)
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
