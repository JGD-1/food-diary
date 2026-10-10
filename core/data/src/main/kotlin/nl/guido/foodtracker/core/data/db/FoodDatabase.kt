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
        BatchPortionEntity::class,
    ],
    version = 3,
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

/**
 * Version 3 (findings 1-19): fibre/sugar/salt on foods and diary lines, serving and pack sizes,
 * protein goal, "Finished" batches, pinned foods (favourite.recipeId may now be empty) and the
 * shared batch_portion table. Only adds things: every existing row keeps its numbers.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("fibre", "sugar", "salt", "servingG", "packageG").forEach {
            db.execSQL("ALTER TABLE `food` ADD COLUMN `$it` REAL")
        }
        listOf("fibre", "sugar", "salt").forEach {
            db.execSQL("ALTER TABLE `log_entry` ADD COLUMN `$it` REAL")
        }
        db.execSQL("ALTER TABLE `profile` ADD COLUMN `proteinGoalG` INTEGER")
        db.execSQL("ALTER TABLE `batch` ADD COLUMN `finishedOn` TEXT")

        // SQLite cannot drop NOT NULL from a column, so favourite is rebuilt with the same rows.
        db.execSQL(
            "CREATE TABLE `favourite_new` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `recipeId` TEXT, " +
                "`usualPortionJson` TEXT, `updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, " +
                "`foodId` TEXT, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "INSERT INTO `favourite_new` (`id`, `userId`, `recipeId`, `usualPortionJson`, `updatedAt`, `deleted`) " +
                "SELECT `id`, `userId`, `recipeId`, `usualPortionJson`, `updatedAt`, `deleted` FROM `favourite`",
        )
        db.execSQL("DROP TABLE `favourite`")
        db.execSQL("ALTER TABLE `favourite_new` RENAME TO `favourite`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_favourite_userId` ON `favourite` (`userId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `batch_portion` (`id` TEXT NOT NULL, `batchId` TEXT NOT NULL, " +
                "`householdId` TEXT NOT NULL, `userId` TEXT NOT NULL, `grams` REAL NOT NULL, `logEntryId` TEXT, " +
                "`updatedAt` INTEGER NOT NULL, `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_batch_portion_batchId` ON `batch_portion` (`batchId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_batch_portion_householdId` ON `batch_portion` (`householdId`)")
    }
}
