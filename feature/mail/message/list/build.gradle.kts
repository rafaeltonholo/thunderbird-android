import org.jetbrains.kotlin.gradle.internal.config.LanguageFeature

plugins {
    id(ThunderbirdPlugins.Library.androidCompose)
    alias(libs.plugins.dev.mokkery)
}

android {
    namespace = "net.thunderbird.feature.mail.message.list"
}

kotlin {
    sourceSets.all {
        languageSettings.enableLanguageFeature(LanguageFeature.WhenGuards.name)
    }
}

dependencies {
    implementation(libs.androidx.compose.material3)
    implementation(libs.faker.core)

    implementation(projects.backend.api)
    implementation(projects.core.android.common)
    implementation(projects.core.featureflag)
    implementation(projects.core.logging.api)
    implementation(projects.core.outcome)
    implementation(projects.core.preference.api)
    implementation(projects.core.ui.compose.designsystem)
    implementation(projects.core.ui.compose.navigation)
    implementation(projects.core.ui.theme.api)
    implementation(projects.feature.account.api)
    implementation(projects.feature.mail.account.api)
    implementation(projects.feature.mail.folder.api)
    implementation(projects.feature.navigation.drawer.dropdown)
    implementation(projects.feature.search.implLegacy)
    implementation(projects.legacy.mailstore)
    implementation(projects.mail.common)
}
