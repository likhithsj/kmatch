// Verifies that the published kmatch artifact is consumable from Swift
// through SKIE (https://skie.touchlab.co): the framework builds for Apple
// targets and swift/Verify.swift asserts golden values through the generated
// Swift API. Run via ../../tools -> .github/workflows/skie-sample.yml or:
//
//   ./gradlew linkReleaseFrameworkMacosArm64 && ./verify.sh   (on a Mac)
plugins {
    kotlin("multiplatform") version "2.2.21"
    id("co.touchlab.skie") version "0.10.14"
}

kotlin {
    listOf(
        macosArm64(),          // Swift assertions run against this one on CI
        iosSimulatorArm64(),   // proves the iOS framework builds with SKIE
    ).forEach { target ->
        target.binaries.framework {
            baseName = "KmatchKit"
            isStatic = true
            export("io.github.likhithsj:kmatch:0.3.1")
        }
    }

    sourceSets {
        commonMain.dependencies {
            // The published artifact from Maven Central -- exactly what any
            // iOS team would depend on. Not a project() dependency.
            api("io.github.likhithsj:kmatch:0.3.1")
        }
    }
}
