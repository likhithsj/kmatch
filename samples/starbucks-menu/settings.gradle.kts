// Starbucks menu search: a Compose Multiplatform app (Android + iOS) whose
// search box is kmatch. Builds with the repository root's Gradle wrapper:
//
//   ../../gradlew -p . :composeApp:assembleDebug          (Android APK)
//   open iosApp/iosApp.xcodeproj                           (iOS, on a Mac)
//
// kmatch comes from SOURCE via a composite build (not Maven Central) so the
// sample always exercises the current tree -- including `searchFold`, which
// shipped in 1.0.0. Swap the includeBuild for a plain Maven dependency once
// that version is published; nothing else changes.
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

includeBuild("../..") {
    dependencySubstitution {
        substitute(module("io.github.likhithsj:kmatch")).using(project(":"))
    }
}

include(":composeApp")
