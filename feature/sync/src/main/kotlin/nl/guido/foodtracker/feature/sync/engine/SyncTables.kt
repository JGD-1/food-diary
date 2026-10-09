package nl.guido.foodtracker.feature.sync.engine

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.long

/** One row as read from or written to the phone database: column name → String, Long, Double, Boolean or null. */
typealias Row = Map<String, Any?>

enum class ColType { TEXT, LONG, DOUBLE, BOOL }

/** A column: its name on the phone (Room) and online (Supabase). */
data class Col(val local: String, val remote: String, val type: ColType)

/** Which rows on this phone belong to the signed-in person, and so are theirs to sync. */
enum class Scope {
    /** Rows whose [SyncTable.ownerColumn] is the user id. */
    USER,

    /** Rows whose [SyncTable.ownerColumn] is the household id. */
    HOUSEHOLD,

    /** Recipe variants of a recipe in the household (variants have no household column of their own). */
    HOUSEHOLD_VARIANT,
}

data class SyncTable(
    val local: String,
    val remote: String,
    val scope: Scope,
    /** Local column that holds the user or household id ([Scope.HOUSEHOLD_VARIANT]: the recipe id). */
    val ownerColumn: String,
    val columns: List<Col>,
    /** Extra SQL condition for rows that never sync (e.g. NEVO foods). */
    val localFilter: String? = null,
) {
    val ownerRemote: String get() = columns.first { it.local == ownerColumn }.remote
}

private fun text(local: String, remote: String = snake(local)) = Col(local, remote, ColType.TEXT)
private fun long(local: String, remote: String = snake(local)) = Col(local, remote, ColType.LONG)
private fun double(local: String, remote: String = snake(local)) = Col(local, remote, ColType.DOUBLE)
private fun bool(local: String, remote: String = snake(local)) = Col(local, remote, ColType.BOOL)

internal fun snake(camel: String): String = camel.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()

private val stamps = listOf(long("updatedAt"), bool("deleted"))

/**
 * Everything that syncs, in push order (recipes before their variants).
 * Must match core/data/db/Entities.kt and supabase/migrations. A table missing on the phone is skipped.
 */
object SyncTables {
    val profile = SyncTable(
        "profile", "profiles", Scope.USER, "id",
        listOf(
            text("id"), text("name"), long("birthYear"), text("sex"), long("heightCm"), text("activity"),
            double("startWeightKg"), double("targetWeightKg"), double("weeklyPaceKg"), long("manualTargetKcal"),
        ) + stamps,
    )
    val logEntry = SyncTable(
        "log_entry", "log_entries", Scope.USER, "userId",
        listOf(
            text("id"), text("userId"), text("date"), text("meal"), text("whatJson"), double("portionGrams"),
            text("portionLabel"), double("kcal"), double("protein"), double("carbs"), double("fat"),
            bool("isEstimate"), long("createdAt"),
        ) + stamps,
    )
    val weighIn = SyncTable(
        "weigh_in", "weigh_ins", Scope.USER, "userId",
        listOf(text("id"), text("userId"), text("date"), double("kg")) + stamps,
    )
    val food = SyncTable(
        "food", "foods", Scope.USER, "ownerId",
        listOf(
            text("id"), text("ownerId"), text("name"), text("brand"), text("barcode"), double("kcal"),
            double("protein"), double("carbs"), double("fat"), text("source"), bool("isDrink"),
        ) + stamps,
        localFilter = "source != 'NEVO'",
    )
    val recipe = SyncTable(
        "recipe", "recipes", Scope.HOUSEHOLD, "householdId",
        listOf(
            text("id"), text("householdId"), text("name"), text("ingredientsJson"), bool("pinned"),
            text("usualPortionJson"),
        ) + stamps,
    )
    val recipeVariant = SyncTable(
        "recipe_variant", "recipe_variants", Scope.HOUSEHOLD_VARIANT, "baseRecipeId",
        listOf(text("id"), text("baseRecipeId"), text("name"), text("extrasJson")) + stamps,
    )
    val favourite = SyncTable(
        "favourite", "favourites", Scope.USER, "userId",
        listOf(text("id"), text("userId"), text("recipeId"), text("usualPortionJson")) + stamps,
    )
    val batch = SyncTable(
        "batch", "batches", Scope.HOUSEHOLD, "householdId",
        listOf(
            text("id"), text("householdId"), text("recipeId"), text("name"), text("ingredientsJson"),
            double("cookedWeightG"), text("cookedOn"),
        ) + stamps,
    )

    val all = listOf(profile, logEntry, weighIn, food, recipe, recipeVariant, favourite, batch)
    val household = listOf(recipe, recipeVariant, batch)
}

/** Phone row → JSON for Supabase. */
fun SyncTable.toRemote(row: Row): JsonObject = JsonObject(
    columns.associate { col ->
        val value = row[col.local]
        col.remote to when {
            value == null -> JsonNull
            col.type == ColType.TEXT -> JsonPrimitive(value.toString())
            col.type == ColType.LONG -> JsonPrimitive((value as Number).toLong())
            col.type == ColType.DOUBLE -> JsonPrimitive((value as Number).toDouble())
            else -> JsonPrimitive(value.asBoolean())
        }
    },
)

/** Supabase JSON → phone row (Booleans stay Booleans; the database layer stores them as 0/1). */
fun SyncTable.toLocal(json: JsonObject): Row = columns.associate { col ->
    val value: JsonElement? = json[col.remote]
    col.local to when {
        value == null || value is JsonNull -> null
        col.type == ColType.TEXT -> (value as JsonPrimitive).content
        col.type == ColType.LONG -> (value as JsonPrimitive).long
        col.type == ColType.DOUBLE -> (value as JsonPrimitive).double
        else -> (value as JsonPrimitive).boolean
    }
}

internal fun Any.asBoolean(): Boolean = when (this) {
    is Boolean -> this
    is Number -> toLong() != 0L
    else -> toString() == "true" || toString() == "1"
}

val Row.id: String get() = this["id"] as String
val Row.updatedAt: Long get() = (this["updatedAt"] as Number).toLong()
