pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "harness-payment-example"
include(":core", ":sharedUI", ":androidApp", ":desktopApp", ":harness-cli")
