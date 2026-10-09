package nl.guido.foodtracker.feature.camera.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin

class SevenSegmentReaderTest {

    /**
     * Guido's photos of their kitchen scale (KitchenBrothers, white segment digits on black glass),
     * cut to what the camera sees in the aiming frame. File names hold the right answer: "03_669g.jpg".
     * Made smaller the way the live camera does (short side at most 400 pixels).
     */
    @Test fun `reads Guidos scale photos`() {
        val folder = File(javaClass.classLoader!!.getResource("scale-aimed")!!.toURI())
        val report = folder.listFiles()!!.filter { it.extension == "jpg" }.sorted().map { file ->
            val expected = Regex("_(\\d+)g").find(file.name)!!.groupValues[1].toInt()
            val got = SevenSegmentReader.read(load(file, maxShortSide = 400))
            Triple(file.name, expected, got)
        }
        val wrong = report.filter { it.second != it.third }
        assertEquals("Misread: ${wrong.joinToString { "${it.first} read as ${it.third}" }}", emptyList<Any>(), wrong)
        assertEquals(13, report.size)
    }

    @Test fun `reads a display in every direction`() {
        for (turn in listOf(0, 90, 180, 270, 25, -40)) {
            assertEquals("turned $turn°", 1053, SevenSegmentReader.read(display("1053", turn.toDouble())))
        }
    }

    @Test fun `tells 6 and 9 apart when upside down`() {
        assertEquals(669, SevenSegmentReader.read(display("669", 180.0)))
        assertEquals(189, SevenSegmentReader.read(display("189", 180.0)))
    }

    @Test fun `reads every digit`() {
        assertEquals(1234, SevenSegmentReader.read(display("1234", 0.0)))
        assertEquals(5678, SevenSegmentReader.read(display("5678", 0.0)))
        assertEquals(90, SevenSegmentReader.read(display("90", 0.0)))
        assertEquals(0, SevenSegmentReader.read(display("0", 0.0)))
    }

    @Test fun `an empty picture has no number`() =
        assertNull(SevenSegmentReader.read(Gray(300, 200, IntArray(300 * 200) { 12 })))

    private fun load(file: File, maxShortSide: Int): Gray {
        val img = ImageIO.read(file)
        val step = maxOf(1, (minOf(img.width, img.height) + maxShortSide - 1) / maxShortSide)
        val w = img.width / step
        val h = img.height / step
        return Gray(w, h, IntArray(w * h) { i ->
            val rgb = img.getRGB((i % w) * step, (i / w) * step)
            (((rgb shr 16) and 255) * 299 + ((rgb shr 8) and 255) * 587 + (rgb and 255) * 114) / 1000
        })
    }

    /** Which bars (a..g) each digit lights. */
    private val segments = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abdeg", '3' to "abcdg", '4' to "bcfg",
        '5' to "acdfg", '6' to "acdefg", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg",
    )

    /** Draws a scale display showing [digits] plus a small "g" sign, turned by [degrees]. */
    private fun display(digits: String, degrees: Double): Gray {
        val w = 400
        val h = 300
        val pixels = IntArray(w * h) { 15 }
        val pitch = 40.0 // from one digit to the next
        // Each bar: centre x, centre y, half its size across, half its size down. Bars don't touch.
        val rects = mutableListOf<DoubleArray>()
        digits.forEachIndexed { i, ch ->
            val cx = (i - (digits.length - 1) / 2.0) * pitch
            for (s in segments.getValue(ch)) {
                rects += when (s) {
                    'a' -> doubleArrayOf(cx, -25.0, 7.0, 3.0)
                    'g' -> doubleArrayOf(cx, 0.0, 7.0, 3.0)
                    'd' -> doubleArrayOf(cx, 25.0, 7.0, 3.0)
                    'b' -> doubleArrayOf(cx + 12, -12.5, 3.0, 7.5)
                    'c' -> doubleArrayOf(cx + 12, 12.5, 3.0, 7.5)
                    'f' -> doubleArrayOf(cx - 12, -12.5, 3.0, 7.5)
                    else -> doubleArrayOf(cx - 12, 12.5, 3.0, 7.5) // e
                }
            }
        }
        // The unit sign: a small blob just right of the last digit, at the top.
        rects += doubleArrayOf((digits.length - 1) / 2.0 * pitch + 23, -22.0, 3.0, 3.0)
        val t = Math.toRadians(degrees)
        for (y in 0 until h) for (x in 0 until w) {
            // Turn the pixel back into the display's own directions.
            val px = x - w / 2.0
            val py = y - h / 2.0
            val dx = px * cos(t) + py * sin(t)
            val dy = -px * sin(t) + py * cos(t)
            if (rects.any { r -> kotlin.math.abs(dx - r[0]) <= r[2] && kotlin.math.abs(dy - r[1]) <= r[3] }) pixels[y * w + x] = 230
        }
        return Gray(w, h, pixels)
    }
}
