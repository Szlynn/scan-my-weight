package io.github.szlynn.scanmyweight.ocr

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable

/**
 * CameraX [ImageAnalysis.Analyzer] that runs ML Kit on-device text recognition on each frame
 * and delivers a [OcrParser.ParseResult] to [onResult].
 *
 * Frames are submitted to ML Kit on the CameraX executor thread. [onResult] is invoked on the
 * main application thread, because ML Kit task listeners with no explicit executor default to
 * the main thread.
 *
 * Call [close] when the analyzer is no longer needed (e.g. when the scan session ends or the
 * analyzer is replaced) to release the underlying [com.google.mlkit.vision.text.TextRecognizer].
 * [ImageProxy.close] is called separately inside [analyze] and does not affect the recognizer.
 */
class MlKitTextAnalyzer(
    private val onResult: (OcrParser.ParseResult) -> Unit,
) : ImageAnalysis.Analyzer, Closeable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.textBlocks.joinToString("\n") { it.text }
                onResult(OcrParser.parse(rawText))
            }
            .addOnFailureListener {
                // On failure emit an empty result so the state machine simply sees no data
                // for this frame rather than stalling.
                onResult(OcrParser.ParseResult())
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /**
     * Releases the [com.google.mlkit.vision.text.TextRecognizer].
     * Call this when the scan session ends or this analyzer is replaced.
     */
    override fun close() {
        recognizer.close()
    }
}
