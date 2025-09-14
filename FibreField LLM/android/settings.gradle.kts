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
include(":core:design")

// Domain modules - business logic
include(":domain:authentication")
include(":domain:installation")
include(":domain:drops")
include(":domain:activation")
include(":domain:remediation")

// Feature modules - UI and presentation
include(":feature:authentication")
include(":feature:installation")
include(":feature:drops")
include(":feature:activation")
include(":feature:remediation")

// Infrastructure modules - system services
include(":infrastructure:sync")
include(":infrastructure:location")
include(":infrastructure:security")