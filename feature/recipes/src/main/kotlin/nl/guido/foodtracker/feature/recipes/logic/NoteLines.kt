package nl.guido.foodtracker.feature.recipes.logic

import kotlin.math.roundToInt

/**
 * Reading one line of a handwritten ingredient note (round 4, H): "300 g gehakt", "2 eieren",
 * "1 blik tomaten", "olijfolie 2 el", "½ ui". Dutch and English words both work. The text comes
 * from the phone's text reader, so common misreadings in numbers ("3OO g", "l00 ml") are fixed.
 */
data class NoteLine(
    /** The line as read, cleaned up. */
    val read: String,
    /** The words naming the food, e.g. "gehakt"; used to search. */
    val food: String,
    /** How many, e.g. 300 (with [unit] GRAM) or 2 (eggs); null when the line has no amount. */
    val count: Double? = null,
    val unit: NoteUnit? = null,
    /** The unit as written, e.g. "el" or "blikje", for the amount label. */
    val unitWord: String? = null,
) {
    /**
     * Grams for this line. Pieces use [pieceGrams] (e.g. one egg), else [servingG]; tins and
     * packs use [packageG]. Null when the line has no amount or the size of one isn't known.
     */
    fun grams(pieceGrams: Double? = null, servingG: Double? = null, packageG: Double? = null): Double? {
        val n = count ?: return null
        val one = when (unit) {
            null, NoteUnit.PIECE -> pieceGrams ?: servingG
            NoteUnit.TIN -> packageG ?: NoteUnit.TIN_GUESS_G
            NoteUnit.PACK -> packageG
            else -> unit.grams
        } ?: return null
        return (n * one).takeIf { it > 0.0 }
    }

    /**
     * The amount as words for the ingredient line ("2 eieren", "2 el", "1 blik"), or null when the
     * line was in grams or ml already (the grams say it all).
     */
    val amountLabel: String?
        get() {
            val n = count ?: return null
            return when (unit) {
                null, NoteUnit.PIECE -> "${formatCount(n)} $food"
                NoteUnit.GRAM, NoteUnit.KILO, NoteUnit.ML, NoteUnit.CL, NoteUnit.DL, NoteUnit.LITRE -> null
                else -> "${formatCount(n)} ${unitWord ?: unit.name.lowercase()}"
            }
        }
}

/** Units found on notes, with grams for one (liquids: 1 ml = 1 g, as everywhere in the app). */
enum class NoteUnit(val grams: Double?) {
    GRAM(1.0), KILO(1000.0), ML(1.0), CL(10.0), DL(100.0), LITRE(1000.0),
    TABLESPOON(15.0), TEASPOON(5.0), PINCH(1.0), CLOVE(5.0), CUP(240.0),
    /** A tin: the food's pack size, else a usual 400 g tin. */
    TIN(null),
    /** A pack or bag: only when the food's pack size is known. */
    PACK(null),
    /** "2 stuks": like a bare count. */
    PIECE(null);

    companion object {
        const val TIN_GUESS_G = 400.0
    }
}

private val UNIT_WORDS: Map<String, NoteUnit> = buildMap {
    listOf("g", "gr", "gram", "grams", "gramm", "grammen").forEach { put(it, NoteUnit.GRAM) }
    listOf("kg", "kilo", "kilos", "kilogram").forEach { put(it, NoteUnit.KILO) }
    listOf("ml", "milliliter", "millilitre").forEach { put(it, NoteUnit.ML) }
    listOf("cl", "centiliter").forEach { put(it, NoteUnit.CL) }
    listOf("dl", "deciliter").forEach { put(it, NoteUnit.DL) }
    listOf("l", "lt", "ltr", "liter", "litre", "liters", "litres").forEach { put(it, NoteUnit.LITRE) }
    listOf("el", "eetl", "eetlepel", "eetlepels", "tbsp", "tbs", "tablespoon", "tablespoons").forEach { put(it, NoteUnit.TABLESPOON) }
    listOf("tl", "theel", "theelepel", "theelepels", "tsp", "teaspoon", "teaspoons").forEach { put(it, NoteUnit.TEASPOON) }
    listOf("snuf", "snufje", "snufjes", "mespunt", "mespuntje", "pinch").forEach { put(it, NoteUnit.PINCH) }
    listOf("teen", "teentje", "teentjes", "tenen", "clove", "cloves").forEach { put(it, NoteUnit.CLOVE) }
    listOf("cup", "cups").forEach { put(it, NoteUnit.CUP) }
    listOf("blik", "blikje", "blikjes", "blikken", "tin", "tins", "can", "cans").forEach { put(it, NoteUnit.TIN) }
    listOf("pak", "pakje", "pakjes", "pakken", "zak", "zakje", "zakjes", "pack", "packs", "packet", "packets", "bag", "bags").forEach { put(it, NoteUnit.PACK) }
    listOf("stuk", "stuks", "piece", "pieces", "pc", "pcs").forEach { put(it, NoteUnit.PIECE) }
}

private val NUMBER_WORDS = mapOf(
    "een" to 1.0, "één" to 1.0, "a" to 1.0, "an" to 1.0, "one" to 1.0,
    "twee" to 2.0, "two" to 2.0, "drie" to 3.0, "three" to 3.0, "vier" to 4.0, "four" to 4.0,
    "vijf" to 5.0, "five" to 5.0, "zes" to 6.0, "six" to 6.0,
    "half" to 0.5, "halve" to 0.5, "halfje" to 0.5, "anderhalf" to 1.5, "anderhalve" to 1.5,
)

private val FRACTIONS = mapOf('½' to 0.5, '¼' to 0.25, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3)

/** Little words between the amount and the food: "300 g van de kip", "2 el of olive oil". */
private val FILLER = setOf("van", "de", "het", "of", "the", "x", "×")

/** Lines that are a heading, not an ingredient. */
private val HEADINGS = setOf("ingredienten", "ingrediënten", "ingredients", "boodschappen", "shopping", "recept", "recipe")

/**
 * Reads one note line, or null when it holds no food words (a number on its own, a heading,
 * a smudge). [text] is one line as the text reader gave it.
 */
fun parseNoteLine(text: String): NoteLine? {
    var cleaned = text.trim()
        .replace(Regex("^[-•*·–—+>~]+\\s*"), "") // bullets
        .replace(Regex("\\s+"), " ")
    // "1. 300 g kip": a numbered list, when another number follows.
    cleaned = cleaned.replace(Regex("^\\d{1,2}[.)]\\s+(?=[\\d½¼¾⅓⅔])"), "")
    if (cleaned.isEmpty()) return null
    val tokens = splitTokens(cleaned)
    if (tokens.isEmpty()) return null

    var amount = leadingAmount(tokens)
    var rest = if (amount != null) tokens.drop(amount.used) else tokens
    if (amount == null) {
        trailingAmount(tokens)?.let { found ->
            amount = found
            rest = tokens.dropLast(found.used)
        }
    }
    val foodWords = rest.dropWhile { it.lowercase() in FILLER }
    val food = foodWords.joinToString(" ")
        .substringBefore('(').substringBefore(',').substringBefore(';')
        .trim(' ', '.', ':', '-', '–')
    if (food.count { it.isLetter() } < 2) return null
    if (amount == null && food.lowercase().trimEnd(':') in HEADINGS) return null
    val a = amount
    return NoteLine(
        read = cleaned,
        food = food,
        count = a?.count,
        unit = a?.unit,
        unitWord = a?.unitWord,
    )
}

/** An amount found at the start or end of a line, and how many tokens it took. */
private data class Amount(val count: Double, val unit: NoteUnit?, val unitWord: String?, val used: Int)

/** Splits "300g" into "300" "g" and "2x" into "2" "x", so numbers and units stand apart. */
private fun splitTokens(line: String): List<String> = line.split(' ').filter { it.isNotEmpty() }.flatMap { raw ->
    val bare = raw.trim(',', ';', ':')
    val token = fixDigits(bare)
    val m = Regex("^(\\d+(?:[.,]\\d+)?(?:/\\d+)?[½¼¾⅓⅔]?|[½¼¾⅓⅔])([A-Za-zé.]+)$").find(token)
    when {
        m != null -> listOf(m.groupValues[1], m.groupValues[2])
        token != bare -> listOf(token)
        // Words keep their commas, so "1 ui, gesnipperd" still knows where the food name ends.
        else -> listOf(raw)
    }
}.filter { it.isNotEmpty() }

/**
 * The text reader often sees 0 as O and 1 as l or I in handwriting. A token with at least one
 * real digit whose other characters are such look-alikes becomes digits ("3OO" → "300", "l00g" → "100g").
 */
internal fun fixDigits(token: String): String {
    val m = Regex("^([0-9OoIl|]+(?:[.,][0-9OoIl|]+)?)([A-Za-z]*)$").find(token) ?: return token
    val number = m.groupValues[1]
    if (number.none { it.isDigit() }) return token
    // An l at the very end stays a letter: "10l" is 10 litres.
    val fixed = number.mapIndexed { i, c ->
        when (c) {
            'O', 'o' -> '0'
            'I', 'l', '|' -> if (i < number.lastIndex) '1' else c
            else -> c
        }
    }.joinToString("")
    return fixed + m.groupValues[2]
}

/** A number token: "300", "1,5", "1/2", "½", "1½". Null when it isn't one. */
internal fun numberOf(token: String): Double? {
    val t = token.replace(',', '.')
    FRACTIONS[t.singleOrNull() ?: ' ']?.let { return it }
    if (t.length > 1 && t.last() in FRACTIONS) {
        val whole = t.dropLast(1).toDoubleOrNull() ?: return null
        return whole + FRACTIONS.getValue(t.last())
    }
    if ('/' in t) {
        val (a, b) = t.split('/', limit = 2)
        val top = a.toDoubleOrNull() ?: return null
        val bottom = b.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        return top / bottom
    }
    return t.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
}

private fun unitOf(token: String): NoteUnit? = UNIT_WORDS[token.lowercase().trimEnd('.')]

private fun leadingAmount(tokens: List<String>): Amount? {
    var i = 0
    val first = numberOf(tokens[0]) ?: NUMBER_WORDS[tokens[0].lowercase()]
    if (first == null) {
        // "snufje zout", "blikje tomaten": a unit on its own means one.
        val unit = unitOf(tokens[0]) ?: return null
        return if (tokens.size > 1) Amount(1.0, unit, tokens[0].trimEnd('.'), 1) else null
    }
    var count: Double = first
    i++
    // "1 1/2" or "1 ½"
    if (i < tokens.size && tokens[i].let { '/' in it || it.singleOrNull() in FRACTIONS }) {
        numberOf(tokens[i])?.takeIf { it < 1.0 }?.let { count += it; i++ }
    }
    // "2 x ui"
    if (i < tokens.size && tokens[i].lowercase() in setOf("x", "×")) i++
    val unit = tokens.getOrNull(i)?.let(::unitOf)
    // A bare "a" or "een" only counts when a unit or a food follows ("een ui", not just "a").
    if (unit == null && i >= tokens.size) return null
    // "1 l" at the end of a line with no food is not an amount for anything.
    if (unit != null && i + 1 >= tokens.size) return null
    return if (unit != null) Amount(count, unit, tokens[i].trimEnd('.'), i + 1) else Amount(count, null, null, i)
}

private fun trailingAmount(tokens: List<String>): Amount? {
    if (tokens.size < 2) return null
    val last = tokens.last()
    val unit = unitOf(last)
    return if (unit != null) {
        if (tokens.size < 3) return null
        val n = numberOf(tokens[tokens.size - 2]) ?: return null
        Amount(n, unit, last.trimEnd('.'), 2)
    } else {
        val n = numberOf(last) ?: return null
        Amount(n, null, null, 1)
    }
}

/** "2", "1.5", "½" for labels. */
internal fun formatCount(n: Double): String = when {
    n == 0.5 -> "½"
    n % 1.0 == 0.0 -> n.toInt().toString()
    n > 1.0 && (n % 1.0) == 0.5 -> "${n.toInt()}½"
    else -> ((n * 100).roundToInt() / 100.0).toString()
}
