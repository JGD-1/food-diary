package nl.guido.foodtracker.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Every table has: id (UUID text), updatedAt (ms) and deleted, so the sync stream can
// push and pull changed rows ("last change wins") and deletions travel as well.
// Dates are ISO text (2026-10-09); enums are their names; nested lists are JSON.

@Entity(tableName = "food", indices = [Index("barcode"), Index("name")])
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val source: String,
    val isDrink: Boolean,
    val ownerId: String?,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(tableName = "log_entry", indices = [Index("userId", "date")])
data class LogEntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val date: String,
    val meal: String,
    val whatJson: String,
    val portionGrams: Double,
    val portionLabel: String?,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val isEstimate: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(tableName = "recipe", indices = [Index("householdId")])
data class RecipeEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val name: String,
    val ingredientsJson: String,
    val pinned: Boolean,
    val usualPortionJson: String?,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(tableName = "recipe_variant", indices = [Index("baseRecipeId")])
data class RecipeVariantEntity(
    @PrimaryKey val id: String,
    val baseRecipeId: String,
    val name: String,
    val extrasJson: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(tableName = "batch", indices = [Index("householdId")])
data class BatchEntity(
    @PrimaryKey val id: String,
    val householdId: String,
    val recipeId: String?,
    val name: String,
    val ingredientsJson: String,
    val cookedWeightG: Double,
    val cookedOn: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val birthYear: Int,
    val sex: String,
    val heightCm: Int,
    val activity: String,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val weeklyPaceKg: Double,
    val manualTargetKcal: Int?,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(tableName = "weigh_in", indices = [Index("userId", "date")])
data class WeighInEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val date: String,
    val kg: Double,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Entity(tableName = "favourite", indices = [Index("userId")])
data class FavouriteEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val recipeId: String,
    val usualPortionJson: String?,
    val updatedAt: Long,
    val deleted: Boolean = false,
)
