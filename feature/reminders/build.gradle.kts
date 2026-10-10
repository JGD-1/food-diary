// P5: the one opt-in reminder ("Log lunch?"), switched on from Profile
plugins {
    alias(libs.plugins.foodapp.android.feature)
}

android {
    namespace = "nl.guido.foodtracker.feature.reminders"
}

dependencies {
    implementation(libs.androidx.work.runtime)
    // Notifications, and asking for the notification permission
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
