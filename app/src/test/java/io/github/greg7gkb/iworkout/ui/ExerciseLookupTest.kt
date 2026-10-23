package io.github.greg7gkb.iworkout.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseLookupTest {
    private fun url(lookup: ExerciseLookup, name: String) = googleSearchUrl(lookup.query(name))

    @Test
    fun queries() {
        assertEquals("https://www.google.com/search?q=Bulgarian+Split+Squats+exercise", url(ExerciseLookup.SEARCH, "Bulgarian Split Squats"))
        assertEquals("https://www.google.com/search?q=Bulgarian+Split+Squats+exercise+images", url(ExerciseLookup.IMAGES, "Bulgarian Split Squats"))
        assertEquals("https://www.google.com/search?q=how+to+do+Bulgarian+Split+Squats+exercise", url(ExerciseLookup.HOW_TO, "Bulgarian Split Squats"))
    }

    @Test
    fun specialCharactersAreEncoded() {
        assertEquals("https://www.google.com/search?q=Push-up+%26+Hold+%2890%25%29+exercise", url(ExerciseLookup.SEARCH, "Push-up & Hold (90%)"))
    }
}
