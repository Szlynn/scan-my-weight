package io.github.szlynn.scanmyweight.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.szlynn.scanmyweight.data.WeightMeasurement
import io.github.szlynn.scanmyweight.data.SheetsRepository
import io.github.szlynn.scanmyweight.ocr.OcrParser
import io.github.szlynn.scanmyweight.ocr.ScanStateMachine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ScanViewModel(private val sheetsRepository: SheetsRepository) : ViewModel() {

    private val stateMachine = ScanStateMachine()

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    // Stable for the locked measurement, including retries; cleared on reset.
    private var measurementId: String? = null

    // Callback invoked on the main thread by MlKitTextAnalyzer.
    fun onParseResult(result: OcrParser.ParseResult) {
        if (_uiState.value.sessionState != ScanSessionState.SCANNING) return

        val machineState = stateMachine.process(result)

        _uiState.update { current ->
            current.copy(
                weightState = if (machineState.weight.isLocked) MetricCaptureState.LOCKED else MetricCaptureState.WAITING,
                bfState = if (machineState.bf.isLocked) MetricCaptureState.LOCKED else MetricCaptureState.WAITING,
                bwState = if (machineState.bw.isLocked) MetricCaptureState.LOCKED else MetricCaptureState.WAITING,
                bmState = if (machineState.bm.isLocked) MetricCaptureState.LOCKED else MetricCaptureState.WAITING,
            )
        }

        if (machineState.isComplete) {
            val measurement = stateMachine.lockedMeasurement ?: return
            _uiState.update { it.copy(sessionState = ScanSessionState.UPLOADING) }
            measurementId = UUID.randomUUID().toString()
            upload(measurement)
        }
    }

    private fun upload(measurement: WeightMeasurement) {
        val id = measurementId ?: return
        viewModelScope.launch {
            sheetsRepository.upload(measurement, id).fold(
                onSuccess = {
                    _uiState.update { it.copy(sessionState = ScanSessionState.COMPLETE) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            sessionState = ScanSessionState.UPLOAD_ERROR,
                            uploadErrorMessage = error.message,
                        )
                    }
                },
            )
        }
    }

    fun onCameraPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted) }
    }

    fun onCameraError(error: Throwable) {
        _uiState.update { it.copy(cameraError = error.message) }
    }

    fun retryUpload() {
        if (_uiState.value.sessionState != ScanSessionState.UPLOAD_ERROR) return
        val measurement = stateMachine.lockedMeasurement ?: return
        _uiState.update { it.copy(sessionState = ScanSessionState.UPLOADING, uploadErrorMessage = null) }
        upload(measurement)
    }

    fun resetScan() {
        if (_uiState.value.sessionState == ScanSessionState.UPLOADING) return
        measurementId = null
        stateMachine.reset()
        _uiState.value = ScanUiState(hasCameraPermission = _uiState.value.hasCameraPermission)
    }

    class Factory(private val sheetsRepository: SheetsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScanViewModel(sheetsRepository) as T
    }
}
