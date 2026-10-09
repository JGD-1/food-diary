package nl.guido.foodtracker.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        FavouriteEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class FoodDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun profileDao(): ProfileDao
}

/** Version 2: per-person favourite recipes. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `favourite` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, " +
                "`recipeId` TEXT NOT NULL, `usualPortionJson` TEXT, `updatedAt` INTEGER NOT NULL, " +
                "`deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_favourite_userId` ON `favourite` (`userId`)")
    }
}
