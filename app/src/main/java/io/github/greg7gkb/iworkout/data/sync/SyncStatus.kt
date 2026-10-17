package io.github.greg7gkb.iworkout.data.sync

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncState(val lastSyncedAt: Long?, val lastError: String?)

// Last sync time / error, persisted in SharedPreferences and exposed as a Flow for the UI.
class SyncStatus(context: Context) {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    fun markSynced() {
        prefs.edit { putLong(KEY_AT, System.currentTimeMillis()); remove(KEY_ERROR) }
        _state.value = read()
    }

    fun markFailed(error: String) {
        prefs.edit { putString(KEY_ERROR, error) }
        _state.value = read()
    }

    private fun read() = SyncState(
        lastSyncedAt = prefs.getLong(KEY_AT, 0L).takeIf { it > 0 },
        lastError = prefs.getString(KEY_ERROR, null),
    )

    private companion object {
        const val KEY_AT = "lastSyncedAt"
        const val KEY_ERROR = "lastError"
    }
}
