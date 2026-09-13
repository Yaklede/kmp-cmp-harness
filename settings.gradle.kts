pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "kmp-cmp-harness"
include(":core", ":sharedUI", ":androidApp", ":desktopApp", ":harness-cli")
