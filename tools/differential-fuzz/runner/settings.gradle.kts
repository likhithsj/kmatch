// Runs generated cases through kmatch. By default it tests THIS working tree
// (a composite build of the repository root); set KMATCH_VERSION=1.0.0 to
// test a published artifact from Maven Central instead.
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "kmatch-differential-fuzz"

if (System.getenv("KMATCH_VERSION").isNullOrBlank()) {
    includeBuild("../../..") {
        dependencySubstitution {
            substitute(module("io.github.likhithsj:kmatch")).using(project(":"))
        }
    }
}
