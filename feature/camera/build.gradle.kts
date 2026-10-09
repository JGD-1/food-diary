// Stream 2: camera, label scan, scale reading
plugins {
    alias(libs.plugins.foodapp.android.feature)
}

android {
    namespace = "nl.guido.foodtracker.feature.camera"
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    // Camera preview and frames
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Reading barcodes and text on the phone (bundled models: work offline, nothing uploaded)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)

    // ScalePhotosTest: the scale reader on real photos, on a phone or emulator
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
