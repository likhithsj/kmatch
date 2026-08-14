plugins {
    kotlin("multiplatform") version "2.2.21"
    id("com.vanniktech.maven.publish") version "0.37.0"
}

kotlin {
    explicitApi()

    jvm()

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
    }
}
