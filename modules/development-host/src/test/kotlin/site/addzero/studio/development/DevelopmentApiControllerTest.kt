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
        val documentedPath = openApi["paths"].propertyNames().asSequence().single()
        val operation = openApi["paths"][documentedPath]["get"]
        val responseSchema = operation["responses"]["200"]["content"]["application/json"]["schema"]
        val messageSchema = openApi["components"]["schemas"]["HelloResponse"]["properties"]["message"]
        val helloResponse = client.get(documentedPath)
        val hello = objectMapper.readTree(helloResponse.body<String>())

        assertEquals(HttpStatusCode.OK, openApiResponse.status)
        assertEquals("/hello", documentedPath)
        assertEquals(HttpStatusCode.OK, helloResponse.status)
        assertEquals("application/json", helloResponse.headers[HttpHeaders.ContentType]?.substringBefore(';'))
        assertEquals("Hello, world!", hello["message"].asString())
        assertEquals("3.1.0", openApi["openapi"].asString())
        assertEquals("getHello", operation["operationId"].asString())
        assertEquals("#/components/schemas/HelloResponse", responseSchema["\$ref"].asString())
        assertEquals(hello["message"].asString(), messageSchema["example"].asString())
    }

    @Test
    fun `开发 Studio 同时公开元数据和接口工作台`() {
        val config = developmentStudioConfig("example-app")

        assertEquals("/v3/api-docs", config.openApiPath)
        assertEquals(setOf("metadata", "api"), config.capabilities)
    }
}
