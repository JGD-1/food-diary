package nl.guido.foodtracker.feature.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import nl.guido.foodtracker.feature.camera.read.Gray
import nl.guido.foodtracker.feature.camera.read.SevenSegmentReader
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the scale reader on Guido's photos of their kitchen scale (KitchenBrothers, white segment
 * digits on black glass), cut to what the camera sees when the display is in the aiming frame, and
 * decoded by Android itself. File names hold the right answer: "03_669g.jpg".
 * The full photos are in /mnt/project-files/foodapp/camera/scale-photos (not in the app).
 * The same photos are also checked on the computer (SevenSegmentReaderTest).
 *
 * Needs a phone or emulator: ./gradlew :feature:camera:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ScalePhotosTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test fun readsGuidosScale() {
        val report = assets.list("scale-aimed")!!.sorted().map { file ->
            val expected = Regex("_(\\d+)g").find(file)!!.groupValues[1].toInt()
            val photo = assets.open("scale-aimed/$file").use { BitmapFactory.decodeStream(it) }
            val got = SevenSegmentReader.read(photo.toGray(maxShortSide = 400))
            "${if (got == expected) "OK" else "MISS"} $file read=$got" to (got == expected)
        }
        val okCount = report.count { it.second }
        // One line, so the build log shows every photo (it cuts failure messages after the first line).
        assertTrue("Scale photos read right: $okCount of ${report.size}  ##  ${report.joinToString("  ##  ") { it.first }}", okCount == report.size)
    }

    /** Grey picture, made smaller the way the live camera does. */
    private fun Bitmap.toGray(maxShortSide: Int): Gray {
        val step = maxOf(1, (minOf(width, height) + maxShortSide - 1) / maxShortSide)
        val w = width / step
        val h = height / step
        val argb = IntArray(width * height)
        getPixels(argb, 0, width, 0, 0, width, height)
        return Gray(w, h, IntArray(w * h) { i ->
            val c = argb[(i / w) * step * width + (i % w) * step]
            (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
        })
    }
}
