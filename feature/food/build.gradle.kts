// Stream 1: food data, search, restaurant estimates
plugins {
    alias(libs.plugins.foodapp.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "nl.guido.foodtracker.feature.food"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
