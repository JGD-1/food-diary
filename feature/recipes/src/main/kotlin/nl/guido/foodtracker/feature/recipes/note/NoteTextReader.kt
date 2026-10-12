package nl.guido.foodtracker.feature.recipes.note

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import nl.guido.foodtracker.feature.recipes.logic.SeenLine
import java.io.Closeable

/**
 * Reads the text lines in each camera frame for "Scan a note" (round 4, H), with Google ML Kit's
 * text reader built into the app: it works offline and nothing is uploaded. A frame that arrives
 * while one is still being read is dropped by the camera, so work never piles up.
 */
internal class NoteTextReader(private val onFrame: (List<SeenLine>) -> Unit) : ImageAnalysis.Analyzer, Closeable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(frame: ImageProxy) {
        val image = frame.image ?: return frame.close()
        recognizer.process(InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees))
            .addOnSuccessListener { text ->
                onFrame(
                    text.textBlocks.flatMap { block ->
                        block.lines.mapNotNull { line ->
                            line.boundingBox?.let { box -> SeenLine(line.text, box.top.toFloat(), box.bottom.toFloat()) }
                        }
                    },
                )
            }
            .addOnCompleteListener { frame.close() }
    }

    override fun close() = recognizer.close()
}
