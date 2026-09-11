pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "Verto"
include(":app")
include(":core:common")
include(":core:crash")
include(":core:session")
include(":core:audit")
include(":core:export")
include(":core:designsystem")
include(":feature:auth")
include(":data:database")
include(":data:preferences")

include(":data:network")
include(":data:sync")
include(":feature:inventory")
include(":feature:party")
include(":feature:invoice")
include(":feature:payment")

include(":feature:shipment")

include(":feature:reports")

include(":feature:organization")

include(":feature:profile")

include(":feature:settings")

include(":feature:notifications")

include(":feature:commission")

include(":feature:messages")

include(":feature:dashboard")
include(":feature:dashboard:api")

include(":feature:expenses")
include(":feature:management")
include(":feature:integration:optimal")

include(":data:operations")
