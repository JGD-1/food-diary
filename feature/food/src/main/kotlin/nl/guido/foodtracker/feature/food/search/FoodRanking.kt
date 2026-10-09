package nl.guido.foodtracker.feature.food.search

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import java.text.Normalizer

/** A search hit plus any other names it can be found by (e.g. the Dutch NEVO name). */
internal data class Candidate(val food: Food, val otherNames: List<String> = emptyList())

/**
 * Orders search results so the likely one is on top: every typed word must match somewhere;
 * then exact name, name starting with the text, a word starting with it; own foods before NEVO
 * before Open Food Facts; shorter names first.
 */
internal object FoodRanking {
    fun words(text: String): List<String> = fold(text).split(' ').filter { it.isNotEmpty() }

    /** The word to send to the database's simple "contains" search: the longest one is most selective. */
    fun probe(text: String): String? = words(text).maxByOrNull { it.length }

    fun rank(query: String, candidates: List<Candidate>, limit: Int = 50): List<Food> {
        val q = words(query)
        if (q.isEmpty()) return emptyList()
        val phrase = q.joinToString(" ")
        return candidates
            .distinctBy { it.food.id }
            .mapNotNull { c ->
                val names = (listOf(c.food.name) + c.otherNames).map(::fold)
                val haystack = (names + listOfNotNull(c.food.brand?.let(::fold))).joinToString(" ")
                if (!q.all { it in haystack }) return@mapNotNull null
                val nameScore = names.minOf { name ->
                    when {
                        name == phrase -> 0
                        name.startsWith(phrase) -> 1
                        name.split(' ').any { it.startsWith(q.first()) } -> 2
                        else -> 3
                    }
                }
                Triple(c.food, nameScore, sourceOrder(c.food.source))
            }
            .sortedWith(compareBy({ it.second }, { it.third }, { it.first.name.length }, { it.first.name }))
            .take(limit)
            .map { it.first }
    }

    private fun sourceOrder(origin: FoodOrigin) = when (origin) {
        FoodOrigin.MANUAL, FoodOrigin.LABEL -> 0
        FoodOrigin.NEVO -> 1
        FoodOrigin.OFF -> 2
    }

    /** Lower case, accents removed ("Crème" matches "creme"), punctuation becomes spaces. */
    fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}%]+"), " ")
            .trim()
}
