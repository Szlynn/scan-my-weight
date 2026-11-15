package io.github.szlynn.scanmyweight.ui.scan

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Composable that hosts a CameraX [PreviewView] bound to the back camera.
 *
 * Camera binding is scoped to the composable's lifetime:
 * - Binding is established once per [lifecycleOwner] change inside a [DisposableEffect].
 * - Only the acquired provider and owned use cases are unbound during disposal;
 *   disposal never waits on [ProcessCameraProvider.getInstance].
 * - [imageAnalyzer] changes update the [ImageAnalysis] use case without camera rebinding.
 *
 * @param imageAnalyzer Optional [ImageAnalysis.Analyzer]. Pass null to show preview only.
 * @param onError       Called on the main thread if camera provider initialisation fails.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    imageAnalyzer: ImageAnalysis.Analyzer? = null,
    onError: (Throwable) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val currentOnError by rememberUpdatedState(onError)

    // Resources acquired after the provider future completes.
    // Held in state so the analyzer effect and onDispose can access them.
    val acquiredProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val imageAnalysisUseCase = remember {
        mutableStateOf<ImageAnalysis?>(null)
    }

    // Update the analyzer on the existing ImageAnalysis use case whenever it changes,
    // independently of camera rebinding.
    val currentAnalyzer by rememberUpdatedState(imageAnalyzer)
    LaunchedEffect(imageAnalyzer) {
        val analysis = imageAnalysisUseCase.value ?: return@LaunchedEffect
        val executor = ContextCompat.getMainExecutor(context)
        if (currentAnalyzer != null) {
            analysis.setAnalyzer(executor, requireNotNull(currentAnalyzer))
        } else {
            analysis.clearAnalyzer()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false

        cameraProviderFuture.addListener(
            {
                if (disposed) return@addListener

                val provider = runCatching { cameraProviderFuture.get() }
                    .onFailure { currentOnError(it) }
                    .getOrNull() ?: return@addListener

                val preview = Preview.Builder().build().also { p ->
                    p.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { a ->
                        currentAnalyzer?.let { a.setAnalyzer(executor, it) }
                            ?: a.clearAnalyzer()
                    }

                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }.onFailure {
                    currentOnError(it)
                    return@addListener
                }

                acquiredProvider.value = provider
                imageAnalysisUseCase.value = analysis
            },
            executor,
        )

        onDispose {
            disposed = true
            acquiredProvider.value?.unbindAll()
            acquiredProvider.value = null
            imageAnalysisUseCase.value = null
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}
