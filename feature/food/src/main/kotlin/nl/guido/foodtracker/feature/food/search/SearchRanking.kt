package nl.guido.foodtracker.feature.food.search

import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin

/** Splits what was typed into words, ignoring case and punctuation. */
internal fun searchWords(text: String): List<String> =
    text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

/**
 * Keeps foods that contain every typed word (in any order, so "brown bread" finds "Bread brown")
 * and puts the best matches first: exact name, name starting with the text, a word starting with
 * the first typed word; own foods before Open Food Facts before NEVO; then shorter names.
 */
internal fun rankFoods(text: String, candidates: List<Food>, limit: Int = 50): List<Food> {
    val words = searchWords(text)
    if (words.isEmpty()) return emptyList()
    val phrase = words.joinToString(" ")
    return candidates
        .filter { food ->
            val hay = haystack(food)
            words.all { it in hay }
        }
        .sortedWith(
            compareBy<Food>(
                { food ->
                    val name = searchWords(food.name).joinToString(" ")
                    when {
                        name == phrase -> 0
                        name.startsWith(phrase) -> 1
                        searchWords(food.name).any { it.startsWith(words.first()) } -> 2
                        else -> 3
                    }
                },
                { food ->
                    when (food.source) {
                        FoodOrigin.LABEL, FoodOrigin.MANUAL -> 0
                        FoodOrigin.OFF -> 1
                        FoodOrigin.NEVO -> 2
                    }
                },
                { it.name.length },
                { it.name },
            ),
        )
        .take(limit)
}

private fun haystack(food: Food) = (food.name + " " + (food.brand ?: "")).lowercase()
