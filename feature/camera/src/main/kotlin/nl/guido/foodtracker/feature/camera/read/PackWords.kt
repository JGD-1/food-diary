package nl.guido.foodtracker.feature.camera.read

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import nl.guido.foodtracker.core.model.Id

/**
 * Packs the app has learned: the words read on a pack, and the food you said it is.
 * The next time most of those words are in view, that food is the first guess.
 */
data class PackWords(val packs: List<Pack> = emptyList()) {
    @Serializable
    data class Pack(val words: Set<String>, val foodId: Id)

    /** Foods learned for packs whose words are mostly in [seen], best match first. */
    fun lookup(seen: Set<String>): List<Id> =
        packs.map { it to match(it.words, seen) }
            .filter { it.second >= MIN_MATCH }
            .sortedByDescending { it.second }
            .map { it.first.foodId }
            .distinct()

    /** These pack words with [foodId] remembered; a pack with the same words is replaced. */
    fun learn(words: Set<String>, foodId: Id): PackWords {
        if (words.isEmpty() || words.sumOf { it.length } < 4) return this
        val pack = Pack(words, foodId)
        if (pack in packs) return this
        val others = packs.filter { it.words != words }.takeLast(MAX_PACKS - 1)
        return PackWords(others + pack)
    }

    fun encode(): String = json.encodeToString(serializer, packs)

    companion object {
        const val MAX_PACKS = 300
        /** Share of a learned pack's words that must be in view (two of three, say). */
        private const val MIN_MATCH = 0.6
        private val json = Json { ignoreUnknownKeys = true }
        private val serializer = ListSerializer(Pack.serializer())

        fun decode(text: String?): PackWords =
            text?.let { runCatching { PackWords(json.decodeFromString(serializer, it)) }.getOrNull() } ?: PackWords()

        private fun match(learned: Set<String>, seen: Set<String>): Double =
            if (learned.isEmpty()) 0.0 else learned.count { it in seen }.toDouble() / learned.size
    }
}
