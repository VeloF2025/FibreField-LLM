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
        maven("https://jitpack.io") // For MLC LLM and other libraries
        maven("https://repo1.maven.org/maven2/") // Additional Maven central
    }
}

rootProject.name = "FibreField Technician"

// Main application module
include(":app")

// Core modules - foundational components
include(":core:common")
include(":core:database")
include(":core:network")
include(":core:ai")

// Domain modules - business logic
include(":domain:authentication")
include(":domain:drops")

// Feature modules - UI and presentation
include(":feature:installation")

// Infrastructure modules - system services
include(":infrastructure:sync")
include(":infrastructure:offline")