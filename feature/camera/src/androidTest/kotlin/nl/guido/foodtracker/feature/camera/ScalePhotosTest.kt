package nl.guido.foodtracker.feature.camera

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import nl.guido.foodtracker.feature.camera.read.ScaleDisplayParser
import nl.guido.foodtracker.feature.camera.read.ScaleParse
import nl.guido.foodtracker.feature.camera.vision.ScaleReader
import nl.guido.foodtracker.feature.camera.vision.toLines
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real scale reader (ML Kit + our parser) on Guido's photos of their kitchen scale
 * (KitchenBrothers, white digits on black glass). File names hold the right answer: "03_669g.jpg".
 * Needs a phone or emulator: ./gradlew :feature:camera:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ScalePhotosTest {
    @Test fun readsGuidosScale() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val report = assets.list("scale")!!.sorted().map { file ->
            val expected = Regex("_(\\d+)g").find(file)!!.groupValues[1].toDouble()
            val bitmap = assets.open("scale/$file").use { BitmapFactory.decodeStream(it) }
            // Same turns the live reader tries when the phone is held sideways.
            val read = ScaleReader.EXTRA_TURNS.asSequence().map { turn ->
                val lines = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, turn))).toLines()
                ScaleDisplayParser.parse(lines) to lines.joinToString(" | ") { it.text }
            }.firstOrNull { it.first != ScaleParse.Unreadable } ?: (ScaleParse.Unreadable to "")
            val ok = read.first == ScaleParse.Grams(expected)
            "${if (ok) "OK  " else "MISS"} $file read=${read.first} text=[${read.second}]".also { Log.i("ScalePhotos", it) } to ok
        }
        recognizer.close()
        // One line, so the build log shows every photo (it cuts failure messages after the first line).
        val summary = report.joinToString("  ##  ") { it.first }
        val okCount = report.count { it.second }
        assertTrue("Scale photos read correctly: $okCount of ${report.size}  ##  $summary", okCount == report.size)
    }
}
