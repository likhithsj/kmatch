// Menu-search integration demo: a category tree + keyword map (the shape of
// a real app's menu model) searched through kmatch, side by side with the
// approaches it replaces. Run the comparison table:
//
//   ../../gradlew -p . run
plugins {
    kotlin("jvm") version "2.2.21"
    application
}

dependencies {
    implementation("io.github.likhithsj:kmatch:0.3.1")
    // Comparison baseline only (an existing Kotlin fuzzy library).
    implementation("ca.solo-studios:kt-fuzzy:0.1.0")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("MenuSearchDemoKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}
