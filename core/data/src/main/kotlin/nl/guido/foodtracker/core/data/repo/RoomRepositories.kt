package nl.guido.foodtracker.core.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.guido.foodtracker.core.data.db.FoodDao
import nl.guido.foodtracker.core.data.db.LogEntryDao
import nl.guido.foodtracker.core.data.db.ProfileDao
import nl.guido.foodtracker.core.data.db.RecipeDao
import nl.guido.foodtracker.core.data.db.toEntity
import nl.guido.foodtracker.core.data.db.toModel
import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.Favourite
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.Id
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.model.RecipeVariant
import nl.guido.foodtracker.core.model.UserProfile
import nl.guido.foodtracker.core.model.WeighIn
import java.time.LocalDate
import javax.inject.Inject

private fun now() = System.currentTimeMillis()

internal class RoomDiaryRepository @Inject constructor(private val dao: LogEntryDao) : DiaryRepository {
    override fun entries(userId: Id, date: LocalDate) = entriesBetween(userId, date, date)
    override fun entriesBetween(userId: Id, from: LocalDate, to: LocalDate): Flow<List<LogEntry>> =
        dao.between(userId, from.toString(), to.toString()).map { rows -> rows.map { it.toModel() } }
    override fun recent(userId: Id, limit: Int): Flow<List<LogEntry>> =
        dao.recent(userId, limit).map { rows -> rows.map { it.toModel() } }
    override suspend fun save(entry: LogEntry) = dao.upsert(entry.toEntity(now()))
    override suspend fun delete(id: Id) = dao.markDeleted(id, now())
}

internal class RoomFoodRepository @Inject constructor(private val dao: FoodDao) : FoodRepository {
    override suspend fun get(id: Id) = dao.get(id)?.toModel()
    override suspend fun byBarcode(barcode: String) = dao.byBarcode(barcode)?.toModel()
    override fun search(text: String, limit: Int): Flow<List<Food>> =
        dao.search(text.trim(), limit).map { rows -> rows.map { it.toModel() } }
    override suspend fun save(food: Food) = dao.upsert(food.toEntity(now()))
    override suspend fun saveAll(foods: List<Food>) {
        val time = now()
        dao.upsertAll(foods.map { it.toEntity(time) })
    }
}

internal class RoomRecipeRepository @Inject constructor(private val dao: RecipeDao) : RecipeRepository {
    override fun recipes(householdId: Id): Flow<List<Recipe>> =
        dao.recipes(householdId).map { rows -> rows.map { it.toModel() } }
    override suspend fun get(id: Id) = dao.get(id)?.toModel()
    override suspend fun save(recipe: Recipe) = dao.upsert(recipe.toEntity(now()))
    override suspend fun delete(id: Id) = dao.markDeleted(id, now())
    override fun variants(recipeId: Id): Flow<List<RecipeVariant>> =
        dao.variants(recipeId).map { rows -> rows.map { it.toModel() } }
    override suspend fun saveVariant(variant: RecipeVariant) = dao.upsertVariant(variant.toEntity(now()))
    override suspend fun deleteVariant(id: Id) = dao.markVariantDeleted(id, now())
    override fun batches(householdId: Id): Flow<List<Batch>> =
        dao.batches(householdId).map { rows -> rows.map { it.toModel() } }
    override suspend fun getBatch(id: Id) = dao.getBatch(id)?.toModel()
    override suspend fun saveBatch(batch: Batch) = dao.upsertBatch(batch.toEntity(now()))
    override fun favourites(userId: Id): Flow<List<Favourite>> =
        dao.favourites(userId).map { rows -> rows.map { it.toModel() } }
    override suspend fun saveFavourite(f: Favourite) = dao.upsertFavourite(f.toEntity(now()))
    override suspend fun deleteFavourite(id: Id) = dao.markFavouriteDeleted(id, now())
}

internal class RoomProfileRepository @Inject constructor(private val dao: ProfileDao) : ProfileRepository {
    override fun profile(userId: Id): Flow<UserProfile?> = dao.profile(userId).map { it?.toModel() }
    override suspend fun save(profile: UserProfile) = dao.upsert(profile.toEntity(now()))
    override fun weighIns(userId: Id): Flow<List<WeighIn>> =
        dao.weighIns(userId).map { rows -> rows.map { it.toModel() } }
    override suspend fun saveWeighIn(weighIn: WeighIn) = dao.upsertWeighIn(weighIn.toEntity(now()))
    override suspend fun deleteWeighIn(id: Id) = dao.markWeighInDeleted(id, now())
}
