package nl.guido.foodtracker.feature.recipes.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Nutrients

/**
 * A batch still being cooked (round 4, B1): everything typed in Cook a batch so far, kept on this
 * phone so it survives leaving the screen or Android closing the app. Not shared: the finished
 * batch is. Text fields stay text, exactly as typed.
 */
@Serializable
data class BatchDraft(
    val recipeId: Id? = null,
    val name: String = "",
    val lines: List<DraftLine> = emptyList(),
    val cookedText: String = "",
    val panId: Id? = null,
    val piecesText: String = "",
    /** ISO date. */
    val cookedOn: String,
    val scale: Double = 1.0,
    val saveToRecipe: Boolean = false,
    /** "The pot stays on the scale" (B2) and the empty pot's weight. */
    val potOnScale: Boolean = false,
    val emptyPotText: String = "",
) {
    /** Worth keeping: something was typed or added. */
    val hasContent: Boolean
        get() = name.isNotBlank() || lines.isNotEmpty() || cookedText.isNotBlank()

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Null for an unreadable or missing draft. */
        fun decode(text: String?): BatchDraft? =
            text?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

/** One ingredient line of a [BatchDraft]. */
@Serializable
data class DraftLine(
    val foodId: Id,
    val name: String,
    val per100g: Nutrients,
    val gramsText: String = "",
    val amountLabel: String? = null,
) {
    fun toEditable() = EditableIngredient(foodId, name, per100g, gramsText, amountLabel)

    companion object {
        fun of(line: EditableIngredient) = DraftLine(line.foodId, line.name, line.per100g, line.gramsText, line.amountLabel)
    }
}

/**
 * "The pot stays on the scale" (B2): this ingredient is the reading minus the empty pot and
 * everything already in it. Null when the reading isn't more than that (then grams are typed).
 */
fun ingredientFromPot(reading: Double, emptyPot: Double, alreadyIn: Double): Double? =
    (reading - emptyPot - alreadyIn).takeIf { it > 0.0 }

/** What the scale should show now with the pot on it: the empty pot plus every ingredient so far. */
fun potReadingNow(emptyPot: Double, lines: List<EditableIngredient>): Double =
    emptyPot + lines.mapNotNull { it.grams }.sum()
