plugins {
    kotlin("multiplatform") version "2.2.21"
    id("com.vanniktech.maven.publish") version "0.37.0"
    id("org.jetbrains.dokka") version "2.2.0"
}

dokka {
    moduleName.set("kmatch")
    dokkaPublications.html {
        failOnWarning.set(false)
    }
    // The entire public API lives in commonMain; documenting only it keeps
    // the site free of per-target duplicates (and native-toolchain needs).
    dokkaSourceSets.configureEach {
        suppress.set(name != "commonMain")
        includes.from("docs/module.md")
        sourceLink {
            localDirectory.set(file("src"))
            remoteUrl("https://github.com/likhithsj/kmatch/tree/main/src")
            remoteLineSuffix.set("#L")
        }
    }
    pluginsConfiguration.html {
        footerMessage.set("kmatch — MIT licensed")
    }
}

kotlin {
    explicitApi()

    jvm {
        compilerOptions {
            // Libraries target the oldest practical bytecode so any consumer
            // JVM can load them; building on a newer JDK must not leak newer
            // class-file versions (0.3.0 shipped Java 21 bytecode by accident).
            // -Xjdk-release also pins the JDK API surface to 1.8.
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
            freeCompilerArgs.add("-Xjdk-release=1.8")
        }
    }

    js {
        nodejs()
    }

    wasmJs {
        nodejs()
    }

    // Apple
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    macosX64()
    macosArm64()
    watchosArm64()
    watchosSimulatorArm64()
    tvosArm64()
    tvosSimulatorArm64()

    // Linux / Windows
    linuxX64()
    linuxArm64()
    mingwX64()

    // Android Native
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()
    androidNativeX64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            // Benchmark comparisons only (KMATCH_BENCH=1); never shipped.
            implementation("me.xdrop:fuzzywuzzy:1.4.0")
            implementation("ca.solo-studios:kt-fuzzy:0.1.0")
        }
    }
}
