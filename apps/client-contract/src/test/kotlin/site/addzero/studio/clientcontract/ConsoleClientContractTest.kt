package site.addzero.studio.clientcontract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConsoleClientContractTest {
    @Test
    fun `元数据 Controller 的客户端路径完整生成`() {
        val source = ConsoleClientContract.generate()
        val document = Json.parseToJsonElement(source).jsonObject
        val paths = assertNotNull(document["paths"]).jsonObject
        val expectedOperations = mapOf(
            "/studio/api/lowcode/library/detail" to "get",
            "/studio/api/lowcode/library/update" to "put",
            "/studio/api/lowcode/library/delete" to "delete",
            "/studio/api/lowcode/library-feature/page" to "get",
            "/studio/api/lowcode/library-feature/create" to "post",
            "/studio/api/lowcode/model/page" to "post",
            "/studio/api/lowcode/model/preview" to "get",
            "/studio/api/lowcode/model/download" to "get",
            "/studio/api/lowcode/dto/list" to "post",
            "/studio/api/lowcode/dto/reuse-analysis" to "post",
            "/studio/api/lowcode/dto/download" to "get",
            "/studio/api/lowcode/convention-file/list" to "post",
            "/studio/api/lowcode/constant/save" to "post",
        )

        expectedOperations.forEach { (path, method) ->
            val pathItem = assertNotNull(paths[path], path).jsonObject
            assertNotNull(pathItem[method], "$method $path")
        }
        assertTrue(paths.keys.none { path -> path.contains("//") })
        assertTrue("#/components/schemas/site.addzero" !in source)
    }

    @Test
    fun `Agent 应用接口声明平台统一响应`() {
        val document = Json.parseToJsonElement(ConsoleClientContract.generate()).jsonObject
        val paths = document["paths"]?.jsonObject

        assertCommonResult(paths, "/agent/settings", "get")
        assertCommonResult(paths, "/agent/settings", "put")
        assertCommonResult(paths, "/agent/models", "get")
        assertCommonResult(paths, "/agent/conversations", "get")
        assertCommonResult(paths, "/agent/conversations", "post")
        assertCommonResult(paths, "/agent/conversations", "delete")
        assertCommonResult(paths, "/agent/conversations/model", "put")
        assertCommonResult(paths, "/agent/messages", "get")
        assertNotNull(paths?.get("/v1/responses"))
        assertNotNull(paths?.get("/agent/context-snapshots"))
    }

    private fun assertCommonResult(
        paths: JsonObject?,
        path: String,
        method: String,
    ) {
        val pathItem = assertNotNull(paths?.get(path)).jsonObject
        val operation = assertNotNull(pathItem[method]).jsonObject
        assertEquals("application", operation["x-client-base"]?.jsonPrimitive?.content)
        val schema = operation["responses"]?.jsonObject
            ?.get("200")?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("application/json")?.jsonObject
            ?.get("schema")?.jsonObject
        val properties = assertNotNull(schema?.get("properties")).jsonObject
        assertNotNull(properties["code"])
        assertNotNull(properties["msg"])
        assertNotNull(properties["data"])
    }
}
