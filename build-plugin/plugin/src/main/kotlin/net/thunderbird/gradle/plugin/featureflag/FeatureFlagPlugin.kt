package net.thunderbird.gradle.plugin.featureflag

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType

@Suppress("unused")
class FeatureFlagPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        val extension = target.extensions.create<FeatureFlagExtension>(FeatureFlagExtension.NAME)
        when {
            isRootProject -> afterEvaluate {
                extension.validate()
                applyForRootProject(extension)
            }

            isAppProject -> applyForAppProject()

            else -> applyForConsumerProject()
        }
    }

    private val Project.isAppProject: Boolean get() = extensions.findByType<ApplicationExtension>() != null

    private val Project.isRootProject: Boolean
        get() = this == rootProject

    private fun Project.applyForRootProject(extension: FeatureFlagExtension) {
        logger.lifecycle("[feature-flag] Applied on root project: $this")
    }

    private fun Project.applyForAppProject() {
        logger.lifecycle("[feature-flag] Applied on app project: $this")
    }

    private fun Project.applyForConsumerProject() {
        logger.lifecycle("[feature-flag] Applied on consumer project: $this")
    }
}

abstract class FeatureFlagExtension {
    abstract val catalog: RegularFileProperty
    abstract val schema: RegularFileProperty
    abstract val validateFormats: Property<Boolean>

    internal fun validate() {
        when {
            !catalog.isPresent -> throw GradleException("Missing Feature flag catalog file")
            !schema.isPresent -> throw GradleException("Missing Feature flag schema file")
        }
    }

    internal companion object {
        const val NAME = "featureFlag"
    }
}
