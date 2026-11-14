package io.github.szlynn.scanmyweight.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.szlynn.scanmyweight.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val webhookUrl: String = "",
    val isSaved: Boolean = false,
)

sealed interface SettingsEvent {
    data object SaveRequested : SettingsEvent
}

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = repository.webhookUrl
        .map { url -> SettingsUiState(webhookUrl = url) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun saveWebhookUrl(url: String) {
        viewModelScope.launch {
            repository.saveWebhookUrl(url.trim())
        }
    }

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(repository) as T
    }
}
