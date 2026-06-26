package net.thunderbird.gradle.plugin.featureflag

import com.android.build.api.dsl.ApplicationExtension
import javax.inject.Inject
import net.thunderbird.gradle.plugin.featureflag.FeatureFlagPlugin.Companion.FEATURE_FLAG_MODULE_PATH
import net.thunderbird.gradle.plugin.featureflag.schema.SchemaValidator
import net.thunderbird.gradle.plugin.featureflag.task.GenerateFeatureFlagKeyEnumsTask
import net.thunderbird.gradle.plugin.featureflag.task.GenerateFeatureFlagKeyEnumsTask.FeatureFlagKeyEnumsExtension
import net.thunderbird.gradle.plugin.featureflag.task.registerTask
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.newInstance

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
        }
    }

    private val Project.isAppProject: Boolean get() = extensions.findByType<ApplicationExtension>() != null

    private val Project.isRootProject: Boolean
        get() = this == rootProject

    private fun Project.applyForRootProject(extension: FeatureFlagExtension) {
        logger.lifecycle("[feature-flag] Applied on root project: $this")

        val schemaValidator = SchemaValidator(validateFormats = extension.validateFormats.orElse(true).get())
        when (val result = schemaValidator.validate(
            schemaFile = extension.schema.asFile.get(),
            catalog = extension.catalog.asFile.get(),
        )) {
            is SchemaValidator.Result.Error.FileNotFound -> throw GradleException(
                "Failed to apply feature flag plugin. Reason: File '${result.path}' not found.",
            )

            is SchemaValidator.Result.Error.ValidationFailed -> {
                val detail = result.errors.joinToString(System.lineSeparator()) { error ->
                    "- $error"
                }
                val message = "Feature flag catalog JSON validation failed for ${result.catalog.path} against ${
                    result.schema.path
                }:${System.lineSeparator()}$detail"

                throw GradleException(message)
            }

            SchemaValidator.Result.Success -> registerKeyEnumGeneration(extension)
        }
    }

    /**
     * Registers the key-enum generation task on the owning [FEATURE_FLAG_MODULE_PATH] module.
     *
     * The generated enum has a fixed, global fully-qualified name, so it must be compiled exactly
     * once. Generating it into the module that every consumer already depends on lets the single
     * class flow transitively to all of them, avoiding duplicate-class failures at dex merge.
     */
    private fun Project.registerKeyEnumGeneration(extension: FeatureFlagExtension) {
        val featureFlagModule = project(FEATURE_FLAG_MODULE_PATH)
        // Defer until the owning module is configured: its Kotlin/KMP extension, used to wire the
        // generated source set, is only available after the module has been evaluated.
        featureFlagModule.afterEvaluate {
            if (tasks.findByName(GenerateFeatureFlagKeyEnumsTask.TASK_NAME) != null) {
                return@afterEvaluate
            }
            logger.debug("[feature-flag] Registering '{}' on {}", GenerateFeatureFlagKeyEnumsTask.TASK_NAME, this)
            val task = tasks.registerTask(this, extension)
            logger.debug("[feature-flag] Registered: {} into {}", task, this)
        }
    }

    private fun Project.applyForAppProject() {
        logger.lifecycle("[feature-flag] Applied on app project: $this")
    }

    private companion object {
        const val FEATURE_FLAG_MODULE_PATH = ":core:featureflag"
    }
}

abstract class FeatureFlagExtension @Inject constructor(
    objects: ObjectFactory,
) {
    abstract val catalog: RegularFileProperty
    abstract val schema: RegularFileProperty
    abstract val validateFormats: Property<Boolean>

    @get:Nested
    internal val featureFlagKeys: FeatureFlagKeyEnumsExtension = objects.newInstance<FeatureFlagKeyEnumsExtension>()

    internal fun validate() {
        when {
            !catalog.isPresent -> throw GradleException("Missing Feature flag catalog file")
            !schema.isPresent -> throw GradleException("Missing Feature flag schema file")
        }
    }

    fun featureFlagKeys(action: Action<FeatureFlagKeyEnumsExtension>) {
        action.execute(featureFlagKeys)
    }

    internal companion object {
        const val NAME = "featureFlag"
    }
}
