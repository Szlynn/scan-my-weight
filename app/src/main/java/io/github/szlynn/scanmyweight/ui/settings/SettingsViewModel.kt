package io.github.szlynn.scanmyweight.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.szlynn.scanmyweight.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val webhookUrl: String = "",
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val isSaved: Boolean = false,
)

sealed interface SettingsEvent {
    data object SaveRequested : SettingsEvent
}

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    // Incrementing this triggers a fresh subscription to the repository flow.
    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState> = retryTrigger
        .flatMapLatest {
            repository.webhookUrl
                .map { result ->
                    result.fold(
                        onSuccess = { url -> SettingsUiState(webhookUrl = url, isLoading = false) },
                        onFailure = { SettingsUiState(isLoading = false, loadError = true) },
                    )
                }
                .catch { emit(SettingsUiState(isLoading = false, loadError = true)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState(),
        )

    fun retryLoad() {
        retryTrigger.value++
    }

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
