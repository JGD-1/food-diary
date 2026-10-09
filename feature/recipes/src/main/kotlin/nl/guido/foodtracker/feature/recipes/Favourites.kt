package nl.guido.foodtracker.feature.recipes

import android.content.Context
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.Portion
import javax.inject.Inject
import javax.inject.Singleton

/** A recipe someone pinned, with the portion they usually eat. Personal: never shared with the household. */
data class PinnedRecipe(val recipeId: Id, val usualPortion: Portion?)

/** Each person's own pinned recipes. */
interface Favourites {
    fun pinned(userId: Id): Flow<List<PinnedRecipe>>
    suspend fun pin(userId: Id, recipeId: Id, usualPortion: Portion?)
    suspend fun unpin(userId: Id, recipeId: Id)
}

/**
 * STAND-IN until the lead adds the per-person favourite table to core/data (decisions.md, 9 Oct):
 * keeps pins in a small file on this phone. Swapped for the database version once that lands.
 */
@Singleton
internal class LocalFavourites @Inject constructor(@ApplicationContext context: Context) : Favourites {
    private val prefs = context.getSharedPreferences("recipes_favourites", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefix(userId: Id) = "$userId/"

    private fun read(userId: Id): List<PinnedRecipe> =
        prefs.all.entries
            .filter { it.key.startsWith(prefix(userId)) }
            .map { (key, value) ->
                val portion = (value as? String)?.takeIf { it.isNotEmpty() }
                    ?.let { runCatching { json.decodeFromString(Portion.serializer(), it) }.getOrNull() }
                PinnedRecipe(key.removePrefix(prefix(userId)), portion)
            }

    override fun pinned(userId: Id): Flow<List<PinnedRecipe>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read(userId)) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        send(read(userId))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    override suspend fun pin(userId: Id, recipeId: Id, usualPortion: Portion?) {
        val value = usualPortion?.let { json.encodeToString(Portion.serializer(), it) } ?: ""
        prefs.edit().putString(prefix(userId) + recipeId, value).apply()
    }

    override suspend fun unpin(userId: Id, recipeId: Id) {
        prefs.edit().remove(prefix(userId) + recipeId).apply()
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RecipesModule {
    @Binds abstract fun favourites(impl: LocalFavourites): Favourites
}
