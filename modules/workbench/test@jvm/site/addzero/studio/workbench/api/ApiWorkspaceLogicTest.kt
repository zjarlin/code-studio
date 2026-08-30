package site.addzero.studio.workbench.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiWorkspaceLogicTest {
    @Test
    fun `相对 API 基址使用当前浏览器 origin`() {
        assertEquals("https://example.test/admin-api", resolveBaseUrl("/admin-api", "https://example.test"))
        assertEquals("https://service.test/api", resolveBaseUrl("https://service.test/api", "https://example.test"))
    }

    @Test
    fun `OpenAPI 投影为分组操作树`() {
        val document = Json.parseToJsonElement(
            """{"paths":{"/users/{id}":{"get":{"operationId":"getUser","summary":"读取用户","tags":["用户"],"parameters":[{"name":"id","in":"path","required":true,"schema":{"type":"integer"}}],"responses":{"200":{"description":"ok"}}}}}}""",
        ).jsonObject

        val groups = collectApiGroups(document)

        assertEquals("用户", groups.single().name)
        assertEquals("getUser", groups.single().operations.single().id)
    }

    @Test
    fun `请求 URL 去重基础路径并编码参数`() {
        val url = buildRequestUrl(
            baseUrl = "https://example.test/admin-api",
            path = "/admin-api/users/{id}",
            pathValues = mapOf("id" to "A B"),
            queryValues = mapOf("keyword" to "张 三"),
        )

        assertEquals("https://example.test/admin-api/users/A%20B?keyword=%E5%BC%A0+%E4%B8%89", url)
    }

    @Test
    fun `TypeScript 生成保留路径参数和客户端选择`() {
        val operation = ApiOperation(
            id = "getUser",
            method = "get",
            path = "/users/{id}",
            summary = "读取用户",
            description = null,
            tags = listOf("用户"),
            parameters = listOf(
                ApiParameter(
                    "id",
                    "path",
                    true,
                    null,
                    Json.parseToJsonElement("""{"type":"integer"}""").jsonObject,
                ),
            ),
            requestBody = null,
            responses = JsonObject(emptyMap()),
        )

        val source = generateTypeScriptRequest(operation, TypeScriptClient.AXIOS)

        assertTrue("import request" in source)
        assertTrue("path.id" in source)
        assertTrue("method: 'GET'" in source)
    }
}
