plugins {
    id(ThunderbirdPlugins.Library.androidCompose)
}

android {
    namespace = "net.thunderbird.feature.thundermail.thunderbird"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(projects.feature.thundermail.api)
    implementation(projects.core.ui.compose.theme2)
    debugImplementation(projects.core.ui.compose.designsystem)
}

codeCoverage {
    branchCoverage = 0
    lineCoverage = 0
}
