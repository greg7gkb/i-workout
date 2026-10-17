package io.github.greg7gkb.iworkout.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Room on the JVM via Robolectric, against an in-memory database.
@RunWith(AndroidJUnit4::class)
class LogDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: LogDao

    private val squat = ExerciseEntity("e2", "r", 2, "Goblet Squat", ExerciseType.EQUIPMENT, "", 2, "10", "20–25 lb")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.logDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun session(
        uuid: String,
        startedAt: Long = 1_000,
        endedAt: Long? = 2_000,
        deleted: Boolean = false,
        dirty: Boolean = true,
        uploaded: Boolean = false,
    ) = SessionEntity(uuid, "r", startedAt, endedAt, "", deleted, dirty, uploaded = uploaded).also { dao.insertSession(it) }

    private suspend fun current(uuid: String) = dao.session(uuid).first()

    @Test
    fun lastTime_isLatestEarlierNonDeletedSession() = runTest {
        session("a", startedAt = 100).also { dao.appendSet(it.uuid, squat, 10, 20.0) }
        session("b", startedAt = 200, deleted = true).also { dao.appendSet(it.uuid, squat, 10, 99.0) }
        session("c", startedAt = 300).also { dao.appendSet(it.uuid, squat, 10, 25.0) }
        session("d", startedAt = 250) // a backdated workout between a and c
        session("e", startedAt = 400)

        assertEquals(listOf(20.0), dao.lastTime("Goblet Squat", "d").first().map { it.weight })
        assertEquals(listOf(25.0), dao.lastTime("Goblet Squat", "e").first().map { it.weight })
        assertTrue(dao.lastTime("Goblet Squat", "a").first().isEmpty())
    }

    @Test
    fun discard_neverUploaded_deletesLocally() = runTest {
        session("s", uploaded = false).also { dao.appendSet(it.uuid, squat, 10, 20.0) }

        dao.discard("s")

        assertNull(current("s"))
        assertTrue(dao.setsFor("s").first().isEmpty())
        assertTrue(dao.pushable().isEmpty())
    }

    @Test
    fun discard_uploaded_softDeletesSoTheDeletionSyncs() = runTest {
        session("s", uploaded = true, dirty = false)

        dao.discard("s")

        val s = current("s")!!
        assertTrue(s.deleted)
        assertTrue(s.dirty)
        assertEquals(listOf("s"), dao.pushable().map { it.uuid })
    }

    @Test
    fun pushable_onlyFinishedOrDeleted() = runTest {
        session("finished")
        session("inProgress", endedAt = null)
        session("discarded", endedAt = null, deleted = true)
        session("clean", dirty = false)

        assertEquals(setOf("finished", "discarded"), dao.pushable().map { it.uuid }.toSet())
    }

    @Test
    fun markPushed_cleansOnlyIfNotEditedMidPush() = runTest {
        session("s")
        val sent = dao.pushable().single()

        dao.setNotes("s", "edited while uploading") // bumps revision
        dao.markPushed("s", sent.revision)

        current("s")!!.let {
            assertTrue("upload happened", it.uploaded)
            assertTrue("edit must still be pushed", it.dirty)
        }

        dao.markPushed("s", dao.pushable().single().revision)
        assertFalse(current("s")!!.dirty)
    }

    @Test
    fun shift_movesStartAndEndTogether() = runTest {
        session("s", startedAt = 10_000, endedAt = 70_000)

        dao.shift("s", -5_000)

        current("s")!!.let {
            assertEquals(5_000, it.startedAt)
            assertEquals(65_000L, it.endedAt)
        }
    }

    @Test
    fun finishOpen_closesOnlyOpenSessions() = runTest {
        session("open", endedAt = null)
        session("done", endedAt = 2_000)

        dao.finishOpen(9_000)

        assertEquals(9_000L, current("open")!!.endedAt)
        assertEquals(2_000L, current("done")!!.endedAt)
    }

    @Test
    fun removeSet_renumbersRemainingSets() = runTest {
        session("s")
        dao.appendSet("s", squat, 10, 20.0)
        dao.appendSet("s", squat, 9, 20.0)
        dao.appendSet("s", squat, 8, 20.0)

        dao.removeSet(dao.setsFor("s").first()[1])

        assertEquals(listOf(1 to 10, 2 to 8), dao.setsFor("s").first().map { it.setNumber to it.reps })
    }
}
