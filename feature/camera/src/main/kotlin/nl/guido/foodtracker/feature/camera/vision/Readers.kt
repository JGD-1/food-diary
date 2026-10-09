package nl.guido.foodtracker.feature.camera.vision

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import nl.guido.foodtracker.feature.camera.read.ScaleDisplayParser
import nl.guido.foodtracker.feature.camera.read.ScaleParse
import nl.guido.foodtracker.feature.camera.read.TextLine
import java.io.Closeable

// All reading happens on the phone with Google ML Kit's built-in models. Nothing is uploaded.

/** Looks for shop barcodes in each camera frame. */
internal class BarcodeReader(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer, Closeable {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(frame: ImageProxy) {
        val image = frame.image ?: return frame.close()
        scanner.process(InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onCode) }
            .addOnCompleteListener { frame.close() }
    }

    override fun close() = scanner.close()
}

/**
 * Reads the number on the kitchen scale's display in each camera frame.
 * People often hold the phone sideways to the scale, so when a frame shows no number the
 * next frames are also tried turned a quarter left, right and upside down; once a number is found that turn is kept.
 */
internal class ScaleReader(private val onFrame: (ScaleParse) -> Unit) : ImageAnalysis.Analyzer, Closeable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var turn = 0

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(frame: ImageProxy) {
        val image = frame.image ?: return frame.close()
        val rotation = (frame.imageInfo.rotationDegrees + EXTRA_TURNS[turn]) % 360
        recognizer.process(InputImage.fromMediaImage(image, rotation))
            .addOnSuccessListener { text ->
                val parse = ScaleDisplayParser.parse(text.toLines())
                if (parse == ScaleParse.Unreadable) turn = (turn + 1) % EXTRA_TURNS.size
                onFrame(parse)
            }
            .addOnCompleteListener { frame.close() }
    }

    override fun close() = recognizer.close()

    companion object {
        val EXTRA_TURNS = intArrayOf(0, 90, 270, 180)
    }
}

/** Reads all text on one photo (the nutrition label). */
internal class LabelReader : Closeable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Calls [onDone] with the lines found; an empty list when reading failed. Closes [photo]. */
    @OptIn(ExperimentalGetImage::class)
    fun read(photo: ImageProxy, onDone: (List<TextLine>) -> Unit) {
        val image = photo.image ?: run { photo.close(); return onDone(emptyList()) }
        recognizer.process(InputImage.fromMediaImage(image, photo.imageInfo.rotationDegrees))
            .addOnSuccessListener { text -> onDone(text.toLines()) }
            .addOnFailureListener { onDone(emptyList()) }
            .addOnCompleteListener { photo.close() }
    }

    override fun close() = recognizer.close()
}

internal fun Text.toLines(): List<TextLine> = textBlocks.flatMap { block ->
    block.lines.mapNotNull { line ->
        line.boundingBox?.let { box ->
            TextLine(line.text, box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
        }
    }
}
