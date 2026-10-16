package io.github.greg7gkb.iworkout.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

// Airtable Web API. Field/table IDs: docs/airtable-schema.md.
interface AirtableApi {
    @GET("v0/{baseId}/{tableId}")
    suspend fun listRecords(
        @Path("baseId") baseId: String,
        @Path("tableId") tableId: String,
        @Query("filterByFormula") filterByFormula: String? = null,
        @Query("offset") offset: String? = null,
        @Query("returnFieldsByFieldId") returnFieldsByFieldId: Boolean = true,
    ): RecordList

    // Upsert keyed on fieldsToMergeOn; max 10 records per call.
    @PATCH("v0/{baseId}/{tableId}")
    suspend fun upsertRecords(
        @Path("baseId") baseId: String,
        @Path("tableId") tableId: String,
        @Body body: UpsertRequest,
    ): RecordList
}

@Serializable
data class Record(
    val id: String,
    val fields: JsonObject,
)

@Serializable
data class RecordList(
    val records: List<Record>,
    val offset: String? = null,
)

@Serializable
data class UpsertRequest(
    val performUpsert: PerformUpsert,
    val records: List<UpsertRecord>,
    val returnFieldsByFieldId: Boolean = true,
)

@Serializable
data class PerformUpsert(val fieldsToMergeOn: List<String>)

@Serializable
data class UpsertRecord(val fields: JsonObject)
