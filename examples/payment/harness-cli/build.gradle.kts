plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core"))
    implementation(libs.clikt)
    implementation(libs.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
}
application { mainClass = "dev.harness.cli.MainKt" }
