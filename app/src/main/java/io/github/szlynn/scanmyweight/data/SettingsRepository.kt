package io.github.szlynn.scanmyweight.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_WEBHOOK_URL = stringPreferencesKey("webhook_url")
    }

    /**
     * Emits the stored webhook URL wrapped in [Result].
     * A [Result.failure] means the DataStore file could not be read (e.g. IOException);
     * a [Result.success] with an empty string means the value was never set.
     */
    val webhookUrl: Flow<Result<String>> = context.dataStore.data
        .map { prefs -> Result.success(prefs[KEY_WEBHOOK_URL] ?: "") }
        .catch { cause -> if (cause is IOException) emit(Result.failure(cause)) else throw cause }

    suspend fun saveWebhookUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WEBHOOK_URL] = url
        }
    }
}
