// Round 11: the Sport tab (type what you did and the kcal burned, or read it from other apps)
plugins {
    alias(libs.plugins.foodapp.android.feature)
}

android {
    namespace = "nl.guido.foodtracker.feature.sport"
}

dependencies {
    // Read sport from other apps (Garmin, …) through Health Connect; read-only
    implementation(libs.androidx.health.connect)
    // Asking for the Health Connect permissions, and the "why" screen
    implementation(libs.androidx.activity.compose)
}
