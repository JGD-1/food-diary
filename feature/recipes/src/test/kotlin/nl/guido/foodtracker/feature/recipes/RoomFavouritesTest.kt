package nl.guido.foodtracker.feature.recipes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import nl.guido.foodtracker.core.data.repo.RecipeRepository
import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.Favourite
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.model.RecipeVariant
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomFavouritesTest {
    /** Only the favourites part of the repository; enough for these tests. */
    private class FakeRepo : RecipeRepository {
        val rows = MutableStateFlow<List<Favourite>>(emptyList())
        override fun favourites(userId: Id): Flow<List<Favourite>> = rows.map { list -> list.filter { it.userId == userId } }
        override suspend fun saveFavourite(f: Favourite) { rows.value = rows.value.filterNot { it.id == f.id } + f }
        override suspend fun deleteFavourite(id: Id) { rows.value = rows.value.filterNot { it.id == id } }
        override fun recipes(householdId: Id): Flow<List<Recipe>> = emptyFlow()
        override suspend fun get(id: Id): Recipe? = null
        override suspend fun save(recipe: Recipe) = Unit
        override suspend fun delete(id: Id) = Unit
        override fun variants(recipeId: Id): Flow<List<RecipeVariant>> = emptyFlow()
        override suspend fun saveVariant(variant: RecipeVariant) = Unit
        override suspend fun deleteVariant(id: Id) = Unit
        override fun batches(householdId: Id): Flow<List<Batch>> = emptyFlow()
        override suspend fun getBatch(id: Id): Batch? = null
        override suspend fun saveBatch(batch: Batch) = Unit
    }

    private val repo = FakeRepo()
    private val favourites = RoomFavourites(repo)

    @Test
    fun pinsArePerPerson() = runTest {
        favourites.pin("guido", "pasta", Portion(250.0))
        favourites.pin("partner", "pasta", Portion(400.0))
        assertEquals(listOf(PinnedRecipe("pasta", Portion(250.0))), favourites.pinned("guido").first())
        assertEquals(listOf(PinnedRecipe("pasta", Portion(400.0))), favourites.pinned("partner").first())
    }

    @Test
    fun pinningAgainUpdatesTheUsualPortionInsteadOfAddingARow() = runTest {
        favourites.pin("guido", "pasta", null)
        favourites.pin("guido", "pasta", Portion(300.0))
        assertEquals(1, repo.rows.value.size)
        assertEquals(Portion(300.0), repo.rows.value.single().usualPortion)
    }

    @Test
    fun unpinOnlyRemovesMyPin() = runTest {
        favourites.pin("guido", "pasta", null)
        favourites.pin("partner", "pasta", null)
        favourites.unpin("guido", "pasta")
        assertEquals(emptyList<PinnedRecipe>(), favourites.pinned("guido").first())
        assertEquals(1, favourites.pinned("partner").first().size)
    }
}
