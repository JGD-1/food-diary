package nl.guido.foodtracker.feature.camera.read

/** What the camera saw on the kitchen scale's display. */
sealed interface ScaleParse {
    data class Grams(val grams: Double) : ScaleParse
    /** The scale shows ounces or pounds; the user is asked to switch it to grams. */
    data object OtherUnit : ScaleParse
    data object Unreadable : ScaleParse
}

/**
 * Finds the weight on a kitchen scale display in recognised text.
 * The display digits are the largest text in view, so the tallest number wins.
 * Digital (seven-segment) digits are often read as letters (O for 0, l for 1, S for 5); those are fixed.
 */
object ScaleDisplayParser {
    private const val MAX_GRAMS = 15_000.0

    private enum class Unit { G, KG, ML, OZ, LB }

    private val unitWords = mapOf(
        "g" to Unit.G, "gr" to Unit.G, "gram" to Unit.G, "grams" to Unit.G,
        "kg" to Unit.KG, "ml" to Unit.ML,
        "oz" to Unit.OZ, "lb" to Unit.LB, "lbs" to Unit.LB,
    )

    // Letters that seven-segment digits are commonly mistaken for.
    private val lookalikes = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0',
        'l' to '1', 'I' to '1', '|' to '1', 'i' to '1',
        'Z' to '2', 'z' to '2', 'S' to '5', 's' to '5',
        'b' to '6', 'B' to '8', 'q' to '9',
    )

    private data class Candidate(val value: Double, val unit: Unit?, val height: Float, val digits: Int)

    fun parse(lines: List<TextLine>): ScaleParse {
        val candidates = lines.flatMap { candidatesIn(it) }
        val best = candidates.maxWithOrNull(compareBy<Candidate>({ it.height }, { it.digits }))
            ?: return ScaleParse.Unreadable
        val unit = best.unit ?: loneUnit(lines)
        val grams = when (unit) {
            Unit.OZ, Unit.LB -> return ScaleParse.OtherUnit
            Unit.KG -> best.value * 1000
            Unit.G, Unit.ML -> best.value
            // "1.250" with nothing after it is a scale in kilogram mode.
            null -> if (decimals(best) == 3) best.value * 1000 else best.value
        }
        return if (grams in 0.0..MAX_GRAMS) ScaleParse.Grams(round1(grams)) else ScaleParse.Unreadable
    }

    private fun decimals(c: Candidate): Int = c.digits - c.value.toLong().toString().length

    private fun candidatesIn(line: TextLine): List<Candidate> {
        val tokens = line.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        // Printed words on the scale ("KitchenBrothers", "TARE"): a number on that line is a logo
        // or model number, not the weight. The display itself only ever shows digits and a unit.
        if (tokens.any { isWord(it) }) return emptyList()
        val out = mutableListOf<Candidate>()
        for ((i, raw) in tokens.withIndex()) {
            if (raw.startsWith("-")) continue // below zero: the scale is being tared
            var token = raw
            var unit: Unit? = null
            // Unit stuck to the number: "245g", "1.2kg"
            unitWords.keys.sortedByDescending { it.length }.firstOrNull { u ->
                token.length > u.length && token.lowercase().endsWith(u) && isNumberish(token.dropLast(u.length))
            }?.let { u -> unit = unitWords[u]; token = token.dropLast(u.length) }
            if (unit == null) unit = tokens.getOrNull(i + 1)?.lowercase()?.let { unitWords[it] }
            val value = toNumber(token, hasUnit = unit != null) ?: continue
            val digitCount = token.count { it.isDigit() || it in lookalikes }
            out += Candidate(value, unit, line.height, digitCount)
        }
        return out
    }

    private fun isWord(token: String): Boolean {
        val letters = token.count { it.isLetter() }
        return letters >= 3 && token.lowercase().trimEnd('.') !in unitWords && letters > token.length / 2 && !isNumberish(token)
    }

    private fun isNumberish(s: String) = s.isNotEmpty() && s.all { it.isDigit() || it in lookalikes || it == '.' || it == ',' }

    private fun toNumber(token: String, hasUnit: Boolean): Double? {
        if (!isNumberish(token)) return null
        // Lookalike letters only count when the token clearly is a number: it has a real digit,
        // or it is the single "O" a scale shows at zero, followed by a unit.
        if (token.none { it.isDigit() } && !(hasUnit && token.length == 1)) return null
        val fixed = token.map { lookalikes[it] ?: it }.joinToString("").replace(',', '.')
        if (fixed.count { it == '.' } > 1) return null
        return fixed.trim('.').toDoubleOrNull()
    }

    /** A unit shown on its own (a small "g" next to big digits). */
    private fun loneUnit(lines: List<TextLine>): Unit? =
        lines.flatMap { it.text.lowercase().split(Regex("\\s+")) }.firstNotNullOfOrNull { unitWords[it] }

    private fun round1(v: Double) = Math.round(v * 10) / 10.0
}
