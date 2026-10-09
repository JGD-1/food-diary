package nl.guido.foodtracker.feature.food.data

import nl.guido.foodtracker.core.model.Id
import java.util.UUID

/**
 * Ids for foods that come from a public table. They are worked out from the code in that table
 * (same input, same UUID), so both phones give the same food the same id and diaries keep pointing
 * at it after a re-import. See decisions.md.
 */
internal object FoodIds {
    fun nevo(nevoCode: String): Id = nameBased("nevo:${nevoCode.trim()}")
    fun openFoodFacts(barcode: String): Id = nameBased("off:${barcode.trim()}")

    private fun nameBased(key: String): Id = UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()
}
