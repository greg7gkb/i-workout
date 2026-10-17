package io.github.greg7gkb.iworkout.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class SyncState(val lastSyncedAt: Long?, val lastError: String?)

// One sync attempt, for the on-device sync history. Counts are null when the attempt failed before that step.
@Serializable
data class SyncAttempt(
    val at: Long,
    val ok: Boolean,
    val pushed: Int? = null,
    val routines: Int? = null,
    val exercises: Int? = null,
    val error: String? = null,
)

// Last sync time / error plus a short history of attempts, persisted in SharedPreferences
// and exposed as Flows for the UI. History is bounded, newest first.
class SyncStatus(context: Context) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _history = MutableStateFlow(readHistory())
    val history: StateFlow<List<SyncAttempt>> = _history.asStateFlow()

    @Synchronized
    fun markSynced(pushed: Int, routines: Int, exercises: Int) {
        val now = System.currentTimeMillis()
        record(SyncAttempt(now, ok = true, pushed = pushed, routines = routines, exercises = exercises)) {
            putLong(KEY_AT, now)
            remove(KEY_ERROR)
        }
    }

    @Synchronized
    fun markFailed(error: String, pushed: Int?) {
        record(SyncAttempt(System.currentTimeMillis(), ok = false, pushed = pushed, error = error)) {
            putString(KEY_ERROR, error)
        }
    }

    private fun record(attempt: SyncAttempt, extra: SharedPreferences.Editor.() -> Unit) {
        val history = (listOf(attempt) + _history.value).take(MAX_HISTORY)
        prefs.edit {
            extra()
            putString(KEY_HISTORY, json.encodeToString(history))
        }
        _state.value = readState()
        _history.value = history
    }

    private fun readState() = SyncState(
        lastSyncedAt = prefs.getLong(KEY_AT, 0L).takeIf { it > 0 },
        lastError = prefs.getString(KEY_ERROR, null),
    )

    // A corrupt or old-format history is just dropped; it's diagnostics, not data.
    private fun readHistory(): List<SyncAttempt> =
        prefs.getString(KEY_HISTORY, null)
            ?.let { runCatching { json.decodeFromString<List<SyncAttempt>>(it) }.getOrNull() }
            .orEmpty()

    companion object {
        const val MAX_HISTORY = 20
        private const val KEY_AT = "lastSyncedAt"
        private const val KEY_ERROR = "lastError"
        private const val KEY_HISTORY = "history"
    }
}
