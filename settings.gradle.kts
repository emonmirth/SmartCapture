pluginManagement {
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

rootProject.name = "SmartCapture"
include(":app")
// Future modules can be added incrementally:
// include(":core:model")
// include(":core:designsystem")
// include(":feature:home")
// etc.
