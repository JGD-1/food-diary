// Stream 3: recipes, variants, favourites, batch meals
plugins {
    alias(libs.plugins.foodapp.android.feature)
    // The batch being cooked is kept on the phone as a small JSON file (round 4, B1).
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "nl.guido.foodtracker.feature.recipes"
}

dependencies {
    // "Scan a note" (round 4, H): the live camera and the phone's own text reader (bundled, works
    // offline, nothing uploaded). The same libraries the Scan screen uses, so the app doesn't grow.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)
}
