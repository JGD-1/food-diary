import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal fun Project.configureKotlinAndroid(android: CommonExtension<*, *, *, *, *, *>) {
    android.apply {
        compileSdk = 35
        defaultConfig {
            minSdk = 29
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
    configureKotlinJvmTarget()
}

internal fun Project.configureKotlinJvmTarget() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}

internal fun Project.configureCompose(android: CommonExtension<*, *, *, *, *, *>) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    android.buildFeatures.compose = true
    val bom = libs.lib("androidx-compose-bom")
    dependencies.add("implementation", dependencies.platform(bom))
    listOf(
        "androidx-compose-ui",
        "androidx-compose-ui-tooling-preview",
        "androidx-compose-material3",
        "androidx-compose-material-icons",
    ).forEach { dependencies.add("implementation", libs.lib(it)) }
    dependencies.add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
}

internal fun Project.configureHilt() {
    pluginManager.apply("com.google.devtools.ksp")
    pluginManager.apply("com.google.dagger.hilt.android")
    dependencies.add("implementation", libs.lib("hilt-android"))
    dependencies.add("ksp", libs.lib("hilt-compiler"))
}

internal fun Project.addUnitTestLibraries() {
    dependencies.add("testImplementation", libs.lib("junit"))
    dependencies.add("testImplementation", libs.lib("kotlinx-coroutines-test"))
}
