package io.github.greg7gkb.iworkout.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RoutineEntity::class, ExerciseEntity::class, SessionEntity::class, SetEntity::class],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun logDao(): LogDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "iworkout.db").build()
    }
}
