package io.github.greg7gkb.iworkout.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.greg7gkb.iworkout.ui.describeAttempt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncStatusTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun history_isNewestFirst_bounded_andPersisted() {
        val status = SyncStatus(context)
        repeat(SyncStatus.MAX_HISTORY + 5) { status.markSynced(pushed = it, routines = 2, exercises = 13) }
        status.markFailed("timeout", pushed = 1)

        val reloaded = SyncStatus(context).history.value // a fresh instance reads it back from storage
        assertEquals(SyncStatus.MAX_HISTORY, reloaded.size)
        reloaded.first().let {
            assertFalse(it.ok)
            assertEquals("timeout", it.error)
        }
        assertEquals(SyncStatus.MAX_HISTORY + 4, reloaded[1].pushed)
        assertEquals("timeout", SyncStatus(context).state.value.lastError)
    }

    @Test
    fun success_clearsLastError() {
        val status = SyncStatus(context)
        status.markFailed("boom", pushed = null)
        status.markSynced(pushed = 0, routines = 2, exercises = 13)
        assertNull(status.state.value.lastError)
    }

    @Test
    fun describeAttempt_text() {
        assertEquals("Uploaded 1 workout · 2 routines, 13 exercises", describeAttempt(SyncAttempt(0, true, pushed = 1, routines = 2, exercises = 13)))
        assertEquals(
            "Uploaded 0 workouts · restored 3 · 2 routines, 13 exercises",
            describeAttempt(SyncAttempt(0, true, pushed = 0, restored = 3, routines = 2, exercises = 13)),
        )
        assertEquals("Uploaded 0 workouts", describeAttempt(SyncAttempt(0, false, pushed = 0, error = "x")))
        assertNull(describeAttempt(SyncAttempt(0, false, error = "x")))
    }
}
