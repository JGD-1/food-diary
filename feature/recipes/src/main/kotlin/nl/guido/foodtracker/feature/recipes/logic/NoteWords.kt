package nl.guido.foodtracker.feature.recipes.logic

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import nl.guido.foodtracker.core.model.Id

/**
 * Words from notes that the app has learned (round 4, H): "kbonen" → kidney beans, once picked.
 * Keys are [matchKey]s of the food words on a note line.
 */
data class NoteWords(val foods: Map<String, Id> = emptyMap()) {
    /** The food learned for exactly these words. */
    fun lookup(food: String): Id? = foods[matchKey(food)]

    /**
     * The food learned for words read slightly differently ("kbonon" for "kbonen"). Only after
     * a normal search found nothing, since "bonen" is also this close to "kbonen".
     */
    fun closest(food: String, atLeast: Double = 0.8): Id? {
        val key = matchKey(food)
        if (key.length < 4) return null
        return foods.entries.map { it to similarity(key, it.key) }
            .filter { it.second >= atLeast }
            .maxByOrNull { it.second }
            ?.first?.value
    }

    /** The same words with [foodId] remembered; the newest pick wins. */
    fun learn(food: String, foodId: Id): NoteWords {
        val key = matchKey(food)
        if (key.length < 2 || foods[key] == foodId) return this
        // Keep the list small: the oldest words go first.
        val kept = (foods - key).entries.toList().takeLast(MAX_WORDS - 1).associate { it.key to it.value }
        return NoteWords(kept + (key to foodId))
    }

    fun encode(): String = json.encodeToString(mapSerializer, foods)

    companion object {
        const val MAX_WORDS = 500
        private val json = Json { ignoreUnknownKeys = true }
        private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

        fun decode(text: String?): NoteWords =
            text?.let { runCatching { NoteWords(json.decodeFromString(mapSerializer, it)) }.getOrNull() } ?: NoteWords()
    }
}

/**
 * Shorthand where the first letter or two stand for a word: "Kbonen" for kidney bonen,
 * "Rvlees" for rundvlees. Each try is (the letters, the rest to search for); the rest has at
 * least 4 letters. Empty for an ordinary word.
 */
fun shorthandTries(food: String): List<Pair<String, String>> {
    val word = matchKey(food)
    if (' ' in word || word.length < 5 || !word.all { it.isLetter() }) return emptyList()
    return (1..2).map { word.take(it) to word.drop(it) }.filter { it.second.length >= 4 }
}

/**
 * Whether [name] fits the shorthand: it holds [rest], and a word of it starts with [letters]
 * apart from where [rest] is ("kidney bonen", "bonen kidney", "kidneybonen" for k + bonen).
 */
fun fitsShorthand(letters: String, rest: String, name: String): Boolean {
    val words = matchKey(name).split(' ').filter { it.isNotEmpty() }
    val home = words.indexOfFirst { rest in it }
    if (home < 0) return false
    val before = words[home].substringBefore(rest)
    if (before.isNotEmpty()) return before.startsWith(letters)
    return words.withIndex().any { (i, w) -> i != home && w.startsWith(letters) }
}
