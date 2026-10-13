// Migration kit: replacing me.xdrop:fuzzywuzzy with kmatch behind the same
// interactor signature, plus the side-by-side evidence to run against a
// real catalog before flipping. Run the comparison table:
//
//   ../../gradlew -p . run
plugins {
    kotlin("jvm") version "2.2.21"
    application
}

dependencies {
    implementation("io.github.likhithsj:kmatch:0.3.1")
    // The library being replaced -- kept only to run both engines side by side.
    implementation("me.xdrop:fuzzywuzzy:1.4.0")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("MigrationDemoKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}
