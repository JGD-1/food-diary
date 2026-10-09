package nl.guido.foodtracker.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nl.guido.foodtracker.core.data.db.FoodDatabase
import nl.guido.foodtracker.core.data.db.MIGRATION_1_2
import nl.guido.foodtracker.core.data.repo.DiaryRepository
import nl.guido.foodtracker.core.data.repo.FoodRepository
import nl.guido.foodtracker.core.data.repo.ProfileRepository
import nl.guido.foodtracker.core.data.repo.RecipeRepository
import nl.guido.foodtracker.core.data.repo.RoomDiaryRepository
import nl.guido.foodtracker.core.data.repo.RoomFoodRepository
import nl.guido.foodtracker.core.data.repo.RoomProfileRepository
import nl.guido.foodtracker.core.data.repo.RoomRecipeRepository
import nl.guido.foodtracker.core.data.repo.SyncEntry
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FoodDatabase =
        Room.databaseBuilder(context, FoodDatabase::class.java, "food-diary.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun foodDao(db: FoodDatabase) = db.foodDao()
    @Provides fun logEntryDao(db: FoodDatabase) = db.logEntryDao()
    @Provides fun recipeDao(db: FoodDatabase) = db.recipeDao()
    @Provides fun profileDao(db: FoodDatabase) = db.profileDao()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds @Singleton abstract fun diary(impl: RoomDiaryRepository): DiaryRepository
    @Binds @Singleton abstract fun food(impl: RoomFoodRepository): FoodRepository
    @Binds @Singleton abstract fun recipe(impl: RoomRecipeRepository): RecipeRepository
    @Binds @Singleton abstract fun profile(impl: RoomProfileRepository): ProfileRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class OptionalBindingsModule {
    /** feature/sync binds the real SyncEntry; until then the app sees Optional.empty(). */
    @BindsOptionalOf abstract fun syncEntry(): SyncEntry
}
