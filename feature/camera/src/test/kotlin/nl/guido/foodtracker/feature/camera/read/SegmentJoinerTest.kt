package nl.guido.foodtracker.feature.camera.read

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentJoinerTest {
    /** 400×300 dark picture with bright rectangles (left, top, right, bottom). */
    private fun picture(vararg boxes: IntArray, brightness: Int = 220): Gray {
        val w = 400
        val h = 300
        val px = IntArray(w * h) { 30 }
        for (b in boxes) for (y in b[1] until b[3]) for (x in b[0] until b[2]) px[y * w + x] = brightness
        return Gray(w, h, px)
    }

    private fun Gray.at(x: Int, y: Int) = pixels[y * width + x]

    @Test fun `two segments with a small gap become one dark stroke`() {
        // A top bar and a right bar of a "7", 3 pixels apart.
        val out = SegmentJoiner.join(picture(intArrayOf(60, 40, 90, 44), intArrayOf(93, 46, 97, 80)), thicken = 0.025)
        assertEquals(0, out.at(75, 42))  // the bar is dark
        assertEquals(0, out.at(91, 45))  // the gap is closed
        assertEquals(255, out.at(20, 250)) // empty glass stays white
    }

    @Test fun `a broad bright area such as the worktop is left out`() {
        val out = SegmentJoiner.join(picture(intArrayOf(0, 0, 400, 100), intArrayOf(60, 200, 100, 204)), thicken = 0.03)
        assertEquals(255, out.at(200, 50)) // inside the broad area
        assertEquals(0, out.at(80, 202))    // the thin segment
    }
}
