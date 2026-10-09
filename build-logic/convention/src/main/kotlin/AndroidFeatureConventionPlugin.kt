import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * A feature module (feature/...): screens with Compose, Hilt, and access to the
 * shared building blocks in core/model, core/data and core/ui.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("foodapp.android.library")
            extensions.configure<LibraryExtension> {
                configureCompose(this)
            }
            configureHilt()
            dependencies.add("api", dependencies.project(mapOf("path" to ":core:model")))
            dependencies.add("implementation", dependencies.project(mapOf("path" to ":core:data")))
            dependencies.add("implementation", dependencies.project(mapOf("path" to ":core:ui")))
            listOf(
                "androidx-lifecycle-runtime-compose",
                "androidx-lifecycle-viewmodel-compose",
                "androidx-navigation-compose",
                "hilt-navigation-compose",
                "kotlinx-coroutines-android",
            ).forEach { dependencies.add("implementation", libs.lib(it)) }
        }
    }
}
