package nl.guido.foodtracker.feature.food.pieces

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import nl.guido.foodtracker.core.model.CommonPortions
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.feature.food.nevoId
import java.io.Reader
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

internal const val PIECES_ASSET = "common_pieces.csv"

/**
 * One usual piece of a food, named in English and Dutch ("1 apple" / "1 appel"), and the words for
 * two or more ("apples" / "appels"; null when pieces aren't counted, e.g. "½ avocado").
 */
internal data class Piece(
    val english: String,
    val dutch: String,
    val grams: Double,
    val englishMany: String? = null,
    val dutchMany: String? = null,
) {
    fun portion(language: String) = Portion(grams, if (language == "nl") dutch else english)

    /** True when the label can say "2 eggs". */
    fun countable(language: String) = (if (language == "nl") dutchMany else englishMany) != null

    /** "1 egg", "2 eggs" ("1 ei", "2 eieren"); one piece for counts the list can't name. */
    fun label(count: Int, language: String): String {
        val many = if (language == "nl") dutchMany else englishMany
        return if (count == 1 || many == null) portion(language).label!! else "$count $many"
    }
}

/**
 * Reads the bundled list. Lines: NEVO codes (;)|English|Dutch|grams, optionally followed by
 * |English for 2 or more|Dutch for 2 or more; # starts a comment.
 */
internal object CommonPiecesParser {
    fun parse(reader: Reader): Map<Id, Piece> = reader.useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") }
            .flatMap { line ->
                val c = line.split('|')
                require(c.size == 4 || c.size == 6) { "Pieces line has ${c.size} columns: $line" }
                val piece = Piece(
                    c[1].trim(), c[2].trim(), c[3].trim().toDouble(),
                    c.getOrNull(4)?.trim()?.ifEmpty { null }, c.getOrNull(5)?.trim()?.ifEmpty { null },
                )
                c[0].split(';').map { it.trim() }.filter { it.isNotEmpty() }.map { nevoId(it) to piece }
            }
            .toMap()
    }
}

/** The bundled common pieces for NEVO foods ("1 apple ≈ 150 g"), named in the phone's language. */
@Singleton
internal class BundledCommonPortions(
    private val load: suspend () -> Map<Id, Piece>,
    private val phoneLanguage: () -> String = { Locale.getDefault().language },
) : CommonPortions {
    @Inject constructor(@ApplicationContext context: Context) : this({
        withContext(Dispatchers.IO) { context.assets.open(PIECES_ASSET).bufferedReader().use { CommonPiecesParser.parse(it) } }
    })

    private val mutex = Mutex()
    private var pieces: Map<Id, Piece>? = null

    override suspend fun forFood(food: Food): List<Portion> = listOfNotNull(piece(food)?.portion(language()))

    /** The common piece of a NEVO food, with its words for counting (the shared food picker uses them). */
    suspend fun piece(food: Food): Piece? {
        if (food.source != FoodOrigin.NEVO) return null
        val all = mutex.withLock { pieces ?: load().also { pieces = it } }
        return all[food.id]
    }

    fun language(): String = phoneLanguage()
}
