package io.github.szlynn.scanmyweight.ui.scan

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
 * - Use cases are unbound when the composable leaves composition.
 * - [imageAnalyzer] changes update the analysis use case without triggering a camera rebind.
 *
 * @param imageAnalyzer Optional [ImageAnalysis.Analyzer]. Pass null to show preview only.
 * @param onError       Called on the main thread if the camera provider fails to initialise.
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

    // Keep a stable reference to the latest analyzer and error handler without
    // restarting the DisposableEffect when they change.
    val currentAnalyzer by rememberUpdatedState(imageAnalyzer)
    val currentOnError by rememberUpdatedState(onError)

    DisposableEffect(lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        // Guard against disposal before the future completes.
        var disposed = false

        cameraProviderFuture.addListener(
            {
                if (disposed) return@addListener

                val cameraProvider = runCatching { cameraProviderFuture.get() }
                    .onFailure { currentOnError(it) }
                    .getOrNull() ?: return@addListener

                val preview = Preview.Builder().build().also { p ->
                    p.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = currentAnalyzer?.let { analyzer ->
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor, analyzer) }
                }

                val useCases = listOfNotNull(preview, imageAnalysis).toTypedArray()

                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        *useCases,
                    )
                }.onFailure { currentOnError(it) }
            },
            executor,
        )

        onDispose {
            disposed = true
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}
