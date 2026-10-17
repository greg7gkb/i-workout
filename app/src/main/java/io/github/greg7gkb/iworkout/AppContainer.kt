package io.github.greg7gkb.iworkout

import android.content.Context
import io.github.greg7gkb.iworkout.data.LogRepository
import io.github.greg7gkb.iworkout.data.PlanRepository
import io.github.greg7gkb.iworkout.data.local.AppDatabase
import io.github.greg7gkb.iworkout.data.remote.AirtableApi
import io.github.greg7gkb.iworkout.data.sync.SyncStatus
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

// Hand-wired dependencies; one instance per process, owned by IWorkoutApp.
class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.create(context)

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("Authorization", "Bearer ${BuildConfig.AIRTABLE_TOKEN}")
                    .build(),
            )
        }
        .build()

    private val airtable: AirtableApi = Retrofit.Builder()
        .baseUrl("https://api.airtable.com/")
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AirtableApi::class.java)

    val plan = PlanRepository(db.planDao(), airtable)
    val log = LogRepository(db.logDao(), airtable)
    val syncStatus = SyncStatus(context)
}
