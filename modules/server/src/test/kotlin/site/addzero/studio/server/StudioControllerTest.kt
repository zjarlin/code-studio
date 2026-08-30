package site.addzero.studio.server

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson3.jackson
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.application.install
import io.ktor.server.application.call
import io.ktor.server.engine.ConnectorType
import io.ktor.server.engine.EngineConnectorConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.studio.runtime.METADATA_CONTRIBUTOR_FORMAT_VERSION
import site.addzero.studio.runtime.MetadataContributor
import site.addzero.studio.runtime.StudioAccessPolicy
import site.addzero.studio.runtime.StudioConfig
import tools.jackson.module.kotlin.jacksonObjectMapper

class StudioControllerTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `启用后服务 UI 固定配置和元数据贡献 API`() = testApplication {
        val assetPath = studioAssetPath()
        application {
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                controller(enabled = true).install(this)
            }
        }

        val rootResponse = client.get("/studio")
        val trailingSlashResponse = client.get("/studio/")
        val assetResponse = client.get(assetPath)
        val configResponse = client.get("/studio/config")
        val contributorResponse = client.get("/studio/api/contributors")
        val config = objectMapper.readTree(configResponse.body<String>())
        val contributors = objectMapper.readTree(contributorResponse.body<String>())

        assertEquals(HttpStatusCode.OK, rootResponse.status)
        assertEquals(HttpStatusCode.OK, trailingSlashResponse.status)
        assertEquals(HttpStatusCode.OK, assetResponse.status)
        assertTrue(rootResponse.body<String>().contains("import-map-loader.js"))
        assertTrue(rootResponse.body<String>().indexOf("import-map-loader.js") < rootResponse.body<String>().indexOf("web.mjs"))
        listOf(
            "web.wasm",
            "web.mjs",
            "skiko.wasm",
            "skiko.mjs",
            "import-map-loader.js",
            "vendors/@js-joda/core/dist/js-joda.esm.js",
        ).forEach { fileName ->
            assertTrue(javaClass.classLoader.getResource("studio/$fileName") != null, "server 主制品缺少 $fileName")
        }
        assertEquals(
            setOf(
                "contributorId",
                "displayName",
                "apiBaseUrl",
                "openApiPath",
                "editableContributorId",
                "capabilities",
            ),
            config.propertyNames().asSequence().toSet(),
        )
        assertEquals("example-app", config["contributorId"].asString())
        assertEquals("/example-api", config["apiBaseUrl"].asString())
        assertEquals(2, contributors.size())
        assertFalse(contributors[0]["editable"].asBoolean())
        assertTrue(contributors[1]["editable"].asBoolean())
    }

    @Test
    fun `生产默认不安装 Studio 路由`() = testApplication {
        application {
            installStudio(
                dataSource = UnconnectedDataSource(),
                config = config(enabled = false),
                accessPolicy = StudioAccessPolicy { false },
            )
        }

        val response = client.get("/studio")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `访问策略统一保护配置 API 和静态资源`() = testApplication {
        var apiInvoked = false
        val assetPath = studioAssetPath()
        application {
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                val denied = StudioAccessPolicy { false }
                val apiController = StudioApiController { route ->
                    route.post("/write") {
                        apiInvoked = true
                        val response = mapOf("status" to "unexpected")
                        call.respond(response)
                    }
                }
                controller(
                    enabled = true,
                    accessPolicy = denied,
                    apiControllers = listOf(apiController),
                ).install(this)
            }
        }

        val rootResponse = client.get("/studio")
        val configResponse = client.get("/studio/config")
        val assetResponse = client.get(assetPath)
        val apiResponse = client.post("/studio/api/write")

        assertEquals(HttpStatusCode.Forbidden, rootResponse.status)
        assertEquals(HttpStatusCode.Forbidden, configResponse.status)
        assertEquals(HttpStatusCode.Forbidden, assetResponse.status)
        assertEquals(HttpStatusCode.Forbidden, apiResponse.status)
        assertFalse(apiInvoked)
    }

    @Test
    fun `宿主 Controller 安装在统一 Studio API 边界`() = testApplication {
        application {
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                val apiController = StudioApiController { route ->
                    route.get("/ping") {
                        val response = mapOf("status" to "ok")
                        call.respond(response)
                    }
                }
                controller(enabled = true, apiControllers = listOf(apiController)).install(this)
            }
        }

        val response = client.get("/studio/api/ping")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", objectMapper.readTree(response.body<String>())["status"].asString())
    }

    @Test
    fun `使用真实监听信息生成浏览器可访问的 Studio 地址`() {
        assertEquals(
            "http://localhost:49000/studio/",
            connector(ConnectorType.HTTP, "0.0.0.0", 49000).studioUrl(),
        )
        assertEquals(
            "https://example.test:443/application/studio/",
            connector(ConnectorType.HTTPS, "example.test", 443).studioUrl("/application"),
        )
        assertEquals(
            "http://[::1]:8080/studio/",
            connector(ConnectorType.HTTP, "::1", 8080).studioUrl(),
        )
        assertEquals(null, connector(ConnectorType.UNIX, "localhost", 0).studioUrl())
    }

    private fun controller(
        enabled: Boolean,
        accessPolicy: StudioAccessPolicy = StudioAccessPolicy { true },
        apiControllers: List<StudioApiController> = emptyList(),
    ): StudioController = StudioController(
        config = config(enabled),
        accessPolicy = accessPolicy,
        contributors = listOf(
            contributor("example-library"),
            contributor("example-app", requires = listOf("example-library")),
        ),
        apiControllers = apiControllers,
    )

    private fun config(enabled: Boolean): StudioConfig = StudioConfig(
        contributorId = "example-app",
        displayName = "Example Application",
        apiBaseUrl = "/example-api",
        openApiPath = "/example-api/openapi.json",
        capabilities = setOf("metadata", "api-docs"),
        enabled = enabled,
    )

    private fun contributor(
        id: String,
        requires: List<String> = emptyList(),
    ): MetadataContributor = MetadataContributor(
        formatVersion = METADATA_CONTRIBUTOR_FORMAT_VERSION,
        id = id,
        migrationLocation = "classpath:db/studio/metadata/$id",
        requires = requires,
    )

    private fun connector(type: ConnectorType, host: String, port: Int): EngineConnectorConfig =
        object : EngineConnectorConfig {
            override val type: ConnectorType = type
            override val host: String = host
            override val port: Int = port
        }

    private fun studioAssetPath(): String {
        val index = requireNotNull(javaClass.classLoader.getResource("studio/index.html")) {
            "server 主制品缺少 Studio UI"
        }.readText()
        return requireNotNull(
            Regex("(?:src|href)=\"([^\"]+\\.(?:mjs|css))\"").find(index)?.groupValues?.get(1)?.let {
                "/studio/$it"
            },
        ) {
            "Studio UI index 没有引用构建资源"
        }
    }
}
