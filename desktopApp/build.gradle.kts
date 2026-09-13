plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}
kotlin {
    jvmToolchain(17)
    jvm()
    sourceSets.jvmMain.dependencies {
        implementation(project(":sharedUI"))
        implementation(compose.desktop.currentOs)
        implementation(libs.coroutines.swing)
    }
}
compose.desktop.application { mainClass = "dev.harness.desktop.MainKt" }
