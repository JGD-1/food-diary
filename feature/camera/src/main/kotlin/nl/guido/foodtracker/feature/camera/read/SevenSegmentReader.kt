package nl.guido.foodtracker.feature.camera.read

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reads the number on a digital kitchen scale straight from the lit bars ("segments") of its digits.
 *
 * General text recognition is made for printed letters and often misreads segment digits (a 7 whose
 * top bar stands apart looks like a 1). Here every lit bar is found on its own, the display's
 * directions are worked out from the bars, and each digit is read from which of its 7 bars are lit:
 *
 *      a
 *    f   b
 *      g
 *    e   c
 *      d
 *
 * Works when the display is turned, tilted or seen at a slant. Plain Kotlin, so it can be tested on
 * Guido's photos without a phone.
 */
object SevenSegmentReader {

    /** Which bars are lit (a..g as bits 0..6) for each digit. */
    private val DIGITS = mapOf(
        0b0111111 to 0, 0b0000110 to 1, 0b1011011 to 2, 0b1001111 to 3, 0b1100110 to 4,
        0b1101101 to 5, 0b1111101 to 6, 0b0000111 to 7, 0b0100111 to 7, 0b1111111 to 8,
        0b1101111 to 9, 0b1100111 to 9, 0b1111100 to 6,
    )
    private const val A = 1; private const val B = 2; private const val C = 4; private const val D = 8
    private const val E = 16; private const val F = 32; private const val G = 64

    /** Mark sizes to look for (share of the short side): close-ups need bigger ones. */
    private val MARK_SIZES = listOf(0.03, 0.07)

    /** Brightness cuts (share of the brightest marks): dim displays need a lower one. */
    private val CUTS = listOf(0.45, 0.2)

    /** One lit blob: where it is, which way it points, how long and thick it is. */
    internal data class Blob(val x: Double, val y: Double, val angle: Double, val length: Double, val width: Double, val area: Int)

    /**
     * The weight in grams, or null when no clear number is seen. The picture is looked at in a few
     * ways; the number those readings agree on most wins.
     */
    fun read(image: Gray): Int? {
        val readings = MARK_SIZES.flatMap { size ->
            val marks = BrightMarks.marks(image, size)
            CUTS.mapNotNull { share ->
                val cut = BrightMarks.cut(marks, share)
                readMask(BrightMarks.lit(marks, cut), marks, cut)
            }
        }
        return readings.groupBy { it.value }
            .mapValues { (_, same) -> same.sumOf { it.score.coerceAtLeast(0.1) } }
            .maxByOrNull { it.value }
            ?.key
    }

    internal class Reading(val value: Int, val score: Double)

    internal fun readMask(lit: BooleanArray, marks: Gray, cut: Int): Reading? {
        val bright = marks.pixels.indices.filter { marks.pixels[it] >= cut / 2 }.toIntArray()
        return barSets(blobs(lit, marks.width, marks.height)).flatMap { (first, second) ->
            // Either set could be the upright bars, and the display could be upside down: try all four.
            val angle1 = meanAngle(first)
            val angle2 = meanAngle(second)
            listOf(Triple(first, second, angle2 to angle1), Triple(second, first, angle1 to angle2)).flatMap { (upright, across, angles) ->
                listOf(false, true).mapNotNull { flip -> decode(upright, across, bright, marks.width, angles.first, angles.second, flip) }
            }
        }.maxByOrNull { it.score }
    }

    private fun decode(
        upright: List<Blob>, across: List<Blob>, bright: IntArray, width: Int,
        acrossAngle: Double, uprightAngle: Double, flip: Boolean,
    ): Reading? {
        if (upright.size < 2) return null
        // Display directions: "right" along the across bars, "down" along the upright bars.
        var rx = cos(acrossAngle); var ry = sin(acrossAngle)
        if (flip) { rx = -rx; ry = -ry }
        var dx = cos(uprightAngle); var dy = sin(uprightAngle)
        if (rx * dy - ry * dx < 0) { dx = -dx; dy = -dy }
        val det = rx * dy - ry * dx
        if (det < 0.5) return null // the two directions are too close to tell apart
        // Each blob in the display's own (slanted) grid: u to the right, v downwards.
        fun u(b: Blob) = (b.x * dy - b.y * dx) / det
        fun v(b: Blob) = (rx * b.y - ry * b.x) / det

        // Upright bars sit in an upper and a lower row.
        val (upper, lower) = twoRows(upright.map { v(it) }) ?: return null
        val rowGap = lower - upper
        val middle = (upper + lower) / 2
        val acrossLength = across.map { it.length }.sorted().getOrNull(across.size / 2)
        val uprightLength = upright.map { it.length }.sorted()[upright.size / 2]
        val halfWidth = (acrossLength ?: (uprightLength * 0.8)) / 2

        // Digit centres from the across bars; nearby ones belong to the same digit.
        val centres = mutableListOf<MutableList<Double>>()
        for (x in across.map { u(it) }.sorted()) {
            val last = centres.lastOrNull()
            if (last != null && x - last.average() < halfWidth) last += x else centres += mutableListOf(x)
        }
        val digits = centres.map { DigitSlots(it.average()) }.toMutableList()

        var unused = 0
        for (bar in across) {
            val digit = digits.minBy { abs(it.centre - u(bar)) }
            val row = v(bar) - middle
            digit.lit = digit.lit or when {
                row < -rowGap * 1.6 || row > rowGap * 1.6 -> { unused++; 0 }
                row < -rowGap / 2 -> A
                row > rowGap / 2 -> D
                else -> G
            }
        }
        val lonely = mutableListOf<Blob>()
        for (bar in upright) {
            val digit = digits.minByOrNull { abs(it.centre - u(bar)) }
            val off = if (digit == null) 0.0 else u(bar) - digit.centre
            if (digit == null || abs(off) > halfWidth * 2.2 || abs(off) < halfWidth * 0.4) { lonely += bar; continue }
            val top = v(bar) < middle
            digit.lit = digit.lit or when {
                off > 0 && top -> B
                off > 0 -> C
                top -> F
                else -> E
            }
        }
        // Upright bars with no digit around them: a "1" (only its right-hand bars).
        lonely.sortedBy { u(it) }.forEach { bar ->
            val one = digits.firstOrNull { it.isOne && abs(it.centre - (u(bar) - halfWidth)) < halfWidth }
                ?: DigitSlots(u(bar) - halfWidth, isOne = true).also { digits += it }
            one.lit = one.lit or if (v(bar) < middle) B else C
        }
        val ordered = digits.sortedBy { it.centre }
        val values = ordered.map { DIGITS[it.lit] }
        if (values.isEmpty() || values.size > 5 || values.any { it == null }) return null
        // Digits on one display are evenly spaced; a big hole means two separate things were seen.

        var score = values.size.toDouble() - unused
        // Right way up or upside down? The small unit sign ("g") sits top right of the digits, so
        // compare how much lights up there with the same spot turned round (bottom left).
        val first = ordered.first().centre
        val last = ordered.last().centre
        fun litIn(u0: Double, u1: Double, v0: Double, v1: Double): Int {
            var count = 0
            for (p in bright) {
                val x = p % width
                val y = p / width
                val pu = (x * dy - y * dx) / det
                val pv = (rx * y - ry * x) / det
                if (pu > u0 && pu < u1 && pv > v0 && pv < v1) count++
            }
            return count
        }
        val topRight = litIn(last + halfWidth * 1.5, last + halfWidth * 3.5, upper - rowGap * 0.8, upper + rowGap * 0.2)
        val bottomLeft = litIn(first - halfWidth * 3.5, first - halfWidth * 1.5, lower - rowGap * 0.2, lower + rowGap * 0.8)
        if (topRight > bottomLeft * 1.5 + 3) score += 2
        if (bottomLeft > topRight * 1.5 + 3) score -= 2
        // A "1" only has bars on its right-hand side, so it sits at the normal spacing from its
        // neighbour only when the display is read the right way up.
        val pitch = ordered.zipWithNext().filter { (a, b) -> !a.isOne && !b.isOne }.map { (a, b) -> b.centre - a.centre }
            .sorted().let { if (it.isEmpty()) halfWidth * 5.3 else it[it.size / 2] }
        for ((i, one) in ordered.withIndex()) {
            if (!one.isOne || ordered.size < 2) continue
            val gap = if (i + 1 < ordered.size) ordered[i + 1].centre - one.centre else one.centre - ordered[i - 1].centre
            score -= 2 * abs(gap - pitch) / pitch
        }
        // Leading zeros don't happen on a scale ("0" alone does).
        if (values.size > 1 && values.first() == 0) score -= 3
        return Reading(values.joinToString("").toInt(), score)
    }

    private class DigitSlots(val centre: Double, val isOne: Boolean = false) { var lit = 0 }

    /** Splits values into two groups (upper and lower row); returns their averages. */
    private fun twoRows(values: List<Double>): Pair<Double, Double>? {
        if (values.size < 2) return null
        val sorted = values.sorted()
        var best: Pair<Double, Double>? = null
        var bestSpread = Double.MAX_VALUE
        for (i in 1 until sorted.size) {
            val a = sorted.subList(0, i)
            val b = sorted.subList(i, sorted.size)
            val spread = a.sumOf { (it - a.average()).let { d -> d * d } } + b.sumOf { (it - b.average()).let { d -> d * d } }
            if (spread < bestSpread) { bestSpread = spread; best = a.average() to b.average() }
        }
        return best
    }

    /**
     * Possible sets of digit bars, each split by the two ways they point (upright and across).
     * Bars are long thin blobs; the two ways are the two most common directions; bars of one way
     * are about the same length; and the bars of one display lie close together. How close is
     * hard to say (printed letters may sit near the digits; the next digit may lack the bar
     * closest to this one, as with 6 then 7), so groups are made in a few ways and the reading
     * that makes most sense wins.
     */
    internal fun barSets(blobs: List<Blob>): List<Pair<List<Blob>, List<Blob>>> {
        val thin = blobs.filter { it.length >= it.width * 1.6 && it.area >= 10 }
        if (thin.size < 3) return emptyList()
        // Directions in whole degrees 0..179, weighted by size, smoothed a little.
        val votes = DoubleArray(180)
        for (b in thin) {
            val deg = degrees(b)
            for (k in -5..5) votes[(deg + k).mod(180)] += b.area * (6.0 - abs(k))
        }
        val peak1 = votes.indices.maxBy { votes[it] }
        val peak2 = votes.indices.filter { turn(it, peak1) >= 35 }.maxBy { votes[it] }
        fun pointing(peak: Int) = thin.filter { turn(degrees(it), peak) <= 12 }
            .let { set -> if (set.isEmpty()) set else set.filter { it.length in typical(set) * 0.55..typical(set) * 1.5 } }
        val one = pointing(peak1)
        val two = pointing(peak2)
        if (one.isEmpty() || two.isEmpty()) return emptyList()

        // Distances measured along the two bar directions, in bar lengths, so a slanted view counts the same.
        val (ax, ay) = cos(meanAngle(one)) to sin(meanAngle(one))
        val (bx, by) = cos(meanAngle(two)) to sin(meanAngle(two))
        val det = ax * by - ay * bx
        val lengthOne = typical(one)
        val lengthTwo = typical(two)
        val longest = maxOf(lengthOne, lengthTwo)
        fun along(p: Blob, q: Blob): Pair<Double, Double> {
            val dx = q.x - p.x
            val dy = q.y - p.y
            return abs((dx * by - dy * bx) / det) / lengthOne to abs((ax * dy - ay * dx) / det) / lengthTwo
        }
        val ways: List<(Blob, Blob) -> Boolean> = listOf(
            { p, q -> hypot(p.x - q.x, p.y - q.y) < longest * 1.6 },
            { p, q -> hypot(p.x - q.x, p.y - q.y) < longest * 2.2 },
            { p, q ->
                val (a, b) = along(p, q)
                hypot(a, b) < 1.6 || (a < 3.0 && b < 0.6) || (b < 3.0 && a < 0.6)
            },
        )
        return ways.flatMap { linked ->
            val groups = mutableListOf<List<Blob>>()
            for (bar in one + two) {
                val close = groups.filter { g -> g.any { linked(it, bar) } }
                groups.removeAll(close)
                groups += close.flatten() + bar
            }
            groups.filter { it.size >= 3 }.map { g -> one.filter { it in g } to two.filter { it in g } }
        }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }.distinct()
    }

    private fun degrees(b: Blob) = Math.toDegrees(b.angle).roundToInt().mod(180)

    /** A typical bar length in a set: the third longest, so one stray long blob doesn't count. */
    private fun typical(set: List<Blob>) = set.map { it.length }.sortedDescending().let { it[minOf(it.size - 1, 2)] }

    /** Degrees between two directions (0..90). */
    private fun turn(a: Int, b: Int) = abs(a - b).let { minOf(it, 180 - it) }

    /** Average direction of bars that point (about) the same way. */
    private fun meanAngle(bars: List<Blob>) =
        atan2(bars.sumOf { sin(2 * it.angle) * it.area }, bars.sumOf { cos(2 * it.angle) * it.area }) / 2

    /** Connected lit areas, with their direction and size from their spread. */
    internal fun blobs(lit: BooleanArray, w: Int, h: Int): List<Blob> {
        val seen = BooleanArray(lit.size)
        val out = mutableListOf<Blob>()
        val stack = IntArray(lit.size)
        for (start in lit.indices) {
            if (!lit[start] || seen[start]) continue
            var top = 0
            stack[top++] = start
            seen[start] = true
            var n = 0; var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0; var sxy = 0.0
            while (top > 0) {
                val p = stack[--top]
                val x = p % w
                val y = p / w
                n++; sx += x; sy += y; sxx += x.toDouble() * x; syy += y.toDouble() * y; sxy += x.toDouble() * y
                if (x > 0 && lit[p - 1] && !seen[p - 1]) { seen[p - 1] = true; stack[top++] = p - 1 }
                if (x < w - 1 && lit[p + 1] && !seen[p + 1]) { seen[p + 1] = true; stack[top++] = p + 1 }
                if (y > 0 && lit[p - w] && !seen[p - w]) { seen[p - w] = true; stack[top++] = p - w }
                if (y < h - 1 && lit[p + w] && !seen[p + w]) { seen[p + w] = true; stack[top++] = p + w }
            }
            val mx = sx / n
            val my = sy / n
            val cxx = sxx / n - mx * mx + 1.0 / 12 // + 1/12: each pixel is a little square, not a point
            val cyy = syy / n - my * my + 1.0 / 12
            val cxy = sxy / n - mx * my
            val angle = 0.5 * atan2(2 * cxy, cxx - cyy)
            val root = sqrt(((cxx - cyy) / 2).let { it * it } + cxy * cxy)
            val big = (cxx + cyy) / 2 + root
            val small = ((cxx + cyy) / 2 - root).coerceAtLeast(0.0)
            // A filled bar of length L has a spread of L²/12 along it.
            out += Blob(mx, my, angle, sqrt(12 * big), sqrt(12 * small), n)
        }
        return out
    }
}
