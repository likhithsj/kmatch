plugins {
    kotlin("jvm") version "2.2.21"
    application
}

dependencies {
    // The version only matters with KMATCH_VERSION set; otherwise the
    // composite build substitutes the repository sources.
    implementation("io.github.likhithsj:kmatch:${System.getenv("KMATCH_VERSION")?.takeIf { it.isNotBlank() } ?: "1.0.0"}")
}

application {
    mainClass.set("MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx2g", "-Dfile.encoding=UTF-8")
}
