plugins {
    id(ThunderbirdPlugins.Library.kmp)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "net.thunderbird.core.common"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.logging.implLegacy)
            implementation(projects.core.logging.api)
            implementation(projects.core.logging.implFile)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
        jvmMain.dependencies {
            implementation(libs.androidx.annotation)
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            listOf(
                "-Xexpect-actual-classes",
                "-P",
                "plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=" +
                    "net.thunderbird.core.common.io.KmpParcelize",
            ),
        )
    }
}
