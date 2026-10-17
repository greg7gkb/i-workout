package io.github.greg7gkb.iworkout.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.greg7gkb.iworkout.BuildConfig
import io.github.greg7gkb.iworkout.data.local.AppDatabase
import io.github.greg7gkb.iworkout.data.local.ExerciseEntity
import io.github.greg7gkb.iworkout.data.local.ExerciseType
import io.github.greg7gkb.iworkout.data.local.RoutineEntity
import io.github.greg7gkb.iworkout.data.local.SessionEntity
import io.github.greg7gkb.iworkout.data.remote.AirtableApi
import io.github.greg7gkb.iworkout.data.remote.AirtableSchema.Exercises
import io.github.greg7gkb.iworkout.data.remote.AirtableSchema.Routines
import io.github.greg7gkb.iworkout.data.remote.AirtableSchema.Sessions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

// The real repositories and Retrofit client against a fake Airtable (MockWebServer) and in-memory Room.
@RunWith(AndroidJUnit4::class)
class SyncTest {
    private val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var log: LogRepository
    private lateinit var plan: PlanRepository

    private val squat = ExerciseEntity("e2", "r", 2, "Goblet Squat", ExerciseType.EQUIPMENT, "", 2, "10", "20–25 lb")

    @Before
    fun setUp() {
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AirtableApi::class.java)
        log = LogRepository(db.logDao(), api)
        plan = PlanRepository(db.planDao(), api)
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun ok(body: String = """{"records":[]}""") = MockResponse.Builder().code(200).body(body).build()

    private suspend fun finished(uuid: String, endedAt: Long? = 2_000) =
        db.logDao().insertSession(SessionEntity(uuid, "r", 1_000, endedAt, "", deleted = false, dirty = true))

    private fun requestJson(): JsonObject {
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/v0/${BuildConfig.AIRTABLE_BASE_ID}/${Sessions.TABLE}", request.url.encodedPath)
        return Json.parseToJsonElement(request.body!!.utf8()).jsonObject
    }

    @Test
    fun push_upsertsOnUuid_inBatchesOfTen_andSkipsInProgress() = runTest {
        repeat(12) { finished("s$it") }
        finished("inProgress", endedAt = null)
        db.logDao().appendSet("s0", squat, 10, 25.0)
        server.enqueue(ok())
        server.enqueue(ok())

        assertEquals("uploaded count", 12, log.push())

        val first = requestJson()
        assertEquals(Sessions.UUID, first["performUpsert"]!!.jsonObject["fieldsToMergeOn"]!!.jsonArray.single().jsonPrimitive.content)
        val records = first["records"]!!.jsonArray
        assertEquals(10, records.size)
        assertEquals(2, requestJson()["records"]!!.jsonArray.size)
        assertEquals(2, server.requestCount)

        val s0 = records.map { it.jsonObject["fields"]!!.jsonObject }.single { it[Sessions.UUID]!!.jsonPrimitive.content == "s0" }
        assertEquals("""[{"pos":2,"exercise":"Goblet Squat","set":1,"reps":10,"weight":25}]""", s0[Sessions.SETS]!!.jsonPrimitive.content)
        assertEquals("Goblet Squat: 10 @ 25", s0[Sessions.SUMMARY]!!.jsonPrimitive.content)
        assertEquals("r", s0[Sessions.ROUTINE]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", s0[Sessions.APP_VERSION]!!.jsonPrimitive.content)

        assertTrue("all finished sessions are clean", db.logDao().pushable().isEmpty())
        assertTrue(db.logDao().session("s0").first()!!.uploaded)
    }

    @Test
    fun push_failure_leavesSessionUnsent() = runTest {
        finished("s")
        server.enqueue(MockResponse.Builder().code(500).build())

        try {
            log.push()
            fail("expected the HTTP error to propagate so the worker retries")
        } catch (_: HttpException) {
        }

        val s = db.logDao().session("s").first()!!
        assertTrue(s.dirty)
        assertTrue(!s.uploaded)
    }

    @Test
    fun pull_replacesPlan_followsPaging_skipsOrphans() = runTest {
        db.planDao().replaceAll(listOf(RoutineEntity("stale", "Old routine", "", true)), emptyList())
        server.enqueue(
            ok(
                """{"records":[{"id":"r1","createdTime":"2026-10-01T00:00:00.000Z","fields":
                {"${Routines.NAME}":"Day One","${Routines.ACTIVE}":true}}],"offset":"page2"}""",
            ),
        )
        server.enqueue(
            ok(
                """{"records":[{"id":"r2","createdTime":"2026-10-05T00:00:00.000Z","fields":
                {"${Routines.NAME}":"Day Two","${Routines.ACTIVE}":true}}]}""",
            ),
        )
        server.enqueue(
            ok(
                """{"records":[
                {"id":"x1","fields":{"${Exercises.NAME}":"Goblet Squat","${Exercises.ROUTINE}":["r1"],
                    "${Exercises.POSITION}":2,"${Exercises.TYPE}":"equipment","${Exercises.SETS}":2}},
                {"id":"x2","fields":{"${Exercises.NAME}":"Warm-up","${Exercises.ROUTINE}":["r1"],
                    "${Exercises.POSITION}":1,"${Exercises.TYPE}":"warm-up"}},
                {"id":"orphan","fields":{"${Exercises.NAME}":"Lost","${Exercises.ROUTINE}":["nope"]}},
                {"id":"unlinked","fields":{"${Exercises.NAME}":"Nowhere"}}]}""",
            ),
        )

        assertEquals("routines to exercises", 2 to 2, plan.pull())

        assertEquals(listOf("Day Two", "Day One"), plan.activeRoutines().first().map { it.name }) // newest first; stale gone
        assertEquals(listOf("Warm-up" to ExerciseType.WARM_UP, "Goblet Squat" to ExerciseType.EQUIPMENT), plan.exercisesFor("r1").first().map { it.name to it.type })
        server.takeRequest()
        assertEquals("page2", server.takeRequest().url.queryParameter("offset"))
    }
}
