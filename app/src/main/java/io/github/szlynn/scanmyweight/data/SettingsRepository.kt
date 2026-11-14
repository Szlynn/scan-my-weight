package io.github.szlynn.scanmyweight.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_WEBHOOK_URL = stringPreferencesKey("webhook_url")
    }

    val webhookUrl: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[KEY_WEBHOOK_URL] ?: "" }

    suspend fun saveWebhookUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_WEBHOOK_URL] = url
        }
    }
}
