package net.thunderbird.core.featureflag.provider

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.featureflag.FeatureFlagResult
import net.thunderbird.core.featureflag.data.FeatureFlagCatalogDataSource
import net.thunderbird.core.featureflag.keys.GeneratedFeatureFlagKey.MESSAGE_VIEW_ACTION_EXPORT_EML
import net.thunderbird.core.featureflag.model.AppVariantOverridesRawType
import net.thunderbird.core.featureflag.model.BaseAppVariantOverrides
import net.thunderbird.core.featureflag.model.FeatureFlagCatalog
import net.thunderbird.core.featureflag.model.FlagRegistry
import net.thunderbird.core.featureflag.model.FlagRegistryOverride
import net.thunderbird.core.featureflag.provider.context.FeatureFlagContext
import net.thunderbird.core.featureflag.provider.context.ImmutableFeatureFlagContext
import net.thunderbird.core.logging.testing.TestLogger

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteCatalogFeatureFlagProviderTest {

    @Test
    fun `provide should return the catalog value when the remote catalog loads`() =
        runTest(UnconfinedTestDispatcher()) {
            // Arrange
            val dataSource = FakeRemoteFeatureFlagCatalogDataSource(catalog(default = true))
            val testSubject = createTestSubject(dataSource)
            testSubject.initialize(initialContext = context())

            // Act
            val result = testSubject.provide(MESSAGE_VIEW_ACTION_EXPORT_EML)

            // Assert
            assertThat(result).isEqualTo(FeatureFlagResult.Enabled)
        }

    @Test
    fun `provide should return Unavailable when the remote catalog never loads`() =
        runTest(UnconfinedTestDispatcher()) {
            // Arrange
            val dataSource = FakeRemoteFeatureFlagCatalogDataSource(catalog = null)
            val testSubject = createTestSubject(dataSource)
            testSubject.initialize(initialContext = context())

            // Act
            val result = testSubject.provide(MESSAGE_VIEW_ACTION_EXPORT_EML)

            // Assert
            assertThat(result).isEqualTo(FeatureFlagResult.Unavailable)
        }

    @Test
    fun `initialize should log a warning when the remote catalog never loads`() =
        runTest(UnconfinedTestDispatcher()) {
            // Arrange
            val dataSource = FakeRemoteFeatureFlagCatalogDataSource(catalog = null)
            val logger = TestLogger()
            val testSubject = createTestSubject(dataSource, logger)

            // Act
            testSubject.initialize(initialContext = context())

            // Assert
            assertThat(logger.events).isNotEmpty()
        }

    private fun createTestSubject(
        dataSource: FeatureFlagCatalogDataSource,
        logger: TestLogger = TestLogger(),
    ): RemoteCatalogFeatureFlagProvider = RemoteCatalogFeatureFlagProvider(
        dataSource = dataSource,
        logger = logger,
    )

    private companion object {
        const val CATALOG_VERSION = "2026-07-30.1"
        const val TARGETING_KEY = "targeting-key"

        fun catalog(default: Boolean): FeatureFlagCatalog = FeatureFlagCatalog(
            version = CATALOG_VERSION,
            flags = listOf(FlagRegistry(key = MESSAGE_VIEW_ACTION_EXPORT_EML.key, default = default)),
            overrides = FlagRegistryOverride(
                k9 = FakeRemoteAppVariantOverrides(emptyMap()),
                thunderbird = FakeRemoteAppVariantOverrides(emptyMap()),
            ),
        )

        fun context(): FeatureFlagContext = ImmutableFeatureFlagContext(targetingKey = TARGETING_KEY)
    }
}

private class FakeRemoteFeatureFlagCatalogDataSource(
    private val catalog: FeatureFlagCatalog?,
) : FeatureFlagCatalogDataSource {
    override fun observe(): Flow<FeatureFlagCatalog> = emptyFlow()

    override suspend fun load(): FeatureFlagCatalog? = catalog
}

private class FakeRemoteAppVariantOverrides(wrapper: AppVariantOverridesRawType) : BaseAppVariantOverrides(wrapper)
