package nl.guido.foodtracker.feature.camera

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import nl.guido.foodtracker.feature.camera.read.ScaleDisplayParser
import nl.guido.foodtracker.feature.camera.read.ScaleParse
import nl.guido.foodtracker.feature.camera.vision.ScaleReader
import nl.guido.foodtracker.feature.camera.vision.toLines
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real scale reader (ML Kit + our parser) on Guido's photos of their kitchen scale
 * (KitchenBrothers, white segment digits on black glass). File names hold the right answer: "03_669g.jpg".
 *
 * - scale-aimed/: the same photos cut to what the camera sees when the display is in the aiming
 *   frame, as the Scan screen asks. These must all be read right.
 * - scale/: the whole photos, taken from further away. Reported only, to see how much aiming matters.
 *
 * Needs a phone or emulator: ./gradlew :feature:camera:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class ScalePhotosTest {
    private val assets: AssetManager = InstrumentationRegistry.getInstrumentation().context.assets
    private val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @After fun tearDown() = recognizer.close()

    @Test fun readsGuidosScaleWhenAimed() {
        val far = readFolder("scale")
        Log.i("ScalePhotos", "Whole photos: ${far.count { it.second }} of ${far.size}  ##  ${far.joinToString("  ##  ") { it.first }}")
        val aimed = readFolder("scale-aimed")
        // One line, so the build log shows every photo (it cuts failure messages after the first line).
        val okCount = aimed.count { it.second }
        assertTrue(
            "Aimed photos read right: $okCount of ${aimed.size} (whole photos: ${far.count { it.second }} of ${far.size})" +
                "  ##  ${aimed.joinToString("  ##  ") { it.first }}  ##  WHOLE  ##  ${far.joinToString("  ##  ") { it.first }}",
            okCount == aimed.size,
        )
    }

    private fun readFolder(folder: String): List<Pair<String, Boolean>> = assets.list(folder)!!.sorted().map { file ->
        val expected = Regex("_(\\d+)g").find(file)!!.groupValues[1].toDouble()
        val bitmap = assets.open("$folder/$file").use { BitmapFactory.decodeStream(it) }
        // Same turns the live reader tries when the phone is held sideways.
        val read = ScaleReader.EXTRA_TURNS.asSequence().map { turn ->
            val lines = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, turn))).toLines()
            Triple(ScaleDisplayParser.parse(lines), turn, lines.joinToString(" | ") { it.text })
        }.firstOrNull { it.first != ScaleParse.Unreadable } ?: Triple(ScaleParse.Unreadable, 0, "")
        val ok = read.first == ScaleParse.Grams(expected)
        "${if (ok) "OK" else "MISS"} $file read=${read.first} turn=${read.second} text=[${read.third}]" to ok
    }
}
