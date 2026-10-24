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

    // Words that start a sub-row ("waarvan suikers", "verzadigde vetzuren", "zetmeel"). A nutrient named
    // after one of them is a sub-row and never the main value, except sugars (see [nutrientOf]).
    // "Vetten, waarvan" names fat first, so it is the fat row.
    private val subRowWords = Regex(
        "waarvan|of which|davon|\\bdont\\b|verzadig|saturat|gesattigt|satures|polyol|zetmeel|starch|mono|meervoudig",
    )
    private val keywords = listOf(
        Nutrient.ENERGY to listOf("energie", "energy", "energia", "kcal", "kj"),
        Nutrient.CARBS to listOf("koolhydra", "carbohydrate", "kohlenhydrat", "glucides"),
        Nutrient.PROTEIN to listOf("eiwit", "protein", "eiweiss", "eiweis"),
        Nutrient.FAT to listOf("vet", "fat", "fett", "matieres grasses", "lipides", "grassi"),
    )

    // The extra rows: sugars (usually "waarvan suikers"), fibre and salt. "Sel" only as a whole word.
    private val extraRows = listOf(
        // With the text reader's usual misreads: "suiters", and "Zout" at the edge of a sticker read as "Out" or "Dut".
        Nutrient.SUGAR to Regex("suiker|suiter|sugar|zucker|sucre"),
        Nutrient.FIBRE to Regex("vezel|fibre|fiber|ballast"),
        Nutrient.SALT to Regex("\\bzout|salt|salz|\\bsel\\b|^\\W*(out|dut)\\b"),
    )
    // Rows that look like an extra row but are something else ("free sugars", "sugar alcohols", sodium).
    private val notExtra = listOf("polyol", "natrium", "sodium", "vrije suiker", "free sugar", "suikeralcohol")

    private enum class Unit { G, KCAL, KJ, OTHER }
    /** A number as written ([raw], "9,5"), its unit if one follows, and where it sits. */
    private data class Num(val value: Double, val unit: Unit?, val x: Float, val raw: String = "")
    /** A grams value, and what it would be if the text reader lost its comma ("18g" for "1,8 g"). */
    private data class Reading(val value: Double, val alt: Double? = null)
    private data class Row(val lines: List<TextLine>) {
        val centerY = lines.map { it.centerY }.average().toFloat()
    }

    fun parse(lines: List<TextLine>): LabelValues {
        val usesG = gramsWritten.findAll(clean(lines.joinToString(" ") { it.text })).count() >= 3
        val table = readTable(lines, usesG)
        val running = readRunningText(lines, usesG)
        // Running text read as table rows mixes up its values, so there the pieces win; else the table does.
        val found = settle(if (isRunningText(lines)) table + running else running + table)
        val energy = found[Nutrient.ENERGY]
        val perMl = lines.firstOrNull { per100.containsMatchIn(clean(it.text)) }
            ?.let { Regex("100\\s*ml").containsMatchIn(clean(it.text)) } ?: false
        return LabelValues(
            energy, found[Nutrient.PROTEIN], found[Nutrient.CARBS], found[Nutrient.FAT], perMl,
            fibre = found[Nutrient.FIBRE], sugar = found[Nutrient.SUGAR], salt = found[Nutrient.SALT],
        )
    }

    private fun readTable(lines: List<TextLine>, usesG: Boolean): Map<Nutrient, Reading> {
        val rows = groupIntoRows(lines)
        val header = rows.flatMap { it.lines }.firstOrNull { per100.containsMatchIn(clean(it.text)) }
        val headerX = header?.let { columnX(it) }
        // The other columns ("per portie", "per pack"): a value nearer one of those is not per 100 g.
        val otherX = if (header == null) emptyList() else lines
            .filter { it != header && abs(it.centerY - header.centerY) < header.height && otherColumn.containsMatchIn(clean(it.text)) }
            .map { (it.left + it.right) / 2 }

        val found = mutableMapOf<Nutrient, Reading>()
        var kjOnly: Double? = null
        for (row in rows) {
            val label = clean(row.lines.joinToString(" ") { it.text })
            val nums = numbersIn(row)
            if (nums.isEmpty()) continue

            // Energy: any row with a kcal number, wherever it sits (some labels put kcal on its own row).
            val kcal = nums.filter { it.unit == Unit.KCAL || (it.unit == null && label.contains("kcal") && !label.contains("kj")) }
            val kj = nums.filter { it.unit == Unit.KJ || (it.unit == null && label.contains("kj") && !label.contains("kcal")) }
            if (Nutrient.ENERGY !in found) {
                pick(kcal, headerX) { it in 0.0..950.0 }?.let { found[Nutrient.ENERGY] = Reading(it) }
                    ?: run { if (kjOnly == null) kjOnly = pick(kj, headerX) { it in 0.0..4000.0 } }
            }
            val nutrient = nutrientOf(label)
            if (nutrient == null || nutrient == Nutrient.ENERGY || nutrient in found) continue
            val grams = nums.filter { it.unit == Unit.G || it.unit == null }
                .filter { n -> headerX == null || otherX.none { abs(n.x - it) < abs(n.x - headerX) } }
                .mapNotNull { n -> gramReading(n, usesG)?.let { n to it } }
            val nearest = if (headerX == null) grams.minByOrNull { it.first.x } else grams.minByOrNull { abs(it.first.x - headerX) }
            nearest?.let { found[nutrient] = it.second }
        }
        if (Nutrient.ENERGY !in found) kjOnly?.let { found[Nutrient.ENERGY] = Reading(toKcal(it)) }
        return found
    }

    private val otherColumn = Regex("portie|portion|serving|pack|per \\d+\\s?(g|ml)|\\bri\\b")

    /** "9,5 g" or "14 g" written at least three times: this label writes its units. */
    private val gramsWritten = Regex("\\d\\s?g\\b")

    /**
     * A grams value with the text reader's usual misreads fixed. Null when it can't be grams per 100 g.
     * - "6,7g" read as "6,79" and "0 g" as "09": on a label that writes "g", a bare number ending in 9 lost its g.
     * - "05g" for "0,5g": a leading 0 means the comma was lost.
     * - "101g" for "10,1g", "18g" for "1,8g": a whole number may have lost its comma too. Over 100 it must have;
     *   otherwise [Reading.alt] keeps the other reading, and [settle] picks the one that fits the calories.
     */
    private fun gramReading(n: Num, usesG: Boolean): Reading? {
        var s = n.raw.replace(',', '.')
        if (n.unit == null && usesG && s.length >= 2 && s.endsWith('9') && !s.endsWith(".9")) s = s.dropLast(1)
        if (Regex("0\\d").matches(s)) s = "0." + s[1]
        val value = s.toDoubleOrNull() ?: return null
        val alt = if (Regex("[1-9]\\d+").matches(s)) (s.dropLast(1) + "." + s.last()).toDouble() else null
        return when {
            value <= 100.0 -> Reading(value, alt)
            alt != null && alt <= 100.0 -> Reading(alt)
            else -> null
        }
    }

    /**
     * Calories first: fat, carbs, protein and fibre must roughly add up to the calories (9, 4, 4 and 2 kcal
     * per gram). When they don't and values have another reading ("18g" may be 1,8 g) that fits much better,
     * those are used. Sugars are never more than carbs.
     */
    private fun settle(found: Map<Nutrient, Reading>): Map<Nutrient, Double> {
        val values = found.mapValues { it.value.value }.toMutableMap()
        val kcal = values[Nutrient.ENERGY]
        val counted = listOf(Nutrient.FAT to 9.0, Nutrient.CARBS to 4.0, Nutrient.PROTEIN to 4.0, Nutrient.FIBRE to 2.0)
            .filter { it.first in found }
        val macrosKnown = listOf(Nutrient.FAT, Nutrient.CARBS, Nutrient.PROTEIN).all { it in found }
        if (kcal != null && macrosKnown && counted.any { found.getValue(it.first).alt != null }) {
            fun error(pick: List<Double>) = abs(kcal - counted.indices.sumOf { counted[it].second * pick[it] })
            // Every mix of as-read and other readings (at most 16).
            val mixes = counted.fold(listOf(emptyList<Double>())) { acc, (n, _) ->
                acc.flatMap { mix -> listOfNotNull(found.getValue(n).value, found.getValue(n).alt).map { mix + it } }
            }
            val asRead = error(mixes.first())
            val best = mixes.minBy(::error)
            if (asRead > maxOf(15.0, kcal * 0.12) && error(best) < asRead / 2) {
                counted.forEachIndexed { i, (n, _) -> values[n] = best[i] }
            }
        }
        val carbs = values[Nutrient.CARBS]
        val sugar = found[Nutrient.SUGAR]
        if (carbs != null && sugar != null && sugar.value > carbs && sugar.alt != null && sugar.alt <= carbs) values[Nutrient.SUGAR] = sugar.alt
        return values
    }

    private fun toKcal(kj: Double) = Math.round(kj / KJ_PER_KCAL).toDouble()

    /** Cuts at commas that are not decimal commas, at ; | • and at a full stop followed by a space. */
    private val pieceBreak = Regex(",(?!\\d)|(?<!\\d),|[;|•·]|\\.\\s")

    /**
     * The label's text in reading order, cut into pieces like "vetten 9,5 g". Wrapped lines are joined
     * ("koolhy-" + "draten" → "koolhydraten"), and a piece is also cut where a nutrient follows a value
     * ("energie 250 kcal vet 10 g" → two pieces), for labels without commas.
     */
    private fun pieces(lines: List<TextLine>): List<String> =
        clean(joinLines(lines)).split(pieceBreak).flatMap(::cutAtNutrients).map { it.trim() }.filter { it.isNotEmpty() }

    private fun joinLines(lines: List<TextLine>): String = buildString {
        for (line in lines) {
            val text = line.text.trim()
            if (isEmpty()) append(text)
            else if (endsWith("-") && text.firstOrNull()?.isLowerCase() == true) { setLength(length - 1); append(text) }
            else append(' ').append(text)
        }
    }

    private val nutrientWord = Regex(
        "\\b(energie|energy|vet|fat|koolhydra|carbohydrate|eiwit|protein|suiker|sugar|vezel|fibre|fiber|zout|salt|waarvan|of which)",
    )
    // A value, maybe one word after it ("0,0 g vaarvan suikers": "waarvan" misread, so not a nutrient word).
    private val endsWithValue = Regex("\\d\\s*(g|gr|kcal|kj|mg|µg|ug|%)?\\s*\\)?\\s*([a-z]+\\s+)?$")

    private fun cutAtNutrients(piece: String): List<String> {
        val cuts = nutrientWord.findAll(piece).map { it.range.first }
            .filter { at -> at > 0 && endsWithValue.containsMatchIn(piece.substring(0, at)) }.toList()
        if (cuts.isEmpty()) return listOf(piece)
        return (listOf(0) + cuts).zip(cuts + piece.length) { from, to -> piece.substring(from, to) }
    }

    /** True when one line holds two or more nutrients one after another, as in running text. */
    private fun isRunningText(lines: List<TextLine>): Boolean = lines.any { line ->
        pieces(listOf(line)).count { piece -> nutrientOf(piece) != null && numbersInText(piece).isNotEmpty() } >= 2
    }

    /**
     * The nutrient a row or piece of text names: the one named first ("vetten, waarvan" is fat).
     * Named after a sub-row word ("waarvan verzadigde vetzuren") it is a sub-row: null, except sugars,
     * which are always written as "waarvan suikers". Null when nothing is named.
     */
    private fun nutrientOf(text: String): Nutrient? {
        if (notExtra.any { text.contains(it) }) return null
        val hits = keywords.flatMap { (n, words) -> words.mapNotNull { w -> text.indexOf(w).takeIf { it >= 0 }?.let { it to n } } } +
            extraRows.mapNotNull { (n, words) -> words.find(text)?.let { it.range.first to n } }
        val (at, nutrient) = hits.minByOrNull { it.first } ?: return null
        val subRow = subRowWords.find(text)?.range?.first
        return if (subRow != null && subRow < at && nutrient != Nutrient.SUGAR) null else nutrient
    }

    /**
     * Running text: each piece holds one nutrient and its per-100 g value ("eiwitten 20,1 g").
     * The first value counts (the per-100 g one comes first, e.g. "energie 693 kJ/kcal 166 (8%)").
     */
    private fun readRunningText(lines: List<TextLine>, usesG: Boolean): Map<Nutrient, Reading> {
        val found = mutableMapOf<Nutrient, Reading>()
        var kjOnly: Double? = null
        // Start where the nutrition values start, so "rich in fibre: 3,1 g" on the front of a packet comes last.
        val start = lines.indexOfFirst { valuesStart.containsMatchIn(clean(it.text)) }.coerceAtLeast(0)
        for (piece in pieces(lines.drop(start) + lines.take(start))) {
            val nutrient = nutrientOf(piece) ?: continue
            val nums = numbersInText(piece)
            if (nutrient == Nutrient.ENERGY) {
                if (Nutrient.ENERGY in found) continue
                nums.firstOrNull { it.unit == Unit.KCAL && it.value in 0.0..950.0 }?.let { found[Nutrient.ENERGY] = Reading(it.value) }
                    ?: run { if (kjOnly == null) kjOnly = nums.firstOrNull { it.unit == Unit.KJ && it.value in 0.0..4000.0 }?.value }
                continue
            }
            if (nutrient in found) continue
            nums.firstNotNullOfOrNull { if (it.unit == Unit.G || it.unit == null) gramReading(it, usesG) else null }?.let { found[nutrient] = it }
        }
        if (Nutrient.ENERGY !in found) kjOnly?.let { found[Nutrient.ENERGY] = Reading(toKcal(it)) }
        return found
    }

    private val valuesStart = Regex("voedingswaarde|nutrition|nahrwert|valeurs nutritionnelles|energ|kcal")

    private val per100 = Regex("100\\s*(g|gr|gram|ml)\\b")

    /** Horizontal position of "100 g" inside the header line. */
    private fun columnX(line: TextLine): Float {
        val text = clean(line.text)
        val at = per100.find(text)?.range ?: return (line.left + line.right) / 2
        return xAt(line, (at.first + at.last) / 2)
    }

    private fun pick(nums: List<Num>, headerX: Float?, plausible: (Double) -> Boolean): Double? {
        val ok = nums.filter { plausible(it.value) }
        if (ok.isEmpty()) return null
        return if (headerX == null) ok.minBy { it.x }.value else ok.minBy { abs(it.x - headerX) }.value
    }

    /**
     * Table rows: each line with words is a row, and each line of only values joins the row whose words sit
     * nearest in height on its left. Values printed a little lower than their names (common in photos) still
     * land in the right row.
     */
    private fun groupIntoRows(lines: List<TextLine>): List<Row> {
        val (values, words) = lines.partition { isValueLine(it.text) }
        val rows = words.associateWith { mutableListOf(it) }
        val loose = mutableListOf<TextLine>()
        for (v in values) {
            // Words further left behind other words (an ingredients column beside the table) are not this row.
            val near = words.filter { it.left < v.left }
                .filter { w -> words.none { it.left >= w.right && it.right <= v.left && overlaps(it, w) } }
                .minByOrNull { abs(it.centerY - v.centerY) }
            if (near != null && abs(near.centerY - v.centerY) < maxOf(near.height, v.height) * 0.8) rows.getValue(near) += v else loose += v
        }
        return (rows.values.map { Row(it.sortedBy { l -> l.left }) } + groupByHeight(loose)).sortedBy { it.centerY }
    }

    private fun overlaps(a: TextLine, b: TextLine) = a.top < b.bottom && b.top < a.bottom

    private val units = Regex("(?<=\\d|\\s|^)(kcal|kj|kl|gr|g|mg|ml|ug|µg)\\b")
    private fun isValueLine(text: String): Boolean {
        val rest = units.replace(clean(text), "")
        return rest.any { it.isDigit() } && rest.none { it.isLetter() }
    }

    private fun groupByHeight(lines: List<TextLine>): List<Row> {
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

    // Not a number glued to a word ("E301", "B2").
    private val numberThenUnit = Regex("<?\\s*(?<![a-z\\d.,])(\\d+(?:[.,]\\d+)?)\\s*(kcal|kj|mg|µg|ug|mcg|g|ml|%)?(?![a-z])")

    private fun numbersIn(row: Row): List<Num> = row.lines.flatMap { line ->
        numbersInText(clean(line.text)).map { it.copy(x = xAt(line, it.x.toInt())) }
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
        Num(v, unit, m.range.first.toFloat(), m.groupValues[1])
    }.toList()

    /** "kcal" or "kJ" right before a number ("kcal 166", "kJ: 693"). */
    private val unitBefore = Regex("(kcal|kj)\\s*[:=]?\\s*<?\\s*$")

    /** Rough x position of a character, assuming characters are equally wide. */
    private fun xAt(line: TextLine, index: Int): Float {
        val len = line.text.length.coerceAtLeast(1)
        return line.left + (line.right - line.left) * (index.toFloat() / len)
    }

    /** [normalize], then the text reader's usual misreads fixed: "O,86" → "0,86", "16.6q" and "2,5 9" → grams. */
    private fun clean(s: String): String =
        normalize(s).replace(letterO, "0").replace(qForG, "g").replace(gAfterSpace, " g")

    private val letterO = Regex("(?<![a-z0-9])o(?=[.,]\\d)")
    private val qForG = Regex("(?<=\\d)\\s?q\\b")
    private val gAfterSpace = Regex("(?<=\\d) [9a](?![\\w.,])")

    /** Lower case, no accents (é → e, ß → ss), so "Énergie" and "Eiweiß" match. */
    internal fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase().replace("ß", "ss"), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
}
