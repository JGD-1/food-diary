package nl.guido.foodtracker.core.data.db

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import nl.guido.foodtracker.core.model.ActivityLevel
import nl.guido.foodtracker.core.model.Batch
import nl.guido.foodtracker.core.model.BatchPortion
import nl.guido.foodtracker.core.model.Favourite
import nl.guido.foodtracker.core.model.Food
import nl.guido.foodtracker.core.model.FoodOrigin
import nl.guido.foodtracker.core.model.Ingredient
import nl.guido.foodtracker.core.model.LogEntry
import nl.guido.foodtracker.core.model.Logged
import nl.guido.foodtracker.core.model.Meal
import nl.guido.foodtracker.core.model.Nutrients
import nl.guido.foodtracker.core.model.Portion
import nl.guido.foodtracker.core.model.Recipe
import nl.guido.foodtracker.core.model.RecipeVariant
import nl.guido.foodtracker.core.model.Sex
import nl.guido.foodtracker.core.model.UserProfile
import nl.guido.foodtracker.core.model.WeighIn
import java.time.Instant
import java.time.LocalDate

internal val json = Json { ignoreUnknownKeys = true }
private val ingredientList = ListSerializer(Ingredient.serializer())

internal fun FoodEntity.toModel() = Food(
    id = id, name = name, brand = brand, barcode = barcode,
    per100g = Nutrients(kcal, protein, carbs, fat, fibre, sugar, salt),
    source = FoodOrigin.valueOf(source), isDrink = isDrink, ownerId = ownerId,
    servingG = servingG, packageG = packageG,
)

internal fun Food.toEntity(now: Long) = FoodEntity(
    id = id, name = name, brand = brand, barcode = barcode,
    kcal = per100g.kcal, protein = per100g.protein, carbs = per100g.carbs, fat = per100g.fat,
    source = source.name, isDrink = isDrink, ownerId = ownerId, updatedAt = now,
    fibre = per100g.fibre, sugar = per100g.sugar, salt = per100g.salt,
    servingG = servingG, packageG = packageG,
)

internal fun LogEntryEntity.toModel() = LogEntry(
    id = id, userId = userId, date = LocalDate.parse(date), meal = Meal.valueOf(meal),
    what = json.decodeFromString(Logged.serializer(), whatJson),
    portion = Portion(portionGrams, portionLabel),
    nutrients = Nutrients(kcal, protein, carbs, fat, fibre, sugar, salt),
    isEstimate = isEstimate, createdAt = Instant.ofEpochMilli(createdAt),
)

internal fun LogEntry.toEntity(now: Long) = LogEntryEntity(
    id = id, userId = userId, date = date.toString(), meal = meal.name,
    whatJson = json.encodeToString(Logged.serializer(), what),
    portionGrams = portion.grams, portionLabel = portion.label,
    kcal = nutrients.kcal, protein = nutrients.protein, carbs = nutrients.carbs, fat = nutrients.fat,
    isEstimate = isEstimate, createdAt = createdAt.toEpochMilli(), updatedAt = now,
    fibre = nutrients.fibre, sugar = nutrients.sugar, salt = nutrients.salt,
)

internal fun RecipeEntity.toModel() = Recipe(
    id = id, householdId = householdId, name = name,
    ingredients = json.decodeFromString(ingredientList, ingredientsJson),
    pinned = pinned,
    usualPortion = usualPortionJson?.let { json.decodeFromString(Portion.serializer(), it) },
)

internal fun Recipe.toEntity(now: Long) = RecipeEntity(
    id = id, householdId = householdId, name = name,
    ingredientsJson = json.encodeToString(ingredientList, this.ingredients),
    pinned = pinned,
    usualPortionJson = usualPortion?.let { json.encodeToString(Portion.serializer(), it) },
    updatedAt = now,
)

internal fun RecipeVariantEntity.toModel() = RecipeVariant(
    id = id, baseRecipeId = baseRecipeId, name = name,
    extras = json.decodeFromString(ingredientList, extrasJson),
)

internal fun RecipeVariant.toEntity(now: Long) = RecipeVariantEntity(
    id = id, baseRecipeId = baseRecipeId, name = name,
    extrasJson = json.encodeToString(ingredientList, extras), updatedAt = now,
)

internal fun BatchEntity.toModel() = Batch(
    id = id, householdId = householdId, recipeId = recipeId, name = name,
    ingredients = json.decodeFromString(ingredientList, ingredientsJson),
    cookedWeightG = cookedWeightG, cookedOn = LocalDate.parse(cookedOn),
    finishedOn = finishedOn?.let(LocalDate::parse),
)

internal fun Batch.toEntity(now: Long) = BatchEntity(
    id = id, householdId = householdId, recipeId = recipeId, name = name,
    ingredientsJson = json.encodeToString(ingredientList, this.ingredients),
    cookedWeightG = cookedWeightG, cookedOn = cookedOn.toString(), updatedAt = now,
    finishedOn = finishedOn?.toString(),
)

internal fun ProfileEntity.toModel() = UserProfile(
    id = id, name = name, birthYear = birthYear, sex = Sex.valueOf(sex), heightCm = heightCm,
    activity = ActivityLevel.valueOf(activity), startWeightKg = startWeightKg,
    targetWeightKg = targetWeightKg, weeklyPaceKg = weeklyPaceKg, manualTargetKcal = manualTargetKcal,
    proteinGoalG = proteinGoalG,
)

internal fun UserProfile.toEntity(now: Long) = ProfileEntity(
    id = id, name = name, birthYear = birthYear, sex = sex.name, heightCm = heightCm,
    activity = activity.name, startWeightKg = startWeightKg, targetWeightKg = targetWeightKg,
    weeklyPaceKg = weeklyPaceKg, manualTargetKcal = manualTargetKcal, updatedAt = now,
    proteinGoalG = proteinGoalG,
)

internal fun WeighInEntity.toModel() = WeighIn(id = id, userId = userId, date = LocalDate.parse(date), kg = kg)

internal fun WeighIn.toEntity(now: Long) =
    WeighInEntity(id = id, userId = userId, date = date.toString(), kg = kg, updatedAt = now)

internal fun FavouriteEntity.toModel() = Favourite(
    id = id, userId = userId, recipeId = recipeId, foodId = foodId,
    usualPortion = usualPortionJson?.let { json.decodeFromString(Portion.serializer(), it) },
)

internal fun Favourite.toEntity(now: Long) = FavouriteEntity(
    id = id, userId = userId, recipeId = recipeId, foodId = foodId,
    usualPortionJson = usualPortion?.let { json.encodeToString(Portion.serializer(), it) },
    updatedAt = now,
)

internal fun BatchPortionEntity.toModel() = BatchPortion(
    id = id, batchId = batchId, householdId = householdId, userId = userId, grams = grams, logEntryId = logEntryId,
)

internal fun BatchPortion.toEntity(now: Long) = BatchPortionEntity(
    id = id, batchId = batchId, householdId = householdId, userId = userId, grams = grams,
    logEntryId = logEntryId, updatedAt = now,
)
