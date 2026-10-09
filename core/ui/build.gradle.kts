plugins {
    alias(libs.plugins.foodapp.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "nl.guido.foodtracker.core.ui"
    buildFeatures { compose = true }
}

dependencies {
    api(project(":core:model"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.tooling.preview)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
