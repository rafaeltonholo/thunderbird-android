package net.thunderbird.core.featureflag.data

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.eygraber.uri.Uri
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import net.thunderbird.core.featureflag.data.configstore.FeatureFlagConfigData
import net.thunderbird.core.featureflag.data.configstore.FeatureFlagConfigStore
import net.thunderbird.core.featureflag.data.configstore.RemoteCatalogCacheMetadata
import net.thunderbird.core.featureflag.data.configstore.RemoteCatalogConfig
import net.thunderbird.core.featureflag.keys.GeneratedFeatureFlagKey.MESSAGE_VIEW_ACTION_EXPORT_EML
import net.thunderbird.core.featureflag.model.AppVariantOverridesRawType
import net.thunderbird.core.featureflag.model.BaseAppVariantOverrides
import net.thunderbird.core.featureflag.model.FlagRegistryOverride
import net.thunderbird.core.featureflag.serialization.FlagRegistryOverrideSerializer
import net.thunderbird.core.file.FileSystemManager
import net.thunderbird.core.file.WriteMode
import net.thunderbird.core.logging.LogLevel
import net.thunderbird.core.logging.testing.TestLogger

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteFeatureFlagCatalogDataSourceTest {

    @Test
    fun `load should return null when the user disabled the remote catalog`() = runTest(UnconfinedTestDispatcher()) {
        // Arrange
        val configStore = FakeFeatureFlagConfigStore(
            FeatureFlagConfigData(remoteCatalogConfig = RemoteCatalogConfig(enabled = false)),
        )
        val logger = TestLogger()
        val testSubject = createTestSubject(engine = headOnlyEngine(), configStore = configStore, logger = logger)

        // Act
        val result = testSubject.load()

        // Assert
        assertThat(result).isNull()
        assertThat(logger.events.any { it.level == LogLevel.DEBUG }).isTrue()
    }

    @Test
    fun `load should download and cache the catalog when the cache metadata changed`() =
        runTest(UnconfinedTestDispatcher()) {
            // Arrange
            val body = catalogJson(version = "remote")
            val engine = respondingEngine(headETag = "etag-1", getBody = body)
            val fileSystemManager = FakeFileSystemManager()
            val testSubject = createTestSubject(engine = engine, fileSystemManager = fileSystemManager)

            // Act
            val result = testSubject.load()

            // Assert
            val catalog = requireNotNull(result)
            assertThat(catalog.version).isEqualTo("remote")
            assertThat(catalog.flags).hasSize(1)
            assertThat(fileSystemManager.get(CACHE_URI)?.decodeToString()).isEqualTo(body)
        }

    @Test
    fun `load should persist the new cache metadata after downloading the catalog`() =
        runTest(UnconfinedTestDispatcher()) {
            // Arrange
            val engine = respondingEngine(headETag = "etag-1", getBody = catalogJson(version = "remote"))
            val configStore = FakeFeatureFlagConfigStore(FeatureFlagConfigData())
            val testSubject = createTestSubject(engine = engine, configStore = configStore)

            // Act
            testSubject.load()

            // Assert
            assertThat(configStore.current.remoteCatalogConfig.cacheMetadata?.eTag).isEqualTo("etag-1")
        }

    @Test
    fun `load should read from the cache when the cache metadata is unchanged`() =
        runTest(UnconfinedTestDispatcher()) {
            // Arrange
            val cachedBody = catalogJson(version = "cached")
            val fileSystemManager = FakeFileSystemManager().apply { put(CACHE_URI, cachedBody.encodeToByteArray()) }
            val configStore = FakeFeatureFlagConfigStore(
                FeatureFlagConfigData(
                    remoteCatalogConfig = RemoteCatalogConfig(
                        cacheMetadata = cacheMetadata(eTag = "etag-1"),
                    ),
                ),
            )
            val engine = respondingEngine(headETag = "etag-1", getBody = catalogJson(version = "remote"))
            val testSubject = createTestSubject(
                engine = engine,
                configStore = configStore,
                fileSystemManager = fileSystemManager,
            )

            // Act
            val result = testSubject.load()

            // Assert
            assertThat(result?.version).isEqualTo("cached")
        }

    @Test
    fun `load should return null when the cache file cannot be written`() = runTest(UnconfinedTestDispatcher()) {
        // Arrange
        val engine = respondingEngine(headETag = "etag-1", getBody = catalogJson())
        val fileSystemManager = FakeFileSystemManager().apply { openSinkReturnsNull = true }
        val logger = TestLogger()
        val testSubject = createTestSubject(engine = engine, fileSystemManager = fileSystemManager, logger = logger)

        // Act
        val result = testSubject.load()

        // Assert
        assertThat(result).isNull()
        assertThat(logger.events.any { it.level == LogLevel.DEBUG }).isTrue()
    }

    @Test
    fun `load should return null when the cache file cannot be read`() = runTest(UnconfinedTestDispatcher()) {
        // Arrange
        val configStore = FakeFeatureFlagConfigStore(
            FeatureFlagConfigData(
                remoteCatalogConfig = RemoteCatalogConfig(
                    cacheMetadata = cacheMetadata(eTag = "etag-1"),
                ),
            ),
        )
        val engine = respondingEngine(headETag = "etag-1", getBody = catalogJson())
        val fileSystemManager = FakeFileSystemManager().apply { openSourceReturnsNull = true }
        val logger = TestLogger()
        val testSubject = createTestSubject(
            engine = engine,
            configStore = configStore,
            fileSystemManager = fileSystemManager,
            logger = logger,
        )

        // Act
        val result = testSubject.load()

        // Assert
        assertThat(result).isNull()
        assertThat(logger.events.any { it.level == LogLevel.DEBUG }).isTrue()
    }

    @Test
    fun `load should return null when the response body is not valid JSON`() = runTest(UnconfinedTestDispatcher()) {
        // Arrange
        val engine = respondingEngine(headETag = "etag-1", getBody = "not-json")
        val logger = TestLogger()
        val testSubject = createTestSubject(engine = engine, logger = logger)

        // Act
        val result = testSubject.load()

        // Assert
        assertThat(result).isNull()
        assertThat(logger.events.any { it.level == LogLevel.ERROR }).isTrue()
    }

    @Test
    fun `observe should not emit before load succeeds`() = runTest(UnconfinedTestDispatcher()) {
        // Arrange
        val testSubject = createTestSubject(engine = headOnlyEngine())

        // Act & Assert
        testSubject.observe().test {
            expectNoEvents()
        }
    }

    @Test
    fun `observe should emit the catalog after load succeeds`() = runTest(UnconfinedTestDispatcher()) {
        // Arrange
        val engine = respondingEngine(headETag = "etag-1", getBody = catalogJson(version = "remote"))
        val testSubject = createTestSubject(engine = engine)

        // Act
        testSubject.load()

        // Assert
        testSubject.observe().test {
            assertThat(awaitItem().version).isEqualTo("remote")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observe should not emit when load fails`() = runTest(UnconfinedTestDispatcher()) {
        // Arrange
        val engine = respondingEngine(headETag = "etag-1", getBody = "not-json")
        val testSubject = createTestSubject(engine = engine)

        // Act
        testSubject.load()

        // Assert
        testSubject.observe().test {
            expectNoEvents()
        }
    }

    private fun TestScope.createTestSubject(
        engine: MockEngine,
        configStore: FeatureFlagConfigStore = FakeFeatureFlagConfigStore(FeatureFlagConfigData()),
        fileSystemManager: FakeFileSystemManager = FakeFileSystemManager(),
        logger: TestLogger = TestLogger(),
    ): RemoteFeatureFlagCatalogDataSource {
        val json = createJson()
        return RemoteFeatureFlagCatalogDataSource(
            url = URL,
            cacheFileUri = CACHE_URI,
            logger = logger,
            configStore = configStore,
            fileSystemManager = fileSystemManager,
            json = json,
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) { json(json) }
            },
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    private fun headOnlyEngine(): MockEngine = MockEngine { _ ->
        respond(
            content = ByteReadChannel(ByteArray(0)),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ETag, "etag-1"),
        )
    }

    private fun respondingEngine(headETag: String, getBody: String): MockEngine = MockEngine { request ->
        when (request.method) {
            HttpMethod.Head -> respond(
                content = ByteReadChannel(ByteArray(0)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ETag, headETag),
            )

            else -> respond(content = ByteReadChannel(getBody), status = HttpStatusCode.OK)
        }
    }

    private fun createJson(): Json = Json {
        serializersModule = SerializersModule {
            contextual(
                kClass = FlagRegistryOverride::class,
                serializer = FlagRegistryOverrideSerializer(
                    k9Factory = { wrapper -> FakeRemoteAppVariantOverrides(wrapper) },
                    thunderbirdFactory = { wrapper -> FakeRemoteAppVariantOverrides(wrapper) },
                ),
            )
        }
    }

    private fun cacheMetadata(eTag: String): RemoteCatalogCacheMetadata =
        RemoteCatalogCacheMetadata(eTag = eTag, lastModified = null, contentLength = null)

    private fun catalogJson(version: String = "1.0", default: Boolean = true): String =
        // language=json
        """
        {"version":"$version","flags":[{"key":"${MESSAGE_VIEW_ACTION_EXPORT_EML.key}","default":$default}],
        "overrides":{"k9":{},"thunderbird":{}}}
        """.trimIndent()

    private companion object {
        const val URL = "https://example.com/catalog.json"
        val CACHE_URI: Uri = Uri.parse("file:///cache/remote_catalog.json")
    }
}

private class FakeRemoteAppVariantOverrides(wrapper: AppVariantOverridesRawType) : BaseAppVariantOverrides(wrapper)

private class FakeFeatureFlagConfigStore(
    initial: FeatureFlagConfigData,
) : FeatureFlagConfigStore {
    private val state = MutableStateFlow(initial)

    val current: FeatureFlagConfigData get() = state.value

    override val config: Flow<FeatureFlagConfigData> = state

    override suspend fun update(transform: (FeatureFlagConfigData?) -> FeatureFlagConfigData) {
        state.update { transform(it) }
    }

    override suspend fun clear() {
        state.value = FeatureFlagConfigData.DEFAULT
    }
}

private class FakeFileSystemManager : FileSystemManager {
    private val storage = mutableMapOf<String, ByteArray>()

    var openSinkReturnsNull: Boolean = false
    var openSourceReturnsNull: Boolean = false

    override fun openSink(uri: Uri, mode: WriteMode): RawSink? {
        if (openSinkReturnsNull) return null
        val key = uri.toString()
        return object : RawSink {
            private val collected = mutableListOf<Byte>()

            override fun write(source: Buffer, byteCount: Long) {
                repeat(byteCount.toInt()) { collected += source.readByte() }
            }

            override fun flush() {
                storage[key] = collected.toByteArray()
            }

            override fun close() = flush()
        }
    }

    override fun openSource(uri: Uri): RawSource? {
        if (openSourceReturnsNull) return null
        val bytes = storage[uri.toString()] ?: return null
        return object : RawSource {
            private val buffer = Buffer().apply { write(bytes) }

            override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
                if (buffer.size == 0L) return -1L
                val toRead = minOf(byteCount, buffer.size)
                sink.write(buffer, toRead)
                return toRead
            }

            override fun close() = Unit
        }
    }

    override fun delete(uri: Uri) = Unit

    override fun createDirectories(uri: Uri) = Unit

    fun put(uri: Uri, bytes: ByteArray) {
        storage[uri.toString()] = bytes
    }

    fun get(uri: Uri): ByteArray? = storage[uri.toString()]
}
