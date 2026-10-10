package nl.guido.foodtracker.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM food WHERE id = :id AND deleted = 0")
    suspend fun get(id: String): FoodEntity?

    @Query("SELECT * FROM food WHERE barcode = :barcode AND deleted = 0 LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodEntity?

    @Query("SELECT * FROM food WHERE deleted = 0 AND (name LIKE '%' || :text || '%' OR brand LIKE '%' || :text || '%') ORDER BY name LIMIT :limit")
    fun search(text: String, limit: Int): Flow<List<FoodEntity>>

    @Upsert
    suspend fun upsert(food: FoodEntity)

    @Upsert
    suspend fun upsertAll(foods: List<FoodEntity>)
}

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entry WHERE userId = :userId AND date >= :from AND date <= :to AND deleted = 0 ORDER BY date, createdAt")
    fun between(userId: String, from: String, to: String): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entry WHERE userId = :userId AND deleted = 0 ORDER BY createdAt DESC LIMIT :limit")
    fun recent(userId: String, limit: Int): Flow<List<LogEntryEntity>>

    @Upsert
    suspend fun upsert(entry: LogEntryEntity)

    @Query("UPDATE log_entry SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipe WHERE householdId = :householdId AND deleted = 0 ORDER BY pinned DESC, name")
    fun recipes(householdId: String): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipe WHERE id = :id AND deleted = 0")
    suspend fun get(id: String): RecipeEntity?

    @Upsert
    suspend fun upsert(recipe: RecipeEntity)

    @Query("UPDATE recipe SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    @Query("SELECT * FROM recipe_variant WHERE baseRecipeId = :recipeId AND deleted = 0 ORDER BY name")
    fun variants(recipeId: String): Flow<List<RecipeVariantEntity>>

    @Upsert
    suspend fun upsertVariant(variant: RecipeVariantEntity)

    @Query("UPDATE recipe_variant SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun markVariantDeleted(id: String, now: Long)

    @Query("SELECT * FROM batch WHERE householdId = :householdId AND deleted = 0 ORDER BY cookedOn DESC")
    fun batches(householdId: String): Flow<List<BatchEntity>>

    @Query("SELECT * FROM batch WHERE id = :id AND deleted = 0")
    suspend fun getBatch(id: String): BatchEntity?

    @Upsert
    suspend fun upsertBatch(batch: BatchEntity)

    @Query("SELECT * FROM favourite WHERE userId = :userId AND deleted = 0")
    fun favourites(userId: String): Flow<List<FavouriteEntity>>

    @Upsert
    suspend fun upsertFavourite(favourite: FavouriteEntity)

    @Query("UPDATE favourite SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun markFavouriteDeleted(id: String, now: Long)

    @Query("SELECT * FROM batch_portion WHERE batchId = :batchId AND deleted = 0")
    fun portions(batchId: String): Flow<List<BatchPortionEntity>>

    @Query("SELECT * FROM batch_portion WHERE householdId = :householdId AND deleted = 0")
    fun householdPortions(householdId: String): Flow<List<BatchPortionEntity>>

    @Upsert
    suspend fun upsertPortion(portion: BatchPortionEntity)

    @Query("UPDATE batch_portion SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun markPortionDeleted(id: String, now: Long)

    @Query("UPDATE batch SET finishedOn = :finishedOn, updatedAt = :now WHERE id = :id")
    suspend fun finishBatch(id: String, finishedOn: String?, now: Long)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = :userId AND deleted = 0")
    fun profile(userId: String): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("SELECT * FROM weigh_in WHERE userId = :userId AND deleted = 0 ORDER BY date")
    fun weighIns(userId: String): Flow<List<WeighInEntity>>

    @Upsert
    suspend fun upsertWeighIn(weighIn: WeighInEntity)

    @Query("UPDATE weigh_in SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun markWeighInDeleted(id: String, now: Long)
}
