// Stream 7: sign-in, household, sync, export
plugins {
    alias(libs.plugins.foodapp.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "nl.guido.foodtracker.feature.sync"

    buildFeatures.buildConfig = true
    defaultConfig {
        // Filled in root gradle.properties once the Supabase project exists. Empty = sync off, app works offline.
        val url = providers.gradleProperty("supabaseUrl").orNull.orEmpty()
        val key = providers.gradleProperty("supabaseAnonKey").orNull.orEmpty()
        buildConfigField("String", "SUPABASE_URL", "\"${url.trimEnd('/')}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"$key\"")
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.room.runtime)
}
