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
 * Reads the number on the kitchen scale's display in each camera frame.
 *
 * Only the middle of the frame is used (where the aiming frame is). The digits are read straight
 * from their lit bars ([SevenSegmentReader]), in any direction the phone is held. Frames that
 * arrive while one is being read are dropped by the camera, so work never piles up.
 */
internal class ScaleReader(private val onFrame: (ScaleParse) -> Unit) : ImageAnalysis.Analyzer {
    override fun analyze(frame: ImageProxy) {
        val grams = try {
            SevenSegmentReader.read(aimedGray(frame, frame.imageInfo.rotationDegrees))
        } catch (e: RuntimeException) {
            null
        } finally {
            frame.close()
        }
        onFrame(if (grams != null && grams <= MAX_GRAMS) ScaleParse.Grams(grams.toDouble()) else ScaleParse.Unreadable)
    }

    private companion object {
        /** More than a kitchen scale can weigh: a misreading. */
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
