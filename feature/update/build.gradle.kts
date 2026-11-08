// Round 13: the app updates itself from the home update server on the PC (tools/local/serve.ps1).
plugins {
    alias(libs.plugins.foodapp.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "nl.guido.foodtracker.feature.update"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    // FileProvider, to hand the downloaded APK to Android's install screen
    implementation(libs.androidx.core.ktx)
}
