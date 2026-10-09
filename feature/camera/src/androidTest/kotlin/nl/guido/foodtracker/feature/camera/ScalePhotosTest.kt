package nl.guido.foodtracker.feature.camera

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import nl.guido.foodtracker.feature.camera.read.ScaleDisplayParser
import nl.guido.foodtracker.feature.camera.read.ScaleParse
import nl.guido.foodtracker.feature.camera.vision.ScaleReader
import nl.guido.foodtracker.feature.camera.vision.toBitmap
import nl.guido.foodtracker.feature.camera.vision.toGray
import nl.guido.foodtracker.feature.camera.vision.toLines
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real scale reader (ML Kit + our clean-up + our parser) on Guido's photos of their
 * kitchen scale (KitchenBrothers, white segment digits on black glass), cut to what the camera sees
 * when the display is in the aiming frame. File names hold the right answer: "03_669g.jpg".
 * The full photos are in /mnt/project-files/foodapp/camera/scale-photos (not in the app).
 *
 * Needs a phone or emulator: ./gradlew :feature:camera:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ScalePhotosTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @After fun tearDown() = recognizer.close()

    @Test fun readsGuidosScale() {
        val report = assets.list("scale-aimed")!!.sorted().map { file ->
            val expected = ScaleParse.Grams(Regex("_(\\d+)g").find(file)!!.groupValues[1].toDouble())
            val gray = assets.open("scale-aimed/$file").use { BitmapFactory.decodeStream(it) }.toGray()
            // Every way the live reader tries, in the same order; the live reader keeps the first that reads a number.
            val tries = ScaleReader.ATTEMPTS.map { (prepare, turn) ->
                val lines = Tasks.await(recognizer.process(InputImage.fromBitmap(prepare(gray).toBitmap(), turn))).toLines()
                ScaleDisplayParser.parse(lines) to lines.joinToString("|") { it.text }
            }
            val first = tries.indexOfFirst { it.first != ScaleParse.Unreadable }
            val ok = first >= 0 && tries[first].first == expected
            val allRight = tries.indices.filter { tries[it].first == expected }
            val line = "${if (ok) "OK" else "MISS"} $file first=${tries.getOrNull(first)?.first} way=$first " +
                "rightWays=$allRight text=[${tries.getOrNull(first)?.second}]"
            line to ok
        }
        val okCount = report.count { it.second }
        // One line, so the build log shows every photo (it cuts failure messages after the first line).
        assertTrue("Scale photos read right: $okCount of ${report.size}  ##  ${report.joinToString("  ##  ") { it.first }}", okCount == report.size)
    }
}
