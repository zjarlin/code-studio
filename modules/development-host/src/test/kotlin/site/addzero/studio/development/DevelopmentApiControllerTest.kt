package site.addzero.studio.development

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson3.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

class DevelopmentApiControllerTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `内置 Hello 接口与 OpenAPI 文档一致`() = testApplication {
        application {
            install(ContentNegotiation) {
                jackson()
            }
            routing {
                DevelopmentApiController.install(this)
            }
        }

        val openApiResponse = client.get("/v3/api-docs")
        val openApi = objectMapper.readTree(openApiResponse.body<String>())
        val documentedPath = "/hello"
        val operation = openApi["paths"][documentedPath]["get"]
        val responseSchema = operation["responses"]["200"]["content"]["application/json"]["schema"]
        val messageSchema = openApi["components"]["schemas"]["ExampleHelloResponse"]["properties"]["message"]
        val helloResponse = client.get(documentedPath)
        val hello = objectMapper.readTree(helloResponse.body<String>())

        assertEquals(HttpStatusCode.OK, openApiResponse.status)
        assertEquals("/hello", documentedPath)
        assertEquals(HttpStatusCode.OK, helloResponse.status)
        assertEquals("application/json", helloResponse.headers[HttpHeaders.ContentType]?.substringBefore(';'))
        assertEquals("Hello, world!", hello["message"].asString())
        assertEquals("Example", hello["category"].asString())
        assertEquals(1, hello["value"].asInt())
        assertEquals("/studio/favicon.svg", hello["imagePath"].asString())
        assertEquals("3.1.1", openApi["openapi"].asString())
        assertEquals("getHello", operation["operationId"].asString())
        assertEquals("#/components/schemas/ExampleHelloResponse", responseSchema["\$ref"].asString())
        assertEquals("string", messageSchema["type"].asString())
    }

    @Test
    fun `OpenAPI 包含 Studio Library 客户端契约`() = testApplication {
        application {
            routing {
                DevelopmentApiController.install(this)
            }
        }

        val openApiResponse = client.get("/v3/api-docs")
        val openApi = objectMapper.readTree(openApiResponse.body<String>())
        val paths = openApi["paths"]
        val listOperation = paths["/studio/api/lowcode/library/page"]["get"]
        val validateOperation = paths["/studio/api/lowcode/library/validate"]["post"]
        val addOperation = paths["/studio/api/lowcode/library/add"]["post"]
        val configOperation = paths["/studio/config"]["get"]

        assertEquals(HttpStatusCode.OK, openApiResponse.status)
        assertEquals("listLibraries", listOperation["operationId"].asString())
        assertEquals("validateLibrary", validateOperation["operationId"].asString())
        assertEquals("addLibrary", addOperation["operationId"].asString())
        assertEquals("getStudioConfig", configOperation["operationId"].asString())
        assertEquals(
            "#/components/schemas/LibraryCommand",
            addOperation["requestBody"]["content"]["application/json"]["schema"]["\$ref"].asString(),
        )
        assertEquals(
            "#/components/schemas/LibraryPage",
            listOperation["responses"]["200"]["content"]["application/json"]["schema"]
                ["properties"]["data"]["\$ref"].asString(),
        )
    }

    @Test
    fun `开发 Studio 同时公开元数据和接口工作台`() {
        val config = developmentStudioConfig("example-app")

        assertEquals("/v3/api-docs", config.openApiPath)
        assertEquals(setOf("metadata", "api"), config.capabilities)
    }
}
