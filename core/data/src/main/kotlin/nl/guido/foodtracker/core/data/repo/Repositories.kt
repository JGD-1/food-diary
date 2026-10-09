package nl.guido.foodtracker.core.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.model.RecipeVariant
import nl.guido.foodtracker.core.model.UserProfile
import nl.guido.foodtracker.core.model.WeighIn
import java.time.LocalDate

/** Who is using the app on this phone. The sync stream provides the real one (Google sign-in). */
data class CurrentUser(val userId: Id, val householdId: Id, val displayName: String)

interface SessionRepository {
    val currentUser: StateFlow<CurrentUser>
}

/** Someone's diary. Only ever their own entries. */
interface DiaryRepository {
    fun entries(userId: Id, date: LocalDate): Flow<List<LogEntry>>
    fun entriesBetween(userId: Id, from: LocalDate, to: LocalDate): Flow<List<LogEntry>>
    fun recent(userId: Id, limit: Int = 50): Flow<List<LogEntry>>
    suspend fun save(entry: LogEntry)
    suspend fun delete(id: Id)
}

/** Foods stored on the phone (cached Open Food Facts, NEVO, label scans, own foods). */
interface FoodRepository {
    suspend fun get(id: Id): Food?
    suspend fun byBarcode(barcode: String): Food?
    fun search(text: String, limit: Int = 50): Flow<List<Food>>
    suspend fun save(food: Food)
    suspend fun saveAll(foods: List<Food>)
}

/** Recipes, variants and batch meals, shared within the household. */
interface RecipeRepository {
    fun recipes(householdId: Id): Flow<List<Recipe>>
    suspend fun get(id: Id): Recipe?
    suspend fun save(recipe: Recipe)
    suspend fun delete(id: Id)
    fun variants(recipeId: Id): Flow<List<RecipeVariant>>
    suspend fun saveVariant(variant: RecipeVariant)
    suspend fun deleteVariant(id: Id)
    fun batches(householdId: Id): Flow<List<Batch>>
    suspend fun getBatch(id: Id): Batch?
    suspend fun saveBatch(batch: Batch)
}

/** Profile and weigh-ins. Private to each person. */
interface ProfileRepository {
    fun profile(userId: Id): Flow<UserProfile?>
    suspend fun save(profile: UserProfile)
    fun weighIns(userId: Id): Flow<List<WeighIn>>
    suspend fun saveWeighIn(weighIn: WeighIn)
    suspend fun deleteWeighIn(id: Id)
}
