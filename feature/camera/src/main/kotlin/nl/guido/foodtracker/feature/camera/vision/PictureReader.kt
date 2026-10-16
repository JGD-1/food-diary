package nl.guido.foodtracker.feature.camera.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import nl.guido.foodtracker.feature.camera.read.Picture
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Looks at the food itself with two small free models that run on the phone (LiteRT), nothing
 * uploaded: a starter model that knows about 20 fruits and vegetables (EfficientNet-Lite0) and one
 * that makes a fingerprint of the picture (MobileNet-V3 embedder), for comparing with your own
 * named examples. Both are Google's MediaPipe models (Apache 2.0) in this module's assets.
 */
internal class PictureReader(context: Context) : Closeable {
    private val classifier = Interpreter(asset(context, "food_classifier.tflite"))
    private val embedder = Interpreter(asset(context, "food_embedder.tflite"))
    private val classes = classifier.getOutputTensor(0).shape()[1]
    private val embeddingSize = embedder.getOutputTensor(0).shape()[1]

    /** Reads the middle square of [frame]; the frame stays open. */
    fun read(frame: ImageProxy): Picture = read(frame.toBitmap(), frame.imageInfo.rotationDegrees)

    fun read(bitmap: Bitmap, rotationDegrees: Int = 0): Picture {
        val pixels = middleSquare(bitmap, rotationDegrees)

        // The starter model takes plain 0-255 colour values and gives a score per kind in 256ths.
        val bytes = ByteBuffer.allocateDirect(SIZE * SIZE * 3).order(ByteOrder.nativeOrder())
        pixels.forEach { p -> bytes.put((p shr 16 and 0xFF).toByte()).put((p shr 8 and 0xFF).toByte()).put((p and 0xFF).toByte()) }
        bytes.rewind()
        val scores = Array(1) { ByteArray(classes) }
        classifier.run(bytes, scores)

        // The fingerprint model takes colours from 0 to 1.
        val floats = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
        pixels.forEach { p ->
            floats.putFloat((p shr 16 and 0xFF) / 255f).putFloat((p shr 8 and 0xFF) / 255f).putFloat((p and 0xFF) / 255f)
        }
        floats.rewind()
        val vector = Array(1) { FloatArray(embeddingSize) }
        embedder.run(floats, vector)

        return Picture(FloatArray(classes) { (scores[0][it].toInt() and 0xFF) / 256f }, vector[0])
    }

    override fun close() {
        classifier.close()
        embedder.close()
    }

    private companion object {
        const val SIZE = 224

        fun asset(context: Context, name: String): MappedByteBuffer =
            context.assets.openFd(name).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use {
                    it.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }

        /** The middle square, upright and 224 × 224, as ARGB pixels. */
        fun middleSquare(bitmap: Bitmap, rotationDegrees: Int): IntArray {
            val side = minOf(bitmap.width, bitmap.height)
            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
                postScale(SIZE.toFloat() / side, SIZE.toFloat() / side)
            }
            val square = Bitmap.createBitmap(
                bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side, matrix, true,
            )
            val pixels = IntArray(SIZE * SIZE)
            Bitmap.createScaledBitmap(square, SIZE, SIZE, true).getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            return pixels
        }
    }
}
