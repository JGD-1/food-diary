package nl.guido.foodtracker.feature.camera.read

/**
 * The words on a pack, gathered over many camera frames while it is being weighed.
 * The product name is usually the biggest text, so lines are ranked by their size and by
 * how often they were read; lines seen only once are left out, so one misread frame never counts.
 */
class PackTextVotes(private val minSeen: Int = 2) {
    private class Seen(var count: Int, var height: Float)

    private val lines = mutableMapOf<String, Seen>()

    /** Feed the lines read on one frame. */
    fun offer(frame: List<TextLine>) {
        frame.mapNotNull { line -> packLine(line.text)?.let { it to line.height } }
            .distinctBy { it.first }
            .forEach { (text, height) ->
                val seen = lines.getOrPut(text) { Seen(0, 0f) }
                seen.count++
                seen.height = maxOf(seen.height, height)
            }
        // Keep it small while the phone is held over a pack for a long time.
        if (lines.size > MAX_LINES) {
            lines.entries.sortedBy { it.value.count }.take(lines.size - MAX_LINES).forEach { lines.remove(it.key) }
        }
    }

    /** The likely name lines, biggest text first (at most [limit]). */
    fun names(limit: Int = 3): List<String> =
        lines.entries.filter { it.value.count >= minSeen }
            .sortedWith(compareByDescending<Map.Entry<String, Seen>> { it.value.height }.thenByDescending { it.value.count })
            .take(limit).map { it.key }

    /** Every word read often enough, for matching words learned earlier. */
    fun words(): Set<String> =
        lines.entries.filter { it.value.count >= minSeen }.flatMapTo(HashSet()) { packWords(it.key) }

    fun reset() = lines.clear()

    private companion object {
        const val MAX_LINES = 60
    }
}

/**
 * A line in plain lowercase words, or null when it can't be part of a name: numbers, weights,
 * nutrition table words, or too short or long to be a name.
 */
fun packLine(text: String): String? {
    val words = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    val letters = words.filter { w -> w.any { it.isLetter() } }
    if (letters.isEmpty() || letters.size > 6) return null
    if (letters.sumOf { it.length } < 3) return null
    if (letters.any { it in NOT_A_NAME }) return null
    // "500 g", "e 1234", "2x 125": mostly numbers.
    if (words.count { w -> w.any { it.isDigit() } } * 2 > words.size) return null
    return letters.joinToString(" ")
}

/** The words of a line worth searching or matching on: 3+ letters, no filler like "in", "met", "bio". */
fun packWords(line: String): List<String> =
    line.split(' ').filter { it.length >= 3 && it.all { c -> c.isLetter() } && it !in FILLER }

/**
 * What to search for, best first: each name line as a whole, then its longer words on their
 * own (a brand-heavy "AH Rode kidneybonen in water" still finds kidney beans by "kidneybonen").
 */
fun packSearches(names: List<String>, limit: Int = 5): List<String> {
    val whole = names.map { packWords(it).joinToString(" ") }.filter { it.isNotEmpty() }
    val single = names.flatMap(::packWords).filter { it.length >= 4 }.sortedByDescending { it.length }
    return (whole + single).distinct().take(limit)
}

/** Words that are on packs but never part of the food's name (Dutch and English). */
private val FILLER = setOf(
    "and", "the", "with", "in", "of", "en", "met", "van", "het", "de", "een", "voor", "zonder",
    "bio", "biologisch", "organic", "nieuw", "new", "per", "stuks", "pieces", "net", "netto",
    "water", "vers", "fresh", "naturel", "lekker",
    "albert", "heijn", "jumbo", "lidl", "aldi", "plus", "coop", "spar", "dirk", "hoogvliet",
)

/** Words of the nutrition table, the ingredients list and the small print. */
private val NOT_A_NAME = setOf(
    "energie", "energy", "kcal", "kj", "vet", "fat", "verzadigd", "saturated", "koolhydraten", "carbohydrate",
    "carbohydrates", "suikers", "sugars", "eiwitten", "eiwit", "protein", "zout", "salt", "vezels", "fibre",
    "voedingswaarde", "voedingswaarden", "nutrition", "ingrediënten", "ingredienten", "ingredients",
    "bewaren", "houdbaar", "tenminste", "minimaal", "allergie", "allergenen", "bevat", "contains",
    "gewicht", "inhoud", "weight", "www", "nl", "com", "klantenservice",
)
