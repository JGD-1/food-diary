// Stream 1: food data, search, restaurant estimates
plugins {
    alias(libs.plugins.foodapp.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "nl.guido.foodtracker.feature.food"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Where the estimate service lives. Filled in via gradle.properties once Supabase is set up;
        // empty means Eat out lets you type your own number.
        buildConfigField("String", "SUPABASE_URL", quoted(providers.gradleProperty("supabaseUrl").orNull))
        buildConfigField("String", "SUPABASE_ANON_KEY", quoted(providers.gradleProperty("supabaseAnonKey").orNull))
        // Open Food Facts asks every app for a contact address in its User-Agent (Guido's choice, 9 Oct 2026).
        buildConfigField("String", "OFF_CONTACT", quoted("cptdillinger@gmail.com"))
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

fun quoted(value: String?) = "\"${value.orEmpty().replace("\\", "\\\\").replace("\"", "\\\"")}\""
