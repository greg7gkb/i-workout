package io.github.greg7gkb.iworkout.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Builds a real database at each old version from the exported schemas (app/schemas), fills it with data,
// then opens it with the current AppDatabase. Room runs the migrations and validates the resulting schema
// against the current one, throwing if any migration got it wrong. Then we check the data survived.
// Bumping the database version? Export the schema (automatic) and this test covers the new step.
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val schemaDir = File("schemas/${AppDatabase::class.qualifiedName}")
    private val dbName = "migration-test.db"

    @Test
    fun migratesFromEveryOldVersion() = runTest {
        val versions = schemaDir.listFiles()!!.map { it.nameWithoutExtension.toInt() }.sorted()
        val latest = versions.last()
        for (v in versions.filter { it < latest }) migrateFrom(v)
    }

    private suspend fun migrateFrom(version: Int) {
        context.deleteDatabase(dbName)
        createAtVersion(version)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).allowMainThreadQueries().build()
        try {
            val log = db.logDao()
            val clean = log.session("clean").first()!!
            val dirty = log.session("dirty").first()!!
            assertEquals("v$version: notes kept", "clean notes", clean.notes)
            // v4 backfill: clean sessions were already pushed, dirty ones weren't.
            assertTrue("v$version: clean is uploaded", clean.uploaded)
            assertFalse("v$version: dirty not uploaded", dirty.uploaded)
            assertEquals("v$version: sets kept", listOf(10 to 20.0), log.setsFor("clean").first().map { it.reps to it.weight })
            assertEquals("v$version: routine kept", "Strength Day One", db.planDao().routine("r").first()!!.name)
        } finally {
            db.close()
        }
    }

    // Recreates the database exactly as version `version` defined it, then inserts rows using only
    // columns that exist in every version.
    private fun createAtVersion(version: Int) {
        val schema = Json.parseToJsonElement(File(schemaDir, "$version.json").readText()).jsonObject["database"]!!.jsonObject
        val path = context.getDatabasePath(dbName).apply { parentFile!!.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { sql ->
            for (entity in schema["entities"]!!.jsonArray.map { it.jsonObject }) {
                val table = entity["tableName"]!!.jsonPrimitive.content
                sql.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray?.forEach {
                    sql.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            schema["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }

            sql.execSQL("INSERT INTO routines (id, name, notes, active) VALUES ('r', 'Strength Day One', '', 1)")
            sql.execSQL(
                "INSERT INTO sessions (uuid, routineId, startedAt, endedAt, notes, deleted, dirty) VALUES " +
                    "('clean', 'r', 1000, 2000, 'clean notes', 0, 0), ('dirty', 'r', 3000, 4000, '', 0, 1)",
            )
            sql.execSQL(
                "INSERT INTO sets (sessionUuid, exercisePosition, exerciseName, setNumber, reps, weight, done) " +
                    "VALUES ('clean', 2, 'Goblet Squat', 1, 10, 20.0, 1)",
            )
            sql.version = version
        }
    }
}
