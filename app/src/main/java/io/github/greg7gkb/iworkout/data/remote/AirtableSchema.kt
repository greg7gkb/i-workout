package io.github.greg7gkb.iworkout.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

// Stable Airtable IDs; mirror of docs/airtable-schema.md. Field IDs survive renames in the Airtable UI.
object AirtableSchema {
    object Routines {
        const val TABLE = "tbleFsPQ00hIQOnwH"
        const val NAME = "fldAQ13VPKKxHsLcE"
        const val NOTES = "fldnu6wSiihZlFnj6"
        const val ACTIVE = "fldMk7KijA21uw9do"
    }

    object Exercises {
        const val TABLE = "tblGWXvbop8XtWbB3"
        const val NAME = "fld3qeijt43MM2SKZ"
        const val ROUTINE = "fld1YWtyc9HOQkbXZ"
        const val POSITION = "fld85ej7Zvbg0mJHh"
        const val TYPE = "fldFDGbsE9pDhdvVp"
        const val INSTRUCTIONS = "fldur560dab6tReaK"
        const val SETS = "fldMTqVJifdYDe64n"
        const val REPS = "fldv0ZX1sLonJAte8"
        const val WEIGHT = "fldJxPE9cCsZKWQeb"
    }

    object Sessions {
        const val TABLE = "tblvitGsglPEhUFv9"
        const val UUID = "fldFDsxy3OMs31HVV"
        const val STARTED = "fldW2JUunfFsdtiFP"
        const val ENDED = "fld8BeaJxT2Zh8YBK"
        const val ROUTINE = "fldw6Bds6Ot80t9fA"
        const val NOTES = "fldMK4JsAZG0NPBsa"
        const val SETS = "fld50x68oynBecEJK"
        const val DELETED = "fldLENVhPJXrh5Wor"
        const val SUMMARY = "fldPiPbgEZCqTo2Om"
    }
}

// Airtable omits empty fields entirely, so every accessor is nullable.
fun JsonObject.string(id: String): String? = (get(id) as? JsonPrimitive)?.contentOrNull
fun JsonObject.int(id: String): Int? = (get(id) as? JsonPrimitive)?.intOrNull
fun JsonObject.bool(id: String): Boolean = (get(id) as? JsonPrimitive)?.booleanOrNull ?: false
fun JsonObject.links(id: String): List<String> =
    (get(id) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
