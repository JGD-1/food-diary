package nl.guido.foodtracker.feature.recipes.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Id

/**
 * The food for a note line from the search results (round 4, H): a food already used in a recipe
 * or batch wins when it is near the top, since notes mostly repeat what is cooked at home.
 */
fun pickFood(candidates: List<Food>, recentIds: Set<Id>, look: Int = 8): Food? =
    candidates.take(look).firstOrNull { it.id in recentIds } ?: candidates.firstOrNull()

/**
 * What to search for, best first: the whole food name, then each word on its own (longest first,
 * at least 3 letters), so "rode ui gesnipperd" still finds an onion.
 */
fun searchTries(food: String): List<String> {
    val whole = food.trim()
    val words = whole.split(' ').map { it.trim(',', '.', ':', ';') }.filter { w -> w.count { it.isLetter() } >= 3 }
    return (listOf(whole) + words.sortedByDescending { it.length }).filter { it.isNotEmpty() }.distinct()
}

/**
 * The closest of [names] (ingredients used before) to a misread food name, when alike enough to
 * be the same word read badly: "gehakl" → "gehakt". Null when nothing is that close.
 */
fun closestName(food: String, names: Collection<String>, atLeast: Double = 0.75): String? {
    val key = matchKey(food)
    if (key.length < 3) return null
    return names.map { it to similarity(key, matchKey(it)) }
        .filter { it.second >= atLeast }
        .maxByOrNull { it.second }
        ?.first
}

/** One checked note line handed back to Cook a batch or the recipe form: a food, and its amount when known. */
@Serializable
data class NoteIngredient(val foodId: Id, val grams: Double? = null, val label: String? = null)

private val noteJson = Json { ignoreUnknownKeys = true }

fun encodeNoteIngredients(items: List<NoteIngredient>): String =
    noteJson.encodeToString(ListSerializer(NoteIngredient.serializer()), items)

fun decodeNoteIngredients(text: String): List<NoteIngredient> =
    runCatching { noteJson.decodeFromString(ListSerializer(NoteIngredient.serializer()), text) }.getOrDefault(emptyList())
