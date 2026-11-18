// Starbucks menu search: a Compose Multiplatform app (Android + iOS) whose
// search box is kmatch. Builds with the repository root's Gradle wrapper:
//
//   ../../gradlew -p . :composeApp:assembleDebug          (Android APK)
//   open iosApp/iosApp.xcodeproj                           (iOS, on a Mac)
//
// kmatch is the published artifact from Maven Central (see
// gradle/libs.versions.toml), exactly what an app team would depend on --
// not a project() dependency on this repository's sources.
pluginManagement {
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
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "kmatch-starbucks-menu"

include(":composeApp")
