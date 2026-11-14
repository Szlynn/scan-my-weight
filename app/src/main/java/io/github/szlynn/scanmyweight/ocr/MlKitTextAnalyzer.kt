package io.github.szlynn.scanmyweight.ocr

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * CameraX [ImageAnalysis.Analyzer] that runs ML Kit on-device text recognition on each frame
 * and delivers a [OcrParser.ParseResult] to [onResult].
 *
 * Frames are processed on the executor provided by CameraX. [onResult] is called on that
 * same thread — the caller is responsible for switching to the main thread if needed.
 */
class MlKitTextAnalyzer(
    private val onResult: (OcrParser.ParseResult) -> Unit,
) : ImageAnalysis.Analyzer {

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
}
