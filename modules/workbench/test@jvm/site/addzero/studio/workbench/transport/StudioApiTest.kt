package site.addzero.studio.workbench.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import site.addzero.studio.contract.ApiMultipartPart
import site.addzero.studio.contract.ApiRequestCommand
import site.addzero.studio.contract.ModelPageCommand
import site.addzero.studio.contract.StudioApiFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StudioApiTest {
    @Test
    fun `类型化 POST 请求统一使用 JSON 内容类型`() = runTest {
        var requestContentType: String? = null
        val engine = MockEngine { request ->
            requestContentType = request.body.contentType?.toString()
                ?: request.headers[HttpHeaders.ContentType]
            respond(
                """{"code":0,"msg":"","data":{"rows":[],"total":0}}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = StudioApi(
            client = HttpClient(engine),
            json = Json { ignoreUnknownKeys = true },
            session = StudioSessionState(),
        )

        api.modelPage(ModelPageCommand())

        assertEquals(ContentType.Application.Json.toString(), requestContentType)
        api.close()
    }

    @Test
    fun `curl 包含 multipart 文本和文件`() {
        val curl = renderCurl(
            ApiRequestCommand(
                method = "post",
                url = "https://example.test/upload",
                multipart = listOf(
                    ApiMultipartPart(name = "description", value = "report"),
                    ApiMultipartPart(name = "file", fileName = "report.xlsx", bytes = byteArrayOf(1)),
                ),
            ),
        )

        assertEquals(true, "--form 'description=report'" in curl)
        assertEquals(true, "--form 'file=@report.xlsx'" in curl)
    }

    @Test
    fun `HTTP 200 不会覆盖业务错误码`() = runTest {
        val api = api(HttpStatusCode.OK, """{"code":403,"msg":"Forbidden","data":null}""")

        val failure = assertFailsWith<StudioApiFailure> { api.libraries() }

        assertEquals(200, failure.httpStatus)
        assertEquals(403, failure.businessCode)
        assertEquals("Forbidden", failure.message)
        api.close()
    }

    @Test
    fun `HTTP 错误与业务码同时保留`() = runTest {
        val api = api(HttpStatusCode.InternalServerError, """{"code":500,"msg":"Unavailable","data":null}""")

        val failure = assertFailsWith<StudioApiFailure> { api.libraries() }

        assertEquals(500, failure.httpStatus)
        assertEquals(500, failure.businessCode)
        api.close()
    }

    private fun api(status: HttpStatusCode, body: String): StudioApi {
        val engine = MockEngine {
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return StudioApi(
            client = HttpClient(engine),
            json = Json { ignoreUnknownKeys = true },
            session = StudioSessionState(),
        )
    }
}
