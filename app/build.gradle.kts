plugins {
    alias(libs.plugins.foodapp.android.application)
}

android {
    namespace = "nl.guido.foodtracker"

    defaultConfig {
        // The app's permanent id on the phone. Never change it, or updates stop installing.
        applicationId = "nl.guido.foodtracker"
        // Every automatic build gets a higher number, so each new APK installs over the old one.
        val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    signingConfigs {
        // One fixed key for every build (see decisions.md), so updates keep your data.
        create("shared") {
            storeFile = rootProject.file("signing/food-diary.keystore")
            storePassword = "food-diary"
            keyAlias = "food-diary"
            keyPassword = "food-diary"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:food"))
    implementation(project(":feature:camera"))
    implementation(project(":feature:recipes"))
    implementation(project(":feature:energy"))
    implementation(project(":feature:today"))
    implementation(project(":feature:progress"))
    implementation(project(":feature:sync"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
