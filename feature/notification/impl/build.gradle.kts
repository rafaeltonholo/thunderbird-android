plugins {
    id(ThunderbirdPlugins.Library.kmp)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.feature.notification.api)
            implementation(projects.core.logging.api)
            implementation(projects.feature.account.api)
        }
    }
}

android {
    namespace = "net.thunderbird.feature.notification.impl"
}
