package nl.guido.foodtracker.feature.camera.read

import kotlin.math.max
import kotlin.math.roundToInt

/** A grey picture: one brightness value (0–255) per pixel, row by row. */
class Gray(val width: Int, val height: Int, val pixels: IntArray) {
    init { require(pixels.size == width * height) }
}

/**
 * Prepares a kitchen scale display for text recognition.
 *
 * Scale digits are made of separate bars (segments) with small gaps between them. General text
 * recognition then misreads them: a 7 whose top bar stands apart becomes a 1, a 9 becomes a 3.
 * This keeps only thin bright marks (the lit segments, not the bright worktop around the scale),
 * then thickens them until the gaps close, giving solid dark digits on white.
 *
 * [thicken] is how much to thicken, as a share of the picture's short side; digits that fill
 * more of the picture need more. Plain Kotlin so it can be tested without a phone.
 */
object SegmentJoiner {
    fun join(image: Gray, thicken: Double): Gray {
        val short = minOf(image.width, image.height)
        val background = maxFilter(minFilter(image, odd(short * 0.028)), odd(short * 0.028))
        val marks = IntArray(image.pixels.size) { (image.pixels[it] - background.pixels[it]).coerceAtLeast(0) }
        val cut = max(40, (percentile(marks, 0.995) * 0.45).roundToInt())
        var lit = Gray(image.width, image.height, IntArray(marks.size) { if (marks[it] >= cut) 255 else 0 })
        lit = maxFilter(minFilter(lit, 3), 3) // drop single specks (dust, reflections)
        val solid = maxFilter(lit, odd(short * thicken))
        return Gray(image.width, image.height, IntArray(solid.pixels.size) { 255 - solid.pixels[it] })
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
