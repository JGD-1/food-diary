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
    // Extra rows, optional on the form: not counted in [foundCount].
    val fibre: Double? = null,
    val sugar: Double? = null,
    val salt: Double? = null,
) {
    val foundCount: Int get() = listOf(kcal, protein, carbs, fat).count { it != null }
}

/**
 * Reads the nutrition table on a packet. Made for Dutch labels (Energie, Vetten, Koolhydraten,
 * Eiwitten), with English, German and French names too because many packets are multilingual.
 *
 * The table often has two columns, "per 100 g" and "per portie". EU labels always have the
 * per-100 g column; we use the number closest under its header, or else the first number in the row.
 * Some labels have no table but running text ("per 100 g: energie 693 kJ/kcal 166, vetten 9,5 g, ...");
 * those are read piece by piece, see [readRunningText].
 */
object NutritionLabelParser {
    private const val KJ_PER_KCAL = 4.184

    private enum class Nutrient { ENERGY, FAT, CARBS, PROTEIN, FIBRE, SUGAR, SALT }

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

    // The extra rows: sugars (usually "waarvan suikers"), fibre and salt. "Sel" only as a whole word.
    private val extraRows = listOf(
        Nutrient.SUGAR to Regex("suiker|sugar|zucker|sucre"),
        Nutrient.FIBRE to Regex("vezel|fibre|fiber|ballast"),
        Nutrient.SALT to Regex("zout|salt|salz|\\bsel\\b"),
    )
    // Rows that look like an extra row but are something else ("free sugars", "sugar alcohols", sodium).
    private val notExtra = listOf("polyol", "natrium", "sodium", "vrije suiker", "free sugar", "suikeralcohol")

    private enum class Unit { G, KCAL, KJ, OTHER }
    private data class Num(val value: Double, val unit: Unit?, val x: Float)
    private data class Row(val lines: List<TextLine>) {
        val centerY = lines.map { it.centerY }.average().toFloat()
    }

    fun parse(lines: List<TextLine>): LabelValues {
        val table = readTable(lines)
        val running = readRunningText(lines)
        // Running text read as table rows mixes up its values, so there the pieces win; else the table does.
        val found = if (isRunningText(lines)) table + running else running + table
        val energy = found[Nutrient.ENERGY]
        val perMl = lines.firstOrNull { per100.containsMatchIn(normalize(it.text)) }
            ?.let { Regex("100\\s*ml").containsMatchIn(normalize(it.text)) } ?: false
        return LabelValues(
            energy, found[Nutrient.PROTEIN], found[Nutrient.CARBS], found[Nutrient.FAT], perMl,
            fibre = found[Nutrient.FIBRE], sugar = found[Nutrient.SUGAR], salt = found[Nutrient.SALT],
        )
    }

    private fun readTable(lines: List<TextLine>): Map<Nutrient, Double> {
        val rows = groupIntoRows(lines)
        val header = rows.flatMap { it.lines }.firstOrNull { per100.containsMatchIn(normalize(it.text)) }
        val headerX = header?.let { columnX(it) }

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
            val extra = extraRows.firstOrNull { (_, words) -> words.containsMatchIn(label) }?.first
            if (extra != null && extra !in found && notExtra.none { label.contains(it) }) {
                val grams = nums.filter { it.unit == Unit.G || it.unit == null }
                pick(grams, headerX) { it in 0.0..100.0 }?.let { found[extra] = it }
            }
            if (skipWords.any { label.contains(it) }) continue
            val nutrient = keywords.firstOrNull { (n, words) -> n != Nutrient.ENERGY && words.any { w -> label.contains(w) } }?.first
                ?: continue
            if (nutrient in found) continue
            val grams = nums.filter { it.unit == Unit.G || it.unit == null }
            pick(grams, headerX) { it in 0.0..100.0 }?.let { found[nutrient] = it }
        }
        if (Nutrient.ENERGY !in found) kjOnly?.let { found[Nutrient.ENERGY] = toKcal(it) }
        return found
    }

    private fun toKcal(kj: Double) = Math.round(kj / KJ_PER_KCAL).toDouble()

    /** Cuts at commas that are not decimal commas, at ; | • and at a full stop followed by a space. */
    private val pieceBreak = Regex(",(?!\\d)|(?<!\\d),|[;|•·]|\\.\\s")

    /** The label's text in reading order, cut into pieces like "vetten 9,5 g" (wrapped lines joined). */
    private fun pieces(lines: List<TextLine>): List<String> =
        normalize(lines.joinToString(" ") { it.text }).split(pieceBreak).map { it.trim() }.filter { it.isNotEmpty() }

    /** True when one line holds two or more nutrients one after another, as in running text. */
    private fun isRunningText(lines: List<TextLine>): Boolean = lines.any { line ->
        pieces(listOf(line)).count { piece -> nutrientOf(piece) != null && numbersInText(piece).isNotEmpty() } >= 2
    }

    /** The nutrient a piece of text names, or null (sub-rows like "waarvan verzadigde" give null). */
    private fun nutrientOf(text: String): Nutrient? {
        if (keywords.first().second.any { text.contains(it) }) return Nutrient.ENERGY
        extraRows.firstOrNull { (_, words) -> words.containsMatchIn(text) }?.let { (n, _) ->
            return if (notExtra.any { text.contains(it) }) null else n
        }
        if (skipWords.any { text.contains(it) }) return null
        return keywords.firstOrNull { (n, words) -> n != Nutrient.ENERGY && words.any { w -> text.contains(w) } }?.first
    }

    /**
     * Running text: each piece holds one nutrient and its per-100 g value ("eiwitten 20,1 g").
     * The first value counts (the per-100 g one comes first, e.g. "energie 693 kJ/kcal 166 (8%)").
     */
    private fun readRunningText(lines: List<TextLine>): Map<Nutrient, Double> {
        val found = mutableMapOf<Nutrient, Double>()
        var kjOnly: Double? = null
        for (piece in pieces(lines)) {
            val nutrient = nutrientOf(piece) ?: continue
            val nums = numbersInText(piece)
            if (nutrient == Nutrient.ENERGY) {
                if (Nutrient.ENERGY in found) continue
                nums.firstOrNull { it.unit == Unit.KCAL && it.value in 0.0..950.0 }?.let { found[Nutrient.ENERGY] = it.value }
                    ?: run { if (kjOnly == null) kjOnly = nums.firstOrNull { it.unit == Unit.KJ && it.value in 0.0..4000.0 }?.value }
                continue
            }
            if (nutrient in found) continue
            nums.firstOrNull { (it.unit == Unit.G || it.unit == null) && it.value in 0.0..100.0 }?.let { found[nutrient] = it.value }
        }
        if (Nutrient.ENERGY !in found) kjOnly?.let { found[Nutrient.ENERGY] = toKcal(it) }
        return found
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
        numbersInText(normalize(line.text)).map { it.copy(x = xAt(line, it.x.toInt())) }
    }

    /** Numbers in normalized text with their unit; [Num.x] is the character index. */
    private fun numbersInText(text: String): List<Num> {
        // "kJ/kcal 1046/250": the pair belongs together, the first number is kJ, the second kcal.
        val pair = energyPair.find(text) ?: return singleNumbers(text)
        val kjFirst = pair.groupValues[1].startsWith("kj")
        val a = pair.groupValues[2].replace(',', '.').toDoubleOrNull()
        val b = pair.groupValues[3].replace(',', '.').toDoubleOrNull()
        val x = pair.range.first.toFloat()
        val inPair = listOfNotNull(
            a?.let { Num(it, if (kjFirst) Unit.KJ else Unit.KCAL, x) },
            b?.let { Num(it, if (kjFirst) Unit.KCAL else Unit.KJ, x + 1) },
        )
        val rest = text.replaceRange(pair.range, " ".repeat(pair.range.last - pair.range.first + 1))
        return (inPair + singleNumbers(rest)).sortedBy { it.x }
    }

    private val energyPair =
        Regex("(kj\\s*/\\s*kcal|kcal\\s*/\\s*kj)\\)?\\s*:?\\s*(\\d+(?:[.,]\\d+)?)\\s*/\\s*(\\d+(?:[.,]\\d+)?)")

    private fun singleNumbers(text: String): List<Num> = numberThenUnit.findAll(text).mapNotNull { m ->
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
        // A "100 g" in a header or row label is not a value.
        if (v == 100.0 && text.contains(Regex("per\\s*100|100\\s*g\\s*(/|\\))|\\(100\\s*(g|ml)\\)"))) return@mapNotNull null
        val unit = when (m.groupValues[2]) {
            // The unit may come first: "693 kJ/kcal 166".
            "" -> when {
                unitBefore.find(text.substring(0, m.range.first))?.groupValues?.get(1) == "kcal" -> Unit.KCAL
                unitBefore.find(text.substring(0, m.range.first))?.groupValues?.get(1) == "kj" -> Unit.KJ
                else -> null
            }
            "g" -> Unit.G
            "kcal" -> Unit.KCAL
            "kj" -> Unit.KJ
            else -> Unit.OTHER
        }
        Num(v, unit, m.range.first.toFloat())
    }.toList()

    /** "kcal" or "kJ" right before a number ("kcal 166", "kJ: 693"). */
    private val unitBefore = Regex("(kcal|kj)\\s*[:=]?\\s*<?\\s*$")

    /** Rough x position of a character, assuming characters are equally wide. */
    private fun xAt(line: TextLine, index: Int): Float {
        val len = line.text.length.coerceAtLeast(1)
        return line.left + (line.right - line.left) * (index.toFloat() / len)
    }

    /** Lower case, no accents (é → e, ß → ss), so "Énergie" and "Eiweiß" match. */
    internal fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().replace("ß", "ss"), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
}
