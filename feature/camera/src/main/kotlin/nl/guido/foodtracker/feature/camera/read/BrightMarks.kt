package nl.guido.foodtracker.feature.camera.read

import kotlin.math.max
import kotlin.math.roundToInt

/** A grey picture: one brightness value (0–255) per pixel, row by row. */
class Gray(val width: Int, val height: Int, val pixels: IntArray) {
    init { require(pixels.size == width * height) }
}

/**
 * Picks out small bright marks, such as the lit bars of a scale display, and ignores big bright
 * areas such as the worktop around the scale. Plain Kotlin so it can be tested without a phone.
 */
object BrightMarks {
    /**
     * How much brighter each pixel is than its surroundings, counting only marks narrower than
     * [markSize] (a share of the picture's short side).
     */
    fun marks(image: Gray, markSize: Double): Gray {
        val size = odd(minOf(image.width, image.height) * markSize)
        val background = maxFilter(minFilter(image, size), size)
        return Gray(image.width, image.height, IntArray(image.pixels.size) { (image.pixels[it] - background.pixels[it]).coerceAtLeast(0) })
    }

    /** How bright a mark must be to count: [share] of the brightest marks, and never very faint. */
    fun cut(marks: Gray, share: Double): Int = max(25, (percentile(marks.pixels, 0.995) * share).roundToInt())

    /** Which pixels are lit (true), without single specks of dust or reflections. */
    fun lit(marks: Gray, cut: Int): BooleanArray {
        val on = Gray(marks.width, marks.height, IntArray(marks.pixels.size) { if (marks.pixels[it] >= cut) 255 else 0 })
        val cleaned = maxFilter(minFilter(on, 3), 3)
        return BooleanArray(cleaned.pixels.size) { cleaned.pixels[it] > 0 }
    }

    private fun odd(v: Double) = max(3, v.roundToInt() or 1)

    private fun percentile(values: IntArray, p: Double): Int {
        val counts = IntArray(256)
        values.forEach { counts[it.coerceIn(0, 255)]++ }
        val target = (values.size * p).toLong()
        var seen = 0L
        for (v in 0..255) { seen += counts[v]; if (seen >= target) return v }
        return 255
    }

    private fun minFilter(img: Gray, size: Int) = filter(img, size) { a, b -> minOf(a, b) }
    private fun maxFilter(img: Gray, size: Int) = filter(img, size) { a, b -> maxOf(a, b) }

    /** Square min/max filter, done as a row pass and a column pass. */
    private inline fun filter(img: Gray, size: Int, pick: (Int, Int) -> Int): Gray {
        val w = img.width
        val h = img.height
        val r = size / 2
        val rows = IntArray(w * h)
        for (y in 0 until h) {
            val base = y * w
            for (x in 0 until w) {
                var v = img.pixels[base + x]
                for (dx in maxOf(0, x - r)..minOf(w - 1, x + r)) v = pick(v, img.pixels[base + dx])
                rows[base + x] = v
            }
        }
        val out = IntArray(w * h)
        for (x in 0 until w) {
            for (y in 0 until h) {
                var v = rows[y * w + x]
                for (dy in maxOf(0, y - r)..minOf(h - 1, y + r)) v = pick(v, rows[dy * w + x])
                out[y * w + x] = v
            }
        }
        return Gray(w, h, out)
    }
}
