// Stream 3: recipes, variants, favourites, batch meals
plugins {
    alias(libs.plugins.foodapp.android.feature)
    // The batch being cooked is kept on the phone as a small JSON file (round 4, B1).
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "nl.guido.foodtracker.feature.recipes"
}
