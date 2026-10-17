package io.github.greg7gkb.iworkout.data

import io.github.greg7gkb.iworkout.data.local.SetEntity
import org.junit.Assert.assertEquals
import org.junit.Test

// Pins the Sets JSON and Summary written to Airtable: the contract Claude reads (FOUNDATION §6).
class SetsFormatTest {
    private fun set(id: Long, pos: Int, name: String, n: Int, reps: Int?, weight: Double?) =
        SetEntity(id = id, sessionUuid = "s", exercisePosition = pos, exerciseName = name, setNumber = n, reps = reps, weight = weight)

    private val warmUp = set(1, 1, "Warm-up", 1, null, null)
    private val squat1 = set(2, 2, "Goblet Squat", 1, 10, 25.0)
    private val squat2 = set(3, 2, "Goblet Squat", 2, 8, 22.5)
    private val deadBug = set(4, 3, "Dead Bug", 1, 8, null)

    @Test
    fun setsJson_matchesContract() {
        assertEquals(
            """[{"pos":1,"exercise":"Warm-up","done":true},""" +
                """{"pos":2,"exercise":"Goblet Squat","set":1,"reps":10,"weight":25},""" +
                """{"pos":2,"exercise":"Goblet Squat","set":2,"reps":8,"weight":22.5},""" +
                """{"pos":3,"exercise":"Dead Bug","set":1,"reps":8}]""",
            setsJson(listOf(warmUp, squat1, squat2, deadBug)).toString(),
        )
    }

    @Test
    fun setsSummary_onePerLine_inRoutineOrder() {
        assertEquals(
            "Warm-up ✓\nGoblet Squat: 10 @ 25, 8 @ 22.5\nDead Bug: 8",
            setsSummary(listOf(deadBug, squat1, warmUp, squat2)),
        )
    }

    @Test
    fun emptySession() {
        assertEquals("[]", setsJson(emptyList()).toString())
        assertEquals("", setsSummary(emptyList()))
    }
}
