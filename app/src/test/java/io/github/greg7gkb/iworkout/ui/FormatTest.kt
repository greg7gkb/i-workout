package io.github.greg7gkb.iworkout.ui

import io.github.greg7gkb.iworkout.data.local.ExerciseEntity
import io.github.greg7gkb.iworkout.data.local.ExerciseType
import io.github.greg7gkb.iworkout.data.local.SetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    private val squat = ExerciseEntity("e", "r", 2, "Goblet Squat", ExerciseType.EQUIPMENT, "", sets = 2, reps = "8–10", weight = "20–25 lb")
    private val pushUp = squat.copy(name = "Push-up", type = ExerciseType.BODYWEIGHT, reps = "6 each side", weight = "hands on bench")

    private fun set(n: Int, reps: Int?, weight: Double?) =
        SetEntity(sessionUuid = "s", exercisePosition = 2, exerciseName = "Goblet Squat", setNumber = n, reps = reps, weight = weight)

    @Test
    fun prefill_noHistory_usesTarget() {
        assertEquals(8 to 20.0, prefill(squat, logged = emptyList(), lastTime = emptyList()))
    }

    @Test
    fun prefill_sameSetFromLastTime() {
        val lastTime = listOf(set(1, 10, 25.0), set(2, 9, 27.5))
        assertEquals(10 to 25.0, prefill(squat, emptyList(), lastTime))
    }

    @Test
    fun prefill_previousSetMissingWeight_fallsBackToLastTimesFinalSet() {
        // Set 2 has no counterpart last time, so the weight comes from last time's final Set.
        val lastTime = listOf(set(1, 10, 25.0))
        assertEquals(10 to 25.0, prefill(squat, logged = listOf(set(1, 10, null)), lastTime = lastTime))
    }

    @Test
    fun prefill_previousSetThisSessionWins() {
        val lastTime = listOf(set(1, 10, 25.0), set(2, 10, 25.0))
        assertEquals(6 to 30.0, prefill(squat, logged = listOf(set(1, 6, 30.0)), lastTime = lastTime))
    }

    @Test
    fun prefill_bodyweight_hasNoWeight() {
        assertEquals(6 to null, prefill(pushUp, emptyList(), emptyList()))
    }

    @Test
    fun targetParsing() {
        assertEquals(8, leadingInt("8–10"))
        assertEquals(6, leadingInt("6 each leg"))
        assertNull(leadingInt(null))
        assertEquals(20.0, leadingNumber("20–25 lb")!!, 0.0)
        assertEquals(12.5, leadingNumber("12.5 lb")!!, 0.0)
        assertNull(leadingNumber("light"))
    }

    @Test
    fun describeAndFormatWeight() {
        assertEquals("10 @ 25", describe(set(1, 10, 25.0)))
        assertEquals("8 @ 22.5", describe(set(1, 8, 22.5)))
        assertEquals("8", describe(set(1, 8, null)))
        assertEquals("✓", describe(set(1, null, null)))
    }
}
