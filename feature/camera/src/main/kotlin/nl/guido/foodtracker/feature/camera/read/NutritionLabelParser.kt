package nl.guido.foodtracker.feature.camera.read

import java.text.Normalizer
import kotlin.math.abs

/** Values read from a nutrition label, per 100 g (or per 100 ml). Null = not found; the user fills it in. */
data class LabelValues(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    /** The label says "per 100 ml", so it is most likely a drink. */
    val perMl: Boolean = false,
) {
    val foundCount: Int get() = listOf(kcal, protein, carbs, fat).count { it != null }
}

/**
 * Reads the nutrition table on a packet. Made for Dutch labels (Energie, Vetten, Koolhydraten,
 * Eiwitten), with English, German and French names too because many packets are multilingual.
 *
 * The table often has two columns, "per 100 g" and "per portie". EU labels always have the
 * per-100 g column; we use the number closest under its header, or else the first number in the row.
 */
object NutritionLabelParser {
    private const val KJ_PER_KCAL = 4.184

    private enum class Nutrient { ENERGY, FAT, CARBS, PROTEIN }

    // Sub-rows ("waarvan suikers", "verzadigde vetzuren") and other rows we must never read as the main value.
    private val skipWords = listOf(
        "waarvan", "of which", "davon", "dont", "verzadig", "saturat", "gesattigt", "satures",
        "onverzadig", "suiker", "sugar", "zucker", "sucre", "vezel", "fibre", "fiber", "ballast",
        "zout", "salt", "salz", "sel", "polyol", "zetmeel", "starch",
    )
    private val keywords = listOf(
        Nutrient.ENERGY to listOf("energie", "energy", "energia", "kcal", "kj"),
        Nutrient.CARBS to listOf("koolhydra", "carbohydrate", "kohlenhydrat", "glucides"),
        Nutrient.PROTEIN to listOf("eiwit", "protein", "eiweiss", "eiweis"),
        Nutrient.FAT to listOf("vet", "fat", "fett", "matieres grasses", "lipides", "grassi"),
    )

    private enum class Unit { G, KCAL, KJ, OTHER }
    private data class Num(val value: Double, val unit: Unit?, val x: Float)
    private data class Row(val lines: List<TextLine>) {
        val centerY = lines.map { it.centerY }.average().toFloat()
    }

    fun parse(lines: List<TextLine>): LabelValues {
        val rows = groupIntoRows(lines)
        val header = rows.flatMap { it.lines }.firstOrNull { per100.containsMatchIn(normalize(it.text)) }
        val headerX = header?.let { columnX(it) }
        val perMl = header?.let { Regex("100\\s*ml").containsMatchIn(normalize(it.text)) } ?: false

        val found = mutableMapOf<Nutrient, Double>()
        var kjOnly: Double? = null
        for (row in rows) {
            val label = normalize(row.lines.joinToString(" ") { it.text })
            val nums = numbersIn(row)
            if (nums.isEmpty()) continue

            // Energy: any row with a kcal number, wherever it sits (some labels put kcal on its own row).
            val kcal = nums.filter { it.unit == Unit.KCAL || (it.unit == null && label.contains("kcal") && !label.contains("kj")) }
            val kj = nums.filter { it.unit == Unit.KJ || (it.unit == null && label.contains("kj") && !label.contains("kcal")) }
            if (Nutrient.ENERGY !in found) {
                pick(kcal, headerX) { it in 0.0..950.0 }?.let { found[Nutrient.ENERGY] = it }
                    ?: run { if (kjOnly == null) kjOnly = pick(kj, headerX) { it in 0.0..4000.0 } }
            }
            if (skipWords.any { label.contains(it) }) continue
            val nutrient = keywords.firstOrNull { (n, words) -> n != Nutrient.ENERGY && words.any { w -> label.contains(w) } }?.first
                ?: continue
            if (nutrient in found) continue
            val grams = nums.filter { it.unit == Unit.G || it.unit == null }
            pick(grams, headerX) { it in 0.0..100.0 }?.let { found[nutrient] = it }
        }
        val energy = found[Nutrient.ENERGY] ?: kjOnly?.let { Math.round(it / KJ_PER_KCAL).toDouble() }
        return LabelValues(energy, found[Nutrient.PROTEIN], found[Nutrient.CARBS], found[Nutrient.FAT], perMl)
    }

    private val per100 = Regex("100\\s*(g|gr|gram|ml)\\b")

    /** Horizontal position of "100 g" inside the header line. */
    private fun columnX(line: TextLine): Float {
        val text = normalize(line.text)
        val at = per100.find(text)?.range ?: return (line.left + line.right) / 2
        return xAt(line, (at.first + at.last) / 2)
    }

    private fun pick(nums: List<Num>, headerX: Float?, plausible: (Double) -> Boolean): Double? {
        val ok = nums.filter { plausible(it.value) }
        if (ok.isEmpty()) return null
        return if (headerX == null) ok.minBy { it.x }.value else ok.minBy { abs(it.x - headerX) }.value
    }

    private fun groupIntoRows(lines: List<TextLine>): List<Row> {
        val rows = mutableListOf<MutableList<TextLine>>()
        for (line in lines.sortedBy { it.centerY }) {
            val row = rows.lastOrNull()
            val ref = row?.minBy { it.height }
            if (row != null && ref != null && abs(line.centerY - row.map { it.centerY }.average()) < minOf(ref.height, line.height) * 0.5) {
                row += line
            } else {
                rows += mutableListOf(line)
            }
        }
        return rows.map { Row(it.sortedBy { l -> l.left }) }
    }

    private val numberThenUnit = Regex("<?\\s*(\\d+(?:[.,]\\d+)?)\\s*(kcal|kj|mg|µg|ug|mcg|g|ml)?(?![a-z])")

    private fun numbersIn(row: Row): List<Num> = row.lines.flatMap { line ->
        val text = normalize(line.text)
        numberThenUnit.findAll(text).mapNotNull { m ->
            val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
            // A "100 g" in a header or row label is not a value.
            if (v == 100.0 && text.contains(Regex("per\\s*100|100\\s*g\\s*(/|\\))"))) return@mapNotNull null
            val unit = when (m.groupValues[2]) {
                "" -> null
                "g" -> Unit.G
                "kcal" -> Unit.KCAL
                "kj" -> Unit.KJ
                else -> Unit.OTHER
            }
            Num(v, unit, xAt(line, m.range.first))
        }.toList()
    }

    /** Rough x position of a character, assuming characters are equally wide. */
    private fun xAt(line: TextLine, index: Int): Float {
        val len = line.text.length.coerceAtLeast(1)
        return line.left + (line.right - line.left) * (index.toFloat() / len)
    }

    /** Lower case, no accents (é → e, ß → ss), so "Énergie" and "Eiweiß" match. */
    internal fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().replace("ß", "ss"), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
}
