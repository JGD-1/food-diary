pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "food-diary"

include(":app")
include(":core:model")
include(":core:data")
include(":core:ui")
include(":feature:food")
include(":feature:camera")
include(":feature:recipes")
include(":feature:energy")
include(":feature:today")
include(":feature:progress")
include(":feature:sync")
