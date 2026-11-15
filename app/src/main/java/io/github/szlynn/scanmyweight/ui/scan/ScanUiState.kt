package io.github.szlynn.scanmyweight.ui.scan

/**
 * Per-metric capture state shown in the UI.
 */
enum class MetricCaptureState { WAITING, LOCKED }

/**
 * Overall state of the scan session.
 */
enum class ScanSessionState { SCANNING, COMPLETE, UPLOADING, UPLOAD_ERROR }

/**
 * Full UI state for the scan screen.
 *
 * @param sessionState      Overall progress of the current scan session.
 * @param weightState       Capture state of the weight metric.
 * @param bfState           Capture state of the body fat metric.
 * @param bwState           Capture state of the body water metric.
 * @param bmState           Capture state of the body muscle metric.
 * @param hasCameraPermission Whether the CAMERA permission has been granted.
 * @param cameraError       Non-null if the camera failed to initialise.
 * @param uploadErrorMessage Non-null error message shown after a failed upload.
 */
data class ScanUiState(
    val sessionState: ScanSessionState = ScanSessionState.SCANNING,
    val weightState: MetricCaptureState = MetricCaptureState.WAITING,
    val bfState: MetricCaptureState = MetricCaptureState.WAITING,
    val bwState: MetricCaptureState = MetricCaptureState.WAITING,
    val bmState: MetricCaptureState = MetricCaptureState.WAITING,
    val hasCameraPermission: Boolean = false,
    val cameraError: String? = null,
    val uploadErrorMessage: String? = null,
)
