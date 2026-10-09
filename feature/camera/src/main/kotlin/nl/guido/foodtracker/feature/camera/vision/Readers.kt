package nl.guido.foodtracker.feature.camera.vision

import android.graphics.Bitmap
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
import nl.guido.foodtracker.feature.camera.read.ScaleDisplayParser
import nl.guido.foodtracker.feature.camera.read.SegmentJoiner
import nl.guido.foodtracker.feature.camera.read.ScaleParse
import nl.guido.foodtracker.feature.camera.read.TextLine
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

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
 *
 * Only the middle of the frame is used (where the aiming frame is). Scale digits are made of
 * separate bars, so the picture is first cleaned up into solid digits ([SegmentJoiner]); the plain
 * picture is tried too. People also hold the phone sideways or upside down to the scale. When a
 * frame shows no number, the next frame tries the next way ([ATTEMPTS]); a way that works is kept.
 */
internal class ScaleReader(private val onFrame: (ScaleParse) -> Unit) : ImageAnalysis.Analyzer, Closeable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    @Volatile private var attempt = 0
    private val busy = AtomicBoolean(false)

    override fun analyze(frame: ImageProxy) {
        // Skip frames while the previous one is still being read, so work never piles up.
        if (!busy.compareAndSet(false, true)) return frame.close()
        val (prepare, turn) = ATTEMPTS[attempt]
        val rotation = (frame.imageInfo.rotationDegrees + turn) % 360
        val picture = try {
            prepare(aimedGray(frame, frame.imageInfo.rotationDegrees)).toBitmap()
        } catch (e: RuntimeException) {
            busy.set(false)
            return
        } finally {
            frame.close()
        }
        recognizer.process(InputImage.fromBitmap(picture, rotation))
            .addOnSuccessListener { text ->
                val parse = ScaleDisplayParser.parse(text.toLines())
                if (parse == ScaleParse.Unreadable) attempt = (attempt + 1) % ATTEMPTS.size
                onFrame(parse)
            }
            .addOnCompleteListener { busy.set(false) }
    }

    override fun close() = recognizer.close()

    companion object {
        /** Ways to prepare the picture, best first. Shared with the photo test. */
        val PREPARE: List<(Gray) -> Gray> = listOf(
            { SegmentJoiner.join(it, thicken = 0.021) },
            { SegmentJoiner.join(it, thicken = 0.035) },
            { it },
        )
        val TURNS = intArrayOf(0, 90, 270, 180)
        val ATTEMPTS = TURNS.flatMap { turn -> PREPARE.map { it to turn } }
    }
}

/**
 * The middle of a camera frame (the aiming frame: 80% wide, 45% high when upright) as a grey
 * picture, made smaller so cleaning it up stays quick.
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

internal fun Gray.toBitmap(): Bitmap =
    Bitmap.createBitmap(IntArray(pixels.size) { val v = pixels[it]; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }, width, height, Bitmap.Config.ARGB_8888)

internal fun Bitmap.toGray(): Gray {
    val argb = IntArray(width * height)
    getPixels(argb, 0, width, 0, 0, width, height)
    return Gray(width, height, IntArray(argb.size) { i ->
        val c = argb[i]
        (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
    })
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
