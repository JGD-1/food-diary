package nl.guido.foodtracker.feature.food.dishes

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import nl.guido.foodtracker.core.model.Estimate
import nl.guido.foodtracker.core.model.RestaurantEstimator
import nl.guido.foodtracker.feature.food.search.searchWords
import java.io.Reader
import javax.inject.Inject
import javax.inject.Singleton

internal const val DISH_ASSET = "restaurant_dishes.csv"

/** A restaurant or takeaway dish with a kcal range. [own] = typed in by the person, one value. */
data class Dish(val name: String, val otherNames: List<String>, val estimate: Estimate, val own: Boolean = false)

/** Lower case, words only, single spaces, so "Pizza, Margherita!" and "pizza margherita" are the same dish. */
internal fun dishKey(text: String): String = searchWords(text).joinToString(" ")

/** Reads the bundled list. Lines: name|other names (;)|low|typical|high; # starts a comment. */
internal object DishListParser {
    fun parse(reader: Reader): List<Dish> = reader.useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                val c = line.split('|')
                require(c.size == 5) { "Dish line has ${c.size} columns: $line" }
                Dish(
                    name = c[0].trim(),
                    otherNames = c[1].split(';').map { it.trim() }.filter { it.isNotEmpty() },
                    estimate = Estimate(c[2].trim().toInt(), c[3].trim().toInt(), c[4].trim().toInt()),
                )
            }
            .toList()
    }
}

/**
 * Dishes matching what was typed (every word, in any order, in the name or another name), best first:
 * exact name, name starting with the text, another name starting with it, then the rest;
 * the person's own dishes before the list, then shorter names.
 */
internal fun matchDishes(text: String, dishes: List<Dish>, limit: Int = 8): List<Dish> {
    val words = searchWords(text)
    if (words.isEmpty()) return emptyList()
    val phrase = words.joinToString(" ")
    return dishes
        .filter { dish ->
            val names = (listOf(dish.name) + dish.otherNames).joinToString(" ") { dishKey(it) }
            words.all { it in names }
        }
        .sortedWith(
            compareBy<Dish>(
                { dish ->
                    val name = dishKey(dish.name)
                    when {
                        name == phrase -> 0
                        name.startsWith(phrase) -> 1
                        dish.otherNames.any { dishKey(it).startsWith(phrase) } -> 2
                        else -> 3
                    }
                },
                { if (it.own) 0 else 1 },
                { it.name.length },
            ),
        )
        .take(limit)
}

/** The dish with exactly this name (or other name), if any. */
internal fun findDish(text: String, dishes: List<Dish>): Dish? {
    val key = dishKey(text)
    if (key.isEmpty()) return null
    return dishes.sortedBy { if (it.own) 0 else 1 }
        .firstOrNull { dish -> dishKey(dish.name) == key || dish.otherNames.any { dishKey(it) == key } }
}

@Serializable
private data class OwnDish(val name: String, val kcal: Int)

/** The bundled dish list plus the dishes this person typed in themselves (kept on this phone). */
@Singleton
class DishRepository @Inject internal constructor(@ApplicationContext private val context: Context) {
    private val mutex = Mutex()
    private var bundled: List<Dish>? = null
    private val prefs by lazy { context.getSharedPreferences("food_dishes", Context.MODE_PRIVATE) }
    private val json = Json { ignoreUnknownKeys = true }
    private val ownSerializer = ListSerializer(OwnDish.serializer())

    suspend fun all(): List<Dish> = withContext(Dispatchers.IO) {
        val list = mutex.withLock {
            bundled ?: context.assets.open(DISH_ASSET).bufferedReader().use { DishListParser.parse(it) }
                .also { bundled = it }
        }
        own().map { Dish(it.name, emptyList(), Estimate(it.kcal, it.kcal, it.kcal), own = true) } + list
    }

    /** Remembers a dish typed with its kcal, so it shows up in the list next time (newest value wins). */
    suspend fun saveOwn(name: String, kcal: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val key = dishKey(name)
            val kept = own().filterNot { dishKey(it.name) == key }
            prefs.edit().putString("own", json.encodeToString(ownSerializer, kept + OwnDish(name.trim(), kcal))).apply()
        }
    }

    private fun own(): List<OwnDish> =
        prefs.getString("own", null)?.let { runCatching { json.decodeFromString(ownSerializer, it) }.getOrNull() }.orEmpty()
}

/** Estimates come from the dish list on the phone, no internet. Throws when the dish isn't known. */
internal class DishListEstimator @Inject constructor(private val dishes: DishRepository) : RestaurantEstimator {
    override suspend fun estimate(dish: String): Estimate =
        findDish(dish, dishes.all())?.estimate ?: throw NoSuchElementException("Unknown dish: $dish")
}
