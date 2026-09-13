plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp)
}
kotlin {
    jvmToolchain(17)
    jvm()
    iosArm64()
    iosSimulatorArm64()
    androidLibrary {
        namespace = "dev.harness.core"
        compileSdk = 36
        minSdk = 24
        withHostTest {}
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.coroutines.core)
            implementation(libs.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}
