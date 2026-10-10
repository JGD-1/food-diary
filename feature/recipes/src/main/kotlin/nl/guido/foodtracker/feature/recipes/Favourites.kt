package nl.guido.foodtracker.feature.recipes

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import nl.guido.foodtracker.core.data.repo.RecipeRepository
import nl.guido.foodtracker.core.model.Favourite
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.newId
import javax.inject.Inject

/** A recipe someone pinned, with the portion they usually eat. Personal: never shared with the household. */
data class PinnedRecipe(val recipeId: Id, val usualPortion: Portion?)

/** Each person's own pinned recipes. */
interface Favourites {
    fun pinned(userId: Id): Flow<List<PinnedRecipe>>
    suspend fun pin(userId: Id, recipeId: Id, usualPortion: Portion?)
    suspend fun unpin(userId: Id, recipeId: Id)
}

/** Pins kept in the per-person favourite table (core/data), so they sync with the person's own account only. */
internal class RoomFavourites @Inject constructor(private val recipes: RecipeRepository) : Favourites {
    override fun pinned(userId: Id): Flow<List<PinnedRecipe>> =
        recipes.favourites(userId).map { list -> list.mapNotNull { f -> f.recipeId?.let { PinnedRecipe(it, f.usualPortion) } } }

    /** Pins the recipe, or updates the usual portion if it is already pinned. */
    override suspend fun pin(userId: Id, recipeId: Id, usualPortion: Portion?) {
        val existing = find(userId, recipeId)
        recipes.saveFavourite(
            existing?.copy(usualPortion = usualPortion)
                ?: Favourite(id = newId(), userId = userId, recipeId = recipeId, usualPortion = usualPortion),
        )
    }

    override suspend fun unpin(userId: Id, recipeId: Id) {
        find(userId, recipeId)?.let { recipes.deleteFavourite(it.id) }
    }

    private suspend fun find(userId: Id, recipeId: Id): Favourite? =
        recipes.favourites(userId).first().firstOrNull { it.recipeId == recipeId }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RecipesModule {
    @Binds abstract fun favourites(impl: RoomFavourites): Favourites
}
