// Android consumer verification: an AGP library module (the shape of a real
// app's module) depending on the published kmatch from Maven Central, with
// unit tests asserting golden values. Built and tested in CI on the runner's
// preinstalled Android SDK.
plugins {
    id("com.android.library") version "8.7.3"
    kotlin("android") version "2.2.21"
}

android {
    namespace = "io.github.likhithsj.kmatch.consumercheck"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation("io.github.likhithsj:kmatch:0.3.1")
    testImplementation(kotlin("test"))
}
