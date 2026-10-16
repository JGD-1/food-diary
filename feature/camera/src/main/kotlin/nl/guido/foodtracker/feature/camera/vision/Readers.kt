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
import nl.guido.foodtracker.feature.camera.read.Gray
import nl.guido.foodtracker.feature.camera.read.SevenSegmentReader
import nl.guido.foodtracker.feature.camera.read.ScaleParse
import nl.guido.foodtracker.feature.camera.read.TextLine
import com.google.android.gms.tasks.Tasks
import java.io.Closeable

// All reading happens on the phone (Google ML Kit's built-in models, and our own scale reader). Nothing is uploaded.

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
 * Reads the number on the kitchen scale's display in each camera frame, and while weighing a few
 * times a second also looks for a barcode and the printed name on the pack ([onPack]), to guess
 * the food. [looking] says whether a guess is still wanted; when not, only the scale is read.
 *
 * Only the middle of the frame is used for the scale (where the aiming frame is). The digits are
 * read straight from their lit bars ([SevenSegmentReader]), in any direction the phone is held.
 * Frames that arrive while one is being read are dropped by the camera, so work never piles up.
 */
internal class WeighReader(
    private val onScale: (ScaleParse) -> Unit,
    private val onPack: (code: String?, lines: List<TextLine>) -> Unit,
    private val looking: () -> Boolean,
) : ImageAnalysis.Analyzer, Closeable {
    private val barcodes = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build(),
    )
    private val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastLook = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(frame: ImageProxy) {
        val gray = try {
            aimedGray(frame, frame.imageInfo.rotationDegrees)
        } catch (e: RuntimeException) {
            null
        }
        val now = System.currentTimeMillis()
        val image = frame.image
        if (image != null && looking() && now - lastLook >= LOOK_EVERY_MS) {
            lastLook = now
            // The frame stays open until both readers are done with it; the camera waits meanwhile.
            val input = InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees)
            val code = barcodes.process(input)
            val lines = text.process(input)
            Tasks.whenAllComplete(code, lines).addOnCompleteListener {
                frame.close()
                onPack(
                    if (code.isSuccessful) code.result.firstNotNullOfOrNull { it.rawValue } else null,
                    if (lines.isSuccessful) lines.result.toLines() else emptyList(),
                )
            }
        } else {
            frame.close()
        }
        val grams = gray?.let { runCatching { SevenSegmentReader.read(it) }.getOrNull() }
        onScale(if (grams != null && grams <= MAX_GRAMS) ScaleParse.Grams(grams.toDouble()) else ScaleParse.Unreadable)
    }

    override fun close() {
        barcodes.close()
        text.close()
    }

    private companion object {
        const val LOOK_EVERY_MS = 700L
        const val MAX_GRAMS = 15_000
    }
}

/**
 * The middle of a camera frame (the aiming frame: 80% wide, 45% high when upright) as a grey
 * picture, made smaller so reading it stays quick.
 */
internal fun aimedGray(frame: ImageProxy, rotationDegrees: Int, maxShortSide: Int = 400): Gray {
    val plane = frame.planes[0] // brightness (Y) plane
    val sideways = rotationDegrees % 180 != 0
    val cropW = (frame.width * if (sideways) 0.45 else 0.8).toInt()
    val cropH = (frame.height * if (sideways) 0.8 else 0.45).toInt()
    val left = (frame.width - cropW) / 2
    val top = (frame.height - cropH) / 2
    val step = maxOf(1, (minOf(cropW, cropH) + maxShortSide - 1) / maxShortSide)
    val w = cropW / step
    val h = cropH / step
    val buffer = plane.buffer
    val pixels = IntArray(w * h) { i ->
        val x = left + (i % w) * step
        val y = top + (i / w) * step
        buffer.get(y * plane.rowStride + x * plane.pixelStride).toInt() and 0xFF
    }
    return Gray(w, h, pixels)
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
