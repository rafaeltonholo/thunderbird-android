package net.thunderbird.core.featureflag.data.configstore.mapper

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import net.thunderbird.core.configstore.Config
import net.thunderbird.core.featureflag.data.configstore.FeatureFlagConfigData
import net.thunderbird.core.featureflag.data.configstore.FeatureFlagConfigKeys
import net.thunderbird.core.featureflag.data.configstore.RemoteCatalogConfig
import net.thunderbird.core.featureflag.model.RemoteCatalogFetchFrequency

@OptIn(ExperimentalUuidApi::class)
class FeatureFlagConfigDefinitionMapperTest {

    @Test
    fun `toConfig then fromConfig should round trip every field`() {
        // Arrange
        val testSubject = createTestSubject()
        val remoteCatalogConfig = RemoteCatalogConfig(
            enabled = false,
            fetchFrequency = RemoteCatalogFetchFrequency.Short,
        )
        val data = FeatureFlagConfigData(
            remoteCatalogConfig = remoteCatalogConfig,
            targetingKey = TARGETING_KEY,
            overrides = mapOf("flag_a" to true),
        )

        // Act
        val result = testSubject.fromConfig(testSubject.toConfig(data))

        // Assert
        assertThat(result).isEqualTo(data)
    }

    @Test
    fun `fromConfig should return the default remote catalog config when the key is missing`() {
        // Arrange
        val testSubject = createTestSubject()

        // Act
        val result = testSubject.fromConfig(Config())

        // Assert
        assertThat(result.remoteCatalogConfig).isEqualTo(RemoteCatalogConfig())
    }

    @Test
    fun `fromConfig should return a null targeting key when the key is missing`() {
        // Arrange
        val testSubject = createTestSubject()

        // Act
        val result = testSubject.fromConfig(Config())

        // Assert
        assertThat(result.targetingKey).isNull()
    }

    @Test
    fun `fromConfig should return no overrides when the stored value is not valid JSON`() {
        // Arrange
        val testSubject = createTestSubject()
        val config = Config().apply { this[FeatureFlagConfigKeys.OVERRIDES] = "not-json" }

        // Act
        val result = testSubject.fromConfig(config)

        // Assert
        assertThat(result.overrides).isEmpty()
    }

    @Test
    fun `toConfig should write the overrides even when they are empty`() {
        // Arrange
        val testSubject = createTestSubject()
        val data = FeatureFlagConfigData(overrides = emptyMap())

        // Act
        val config = testSubject.toConfig(data)

        // Assert
        assertThat(testSubject.fromConfig(config).overrides).isEmpty()
    }

    @Test
    fun `toConfig should decode back to the same overrides that were written`() {
        // Arrange
        val testSubject = createTestSubject()
        val data = FeatureFlagConfigData(overrides = mapOf("flag_a" to true, "flag_b" to false))

        // Act
        val result = testSubject.fromConfig(testSubject.toConfig(data))

        // Assert
        assertThat(result.overrides).containsOnly("flag_a" to true, "flag_b" to false)
    }

    private fun createTestSubject(): FeatureFlagConfigDefinitionMapper = FeatureFlagConfigDefinitionMapper(Json)

    private companion object {
        val TARGETING_KEY: Uuid = Uuid.parse("f2d9a9a0-6d3f-4b5e-9f4a-1d2c3b4a5e6f")
    }
}
