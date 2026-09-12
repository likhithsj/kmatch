// Consumer verification: depends on the PUBLISHED kmatch artifact from Maven
// Central (never the local sources) and runs golden-value assertions on every
// environment CI can execute, compiling the rest. This is the project behind
// VERIFICATION.md -- a consumer's-eye proof that the library works as
// documented on each platform.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.2.21"
}

kotlin {
    jvm {
        compilerOptions {
            // Same floor as the library: consumers on Java 8+ must work.
            jvmTarget.set(JvmTarget.JVM_1_8)
            freeCompilerArgs.add("-Xjdk-release=1.8")
        }
    }

    js { nodejs() }
    wasmJs { nodejs() }

    // Test-executable native targets (per host OS in CI).
    linuxX64()
    mingwX64()
    macosArm64()
    iosSimulatorArm64()
    watchosSimulatorArm64()
    tvosSimulatorArm64()

    // Compile-proof targets: no runner hardware can execute their tests, but a
    // consumer compiling against the published artifact is verified in CI.
    iosArm64()
    iosX64()
    linuxArm64()
    androidNativeArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.likhithsj:kmatch:0.3.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// -PtestJdk=8|11|17 runs the JVM tests on that Java runtime (installed by
// actions/setup-java; see gradle.properties installations list) while the
// build itself stays on the default JDK.
val testJdk = providers.gradleProperty("testJdk")
if (testJdk.isPresent) {
    val toolchains = extensions.getByType<JavaToolchainService>()
    tasks.withType<Test>().configureEach {
        javaLauncher.set(
            toolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(testJdk.get().toInt()))
            }
        )
    }
}
