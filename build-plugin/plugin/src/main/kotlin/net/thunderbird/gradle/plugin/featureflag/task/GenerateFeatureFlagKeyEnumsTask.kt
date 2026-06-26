package net.thunderbird.gradle.plugin.featureflag.task

import com.android.build.api.variant.AndroidComponentsExtension
import kotlinx.serialization.json.Json
import net.thunderbird.gradle.plugin.featureflag.FeatureFlagExtension
import net.thunderbird.gradle.plugin.featureflag.codegen.FeatureFlagKeyWriter
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/**
 * Gradle task that generates enum classes containing feature flag keys from a feature flag catalog.
 */
@CacheableTask
abstract class GenerateFeatureFlagKeyEnumsTask : DefaultTask() {

    /**
     * The name of the project for which feature flag key enums are being generated.
     * Used for logging purposes during the generation process.
     */
    @get:Input
    abstract val projectName: Property<String>

    /**
     * Input file containing the feature flag catalog definition in JSON format.
     *
     * The catalog defines available feature flags, their default values, context attributes,
     * and app-specific overrides for different build variants.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val flagCatalog: RegularFileProperty

    /**
     * The target directory where the generated feature flag key enum files will be written.
     * Defaults to 'build/generated/featureflags/src/commonMain/kotlin' if not specified.
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * The package name for the generated feature flag key enums.
     */
    @get:Input
    abstract val featureFlagKeysPackageName: Property<String>

    /**
     * The base name for the generated feature flag key enum class.
     *
     * This name is used by the FeatureFlagKeyWriter to create an enum that implements FeatureFlagKey,
     * containing all flag keys defined in the feature flag catalog.
     */
    @get:Input
    abstract val featureFlagKeyEnumBaseName: Property<String>

    private val writer
        get() = FeatureFlagKeyWriter(
            packageName = featureFlagKeysPackageName.get(),
            enumName = featureFlagKeyEnumBaseName.get(),
        )

    companion object {
        const val TASK_NAME = "generateFeatureFlagKeyEnums"
    }

    init {
        group = "Thunderbird Feature Flags"
        description = "Generate the feature flag keys enums"
    }

    /**
     * Gradle task action that generates feature flag key enums from a catalog file.
     *
     * Reads the feature flag catalog from the configured input file, parses it as JSON,
     * and generates a Kotlin enum class containing all feature flag keys defined in the catalog.
     * The generated enum is written to the configured output directory.
     *
     * @throws kotlinx.serialization.SerializationException if the catalog file cannot be parsed
     * @throws java.io.IOException if the catalog file cannot be read or output cannot be written
     */
    @TaskAction
    internal fun generate() {
        logger.info("[feature-flag] generating feature-flag keys enums for ${projectName.get()}")
        logger.debug("[feature-flag] reading catalog")
        val catalog = readCatalog()
        logger.debug("[feature-flag] writing feature-flag keys enum.")
        val outputDir = outputDirectory.asFile.get()
        writer.write(catalog = catalog, outputDir = outputDir)
        logger.info("[feature-flag] wrote feature-flag keys enum to $outputDir.")
    }

    /**
     * Reads and deserializes the feature flag catalog from the configured catalog file.
     *
     * @return The deserialized FeatureFlagCatalog containing version, context, flags, and overrides
     */
    internal fun readCatalog(): FeatureFlagCatalog {
        val catalog = flagCatalog.get().asFile
        val catalogText = catalog.readText(Charsets.UTF_8)
        return Json.decodeFromString<FeatureFlagCatalog>(catalogText)
    }

    /**
     * Extension configuration for generating feature flag key enums.
     *
     * Configures the output location, package name, and base name for the generated
     * enum classes that contain feature flag keys from a catalog file.
     */
    abstract class FeatureFlagKeyEnumsExtension {
        /**
         * The directory where generated feature flag key enum files will be written.
         *
         * Defaults to `build/generated/featureflags/src/commonMain/kotlin` if not explicitly configured.
         */
        abstract val outputDirectory: DirectoryProperty

        /**
         * The package name for generated feature flag key enum classes.
         *
         * Defaults to `net.thunderbird.core.featureflag.keys` if not explicitly configured.
         */
        abstract val featureFlagKeysPackageName: Property<String>

        /**
         * The base name used for generated feature flag key enum classes.
         *
         * Defaults to `GeneratedFeatureFlagKey` if not explicitly configured.
         */
        abstract val featureFlagKeyEnumBaseName: Property<String>

        init {
            featureFlagKeyEnumBaseName.convention("GeneratedFeatureFlagKey")
        }
    }
}

fun TaskContainer.registerTask(
    project: Project,
    extension: FeatureFlagExtension,
): GenerateFeatureFlagKeyEnumsTask {
    val task = register<GenerateFeatureFlagKeyEnumsTask>(GenerateFeatureFlagKeyEnumsTask.TASK_NAME) {
        projectName.set(project.name)
        featureFlagKeysPackageName.set(
            extension
                .featureFlagKeys
                .featureFlagKeysPackageName
                .orElse("net.thunderbird.core.featureflag.keys"),
        )
        flagCatalog.set(extension.catalog)
        outputDirectory.set(
            extension.featureFlagKeys.outputDirectory.orElse(
                project.layout.buildDirectory.dir(
                    "generated/featureflags/src/commonMain/kotlin",
                ),
            ),
        )
        if (extension.featureFlagKeys.featureFlagKeyEnumBaseName.isPresent) {
            featureFlagKeyEnumBaseName.set(extension.featureFlagKeys.featureFlagKeyEnumBaseName)
        }
    }

    val outputDir = task.map { it.outputDirectory }

    val kmpExtension = project.extensions.findByType<KotlinMultiplatformExtension>()
    val androidExtension = project.extensions.findByType(AndroidComponentsExtension::class.java)

    when {
        kmpExtension != null -> kmpExtension.mapOutputDir(outputDir.get())
        androidExtension != null -> androidExtension.mapOutputDir(project, task)
        else -> project.logger.lifecycle("[feature-flag] $project -> Unsupported project type.")
    }

    return task.get()
}

private fun KotlinMultiplatformExtension.mapOutputDir(outputDir: DirectoryProperty) {
    project.logger.lifecycle("[feature-flag] mapping kmp source")
    val sourceSet = targets
        .first { it.platformType == KotlinPlatformType.common }
        .compilations
        .first { it.platformType == KotlinPlatformType.common }
        .defaultSourceSet
        .kotlin

    sourceSet.srcDirs(outputDir)
}

private fun AndroidComponentsExtension<*, *, *>.mapOutputDir(
    project: Project,
    taskProvider: TaskProvider<GenerateFeatureFlagKeyEnumsTask>,
) {
    project.logger.lifecycle("[feature-flag] mapping android source")
    onVariants { variant ->
        project.logger.lifecycle("[feature-flag] mapping android source for variant = ${variant.name}")
        variant.sources.kotlin?.addGeneratedSourceDirectory(
            taskProvider = taskProvider,
            wiredWith = GenerateFeatureFlagKeyEnumsTask::outputDirectory,
        )
    }
}
