package nl.guido.foodtracker.feature.camera

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import nl.guido.foodtracker.feature.camera.read.LabelValues
import nl.guido.foodtracker.feature.camera.read.NutritionLabelParser
import nl.guido.foodtracker.feature.camera.read.readLabelText
import nl.guido.foodtracker.feature.camera.vision.toLines
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reads Guido's photos of nutrition labels with the phone's own text reader (ML Kit) and the label
 * reader, and scores them against the right values in labels/expected.csv.
 * The full photos are in /mnt/project-files/foodapp/camera/label-photos (not in the app).
 *
 * Every line ML Kit read is written to the log ("LabelLines", one line per text line), so the
 * computer tests (LabelPhotoLinesTest) can use exactly what the phone saw.
 *
 * Needs a phone or emulator: ./gradlew :feature:camera:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class LabelPhotosTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test fun readsGuidosLabels() {
        val expected = assets.open("labels/expected.csv").bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("file,") }
            .associate { row -> row.substringBefore(',') to row }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val report = expected.keys.sorted().map { file ->
            val photo = assets.open("labels/$file").use { BitmapFactory.decodeStream(it) }
            val upright = Tasks.await(recognizer.process(InputImage.fromBitmap(photo, 0))).toLines()
            upright.forEach { Log.i(TAG, "$file|0|${it.left.toInt()}|${it.top.toInt()}|${it.right.toInt()}|${it.bottom.toInt()}|${it.text}") }
            // What the app does: read again turned around when the first read finds nothing.
            val lines = readLabelText(upright) {
                Tasks.await(recognizer.process(InputImage.fromBitmap(photo, 180))).toLines().also { turned ->
                    turned.forEach { Log.i(TAG, "$file|180|${it.left.toInt()}|${it.top.toInt()}|${it.right.toInt()}|${it.bottom.toInt()}|${it.text}") }
                }
            }
            val got = NutritionLabelParser.parse(lines)
            val misses = misses(expected.getValue(file), got)
            "${if (misses.isEmpty()) "OK" else "MISS"} $file ${misses.joinToString(" ")}".trim() to misses.isEmpty()
        }
        recognizer.close()
        val okCount = report.count { it.second }
        val summary = "Label photos fully right: $okCount of ${report.size}  ##  ${report.joinToString("  ##  ") { it.first }}"
        Log.i(TAG, summary)
        assertTrue(summary, okCount == report.size)
    }

    /** "kcal=150/read 620" for each value that differs; empty when all right. */
    private fun misses(row: String, got: LabelValues): List<String> {
        val c = row.split(',')
        fun want(i: Int) = c.getOrNull(i)?.takeIf { it.isNotBlank() }?.toDouble()
        val pairs = listOf(
            "kcal" to (want(1) to got.kcal), "fat" to (want(2) to got.fat), "carbs" to (want(3) to got.carbs),
            "protein" to (want(4) to got.protein), "sugar" to (want(5) to got.sugar), "fibre" to (want(6) to got.fibre),
            "salt" to (want(7) to got.salt),
        )
        val wrong = pairs.filter { (_, v) -> v.first != v.second }.map { (name, v) -> "$name=${v.first}/read ${v.second}" }
        val perMl = c.getOrNull(8) == "yes"
        return if (perMl != got.perMl) wrong + "perMl=$perMl" else wrong
    }

    private companion object { const val TAG = "LabelLines" }
}
