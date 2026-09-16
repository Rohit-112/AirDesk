package com.share.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.share.app.domain.media.ImagePreview
import com.share.app.domain.model.HistoryItem
import com.share.app.domain.model.ThemePreference
import com.share.app.domain.policy.SessionLimits
import com.share.app.domain.repository.HistoryRepository
import com.share.app.domain.repository.PreferencesRepository
import com.share.app.util.AppLog
import com.share.app.util.newId
import com.share.app.util.suspendRunCatching
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import okio.Path.Companion.toPath
import kotlin.uuid.Uuid

internal const val PREFERENCES_FILE_NAME = "knotic.preferences_pb"

fun createPreferencesDataStore(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { producePath().toPath() })

class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    override val themePreference: Flow<ThemePreference> =
        dataStore.data
            .map { ThemePreference.fromKey(it[THEME_KEY]) }
            .catch { emit(ThemePreference.SYSTEM) }

    override suspend fun setThemePreference(preference: ThemePreference) {
        dataStore.edit { it[THEME_KEY] = preference.key }
    }

    override suspend fun deviceId(): String =
        suspendRunCatching {
            dataStore.data.first()[DEVICE_ID_KEY] ?: Uuid.random().toString().also { id ->
                dataStore.edit { it[DEVICE_ID_KEY] = id }
            }
        }.getOrElse { error ->
            // Storage can fail entirely; an ephemeral id still works.
            AppLog.w(error) { "Device id could not be persisted" }
            newId("dev")
        }

    private companion object {
        /** Fixed identifiers, not branding: renaming them orphans existing device ids. */
        val DEVICE_ID_KEY = stringPreferencesKey("knotic-device-id")
        val THEME_KEY = stringPreferencesKey("knotic-theme-preference")
    }
}

/**
 * The activity log lives only as long as the process, like the web client's.
 * Only the most recent few received files keep their bytes; older rows keep
 * their name, size and thumbnail, and lose the save button. A thumbnail is a
 * few kilobytes, so it lasts as long as its row.
 *
 * Only the session engine calls this, on its own single thread, so the payload
 * map needs no lock.
 */
class InMemoryHistoryRepository : HistoryRepository {
    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    override val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    private val payloads = mutableMapOf<String, ByteArray>()

    override fun add(item: HistoryItem, payload: ByteArray?) {
        if (payload != null) payloads[item.id] = payload

        val capped = (listOf(item) + _history.value).take(SessionLimits.MAX_TRACKED_HISTORY)
        var downloadable = 0
        val trimmed = capped.map { row ->
            if (!row.hasPayload) return@map row
            downloadable += 1
            if (downloadable <= SessionLimits.MAX_DOWNLOADABLE) row else row.copy(hasPayload = false)
        }

        val keep = trimmed.filter { it.hasPayload }.mapTo(HashSet()) { it.id }
        payloads.keys.retainAll(keep)
        _history.value = trimmed
    }

    override fun payload(id: String): ByteArray? = payloads[id]

    override fun setThumbnail(id: String, thumbnail: ImagePreview) {
        _history.update { rows -> rows.map { if (it.id == id) it.copy(thumbnail = thumbnail) else it } }
    }
}
