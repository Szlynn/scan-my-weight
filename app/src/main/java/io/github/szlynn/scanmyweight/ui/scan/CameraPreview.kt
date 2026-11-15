package io.github.szlynn.scanmyweight.ui.scan

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.Closeable

/**
 * Composable that hosts a CameraX [PreviewView] bound to the back camera.
 *
 * Camera binding is scoped to the composable's lifetime:
 * - Binding is established once per [lifecycleOwner] change inside a [DisposableEffect].
 * - Use cases are unbound when the composable leaves composition.
 * - [imageAnalyzer] changes update the analysis use case without triggering a camera rebind.
 *
 * @param imageAnalyzer Optional [ImageAnalysis.Analyzer]. Pass null to show preview only.
 *                      This preview owns the supplied analyzer: if it is [Closeable], it is
 *                      closed on replacement or removal. Do not share it between previews.
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
    val previewView = remember(context) { PreviewView(context) }

    val executor = remember { ContextCompat.getMainExecutor(context.applicationContext) }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }
    val currentOnError by rememberUpdatedState(onError)

    DisposableEffect(imageAnalysis, imageAnalyzer, executor) {
        if (imageAnalyzer != null) {
            imageAnalysis.setAnalyzer(executor, imageAnalyzer)
        }
        onDispose {
            imageAnalysis.clearAnalyzer()
            (imageAnalyzer as? Closeable)?.close()
        }
    }

    DisposableEffect(context, lifecycleOwner, previewView, imageAnalysis) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        var boundProvider: ProcessCameraProvider? = null
        // The listener may run after this effect has been disposed.
        var disposed = false

        cameraProviderFuture.addListener(
            {
                if (disposed) return@addListener

                val cameraProvider = runCatching { cameraProviderFuture.get() }
                    .onFailure { currentOnError(it) }
                    .getOrNull() ?: return@addListener

                boundProvider = cameraProvider
                runCatching {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                }.onFailure { currentOnError(it) }
            },
            executor,
        )

        onDispose {
            disposed = true
            runCatching {
                boundProvider?.unbind(preview, imageAnalysis)
            }
        }
    }

    key(previewView) {
        AndroidView(
            factory = { previewView },
            modifier = modifier,
        )
    }
}
