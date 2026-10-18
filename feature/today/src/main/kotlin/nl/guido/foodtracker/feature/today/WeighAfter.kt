package nl.guido.foodtracker.feature.today

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Meal
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Weigh after" (round 7, W2): a food weighed before eating (a bag of crisps, a block of cheese).
 * Nothing is in the diary yet; once what's left is weighed, the difference is logged in [meal] on [date].
 */
internal data class WeighAfterItem(
    val id: Id,
    val userId: Id,
    /** Stored on the phone (FoodRepository) when this was started, so the line is made from it later. */
    val foodId: Id,
    /** The name shown on the card (the readable name when there is one). */
    val title: String,
    val startGrams: Double,
    val meal: Meal,
    val date: LocalDate,
    val startedAt: Instant,
)

/** What was eaten: the start minus what's left; null when that isn't more than nothing. */
internal fun eatenGrams(startGrams: Double, leftGrams: Double): Double? =
    (startGrams - leftGrams).takeIf { leftGrams >= 0 && it > 0 }

/** Parses the typed grams left over ("40", "0", "12,5"); 0 is fine here (all eaten). */
internal fun parseLeftGrams(text: String): Double? {
    val value = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
    return value.takeIf { it >= 0.0 && it <= 5000.0 }
}

/** One item per line, fields split by tabs (tabs and line breaks in names become spaces). */
internal fun encodeWeighAfter(items: List<WeighAfterItem>): String = items.joinToString("\n") { item ->
    listOf(
        item.id, item.userId, item.foodId, clean(item.title), item.startGrams, item.meal.name, item.date, item.startedAt.toEpochMilli(),
    ).joinToString("\t")
}

/** Reads what [encodeWeighAfter] wrote; lines that can't be read are skipped. */
internal fun decodeWeighAfter(text: String?): List<WeighAfterItem> =
    text.orEmpty().lines().filter { it.isNotBlank() }.mapNotNull { line ->
        val f = line.split('\t')
        if (f.size != 8) return@mapNotNull null
        runCatching {
            WeighAfterItem(
                id = f[0], userId = f[1], foodId = f[2], title = f[3], startGrams = f[4].toDouble(),
                meal = Meal.valueOf(f[5]), date = LocalDate.parse(f[6]), startedAt = Instant.ofEpochMilli(f[7].toLong()),
            )
        }.getOrNull()
    }

private fun clean(text: String) = text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

/** Foods waiting to be weighed again, on this phone only (not synced; nothing is in the diary yet). */
@Singleton
internal class WeighAfterStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("today", Context.MODE_PRIVATE)
    private val all = MutableStateFlow(decodeWeighAfter(prefs.getString(KEY, null)))

    val items: StateFlow<List<WeighAfterItem>> = all.asStateFlow()

    fun get(id: Id): WeighAfterItem? = all.value.firstOrNull { it.id == id }

    fun add(item: WeighAfterItem) = change { list -> list.filterNot { it.id == item.id } + item }

    fun remove(id: Id) = change { list -> list.filterNot { it.id == id } }

    private fun change(edit: (List<WeighAfterItem>) -> List<WeighAfterItem>) {
        all.update(edit)
        prefs.edit().putString(KEY, encodeWeighAfter(all.value)).apply()
    }

    private companion object {
        const val KEY = "weigh_after"
    }
}
