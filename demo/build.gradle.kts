// Browser playground for kmatch (https://likhithsj.github.io/kmatch/).
// Not published; the library itself stays zero-dependency.
plugins {
    kotlin("multiplatform")
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                outputFileName = "demo.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":"))
        }
    }
}
