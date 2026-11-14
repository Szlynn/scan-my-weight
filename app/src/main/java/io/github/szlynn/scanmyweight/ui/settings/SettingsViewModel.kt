package io.github.szlynn.scanmyweight.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.szlynn.scanmyweight.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

data class SettingsUiState(
    val webhookUrl: String = "",
    val isLoading: Boolean = true,
    val loadError: Boolean = false,
    val isSaveError: Boolean = false,
    val isSaved: Boolean = false,
)

sealed interface SettingsEvent {
    data object SaveRequested : SettingsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    // Incrementing this triggers a fresh subscription to the repository flow.
    private val retryTrigger = MutableStateFlow(0)
    private val saveErrorState = MutableStateFlow(false)
    private val savedState = MutableStateFlow(false)

    private val loadState = retryTrigger
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

    val uiState: StateFlow<SettingsUiState> = combine(
        loadState,
        saveErrorState,
        savedState,
    ) { load, isSaveError, isSaved ->
        load.copy(isSaveError = isSaveError, isSaved = isSaved)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun retryLoad() {
        retryTrigger.value++
    }

    fun clearSaveError() {
        saveErrorState.value = false
    }

    fun saveWebhookUrl(url: String) {
        viewModelScope.launch {
            saveErrorState.value = false
            savedState.value = false
            try {
                repository.saveWebhookUrl(url.trim())
                savedState.value = true
            } catch (e: IOException) {
                saveErrorState.value = true
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(repository) as T
    }
}
