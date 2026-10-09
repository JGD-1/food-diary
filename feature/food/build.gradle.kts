// Stream 1: food data, search, restaurant estimates
plugins {
    alias(libs.plugins.foodapp.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "nl.guido.foodtracker.feature.food"

    buildFeatures.buildConfig = true
    defaultConfig {
        // The Supabase project (root gradle.properties) runs the "estimate" function. Empty = no estimates.
        val url = providers.gradleProperty("supabaseUrl").orNull.orEmpty()
        val key = providers.gradleProperty("supabaseAnonKey").orNull.orEmpty()
        buildConfigField("String", "SUPABASE_URL", "\"${url.trimEnd('/')}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"$key\"")
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
