plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}
kotlin {
    jvmToolchain(17)
    jvm()
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "HarnessUI"; isStatic = true }
    }
    androidLibrary {
        namespace = "dev.harness.ui"
        compileSdk = 36
        minSdk = 24
        androidResources.enable = true
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
