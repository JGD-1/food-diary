package nl.guido.foodtracker.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The database on the phone: the source of truth for everything.
 * Changing a table means raising [version] and adding a Migration (see conventions.md),
 * otherwise people lose their diary when they update the app.
 */
@Database(
    entities = [
        FoodEntity::class,
        LogEntryEntity::class,
        RecipeEntity::class,
        RecipeVariantEntity::class,
        BatchEntity::class,
        ProfileEntity::class,
        WeighInEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FoodDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun profileDao(): ProfileDao
}
