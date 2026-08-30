package site.addzero.studio.contract

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import site.addzero.platform.lowcode.generator.LsiConventionFile
import site.addzero.platform.lowcode.generator.LsiConventionFileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ContractJsonSnapshotTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    @Test
    fun `旧模型响应缺省 API body 字段时保持兼容`() {
        val body = json.decodeFromString<ApiBodyCommand>("""{"schema":{}}""")

        assertEquals("application/json", body.contentType)
        assertEquals(true, body.required)
        assertEquals(ApiSchema(), body.schema)
    }

    @Test
    fun `旧 DTO 字段缺省源路径时回退到字段名`() {
        val field = json.decodeFromString<DtoFieldCommand>("""{"name":"title"}""")

        assertEquals("title", field.sourcePath)
    }

    @Test
    fun `公共包络与分页字段保持稳定`() {
        assertEquals(
            """{"code":403,"msg":"Forbidden","data":null}""",
            json.encodeToString(CommonResult<String>(403, "Forbidden")),
        )
        assertEquals(
            """{"rows":["a"],"totalRowCount":1,"totalPageCount":1}""",
            json.encodeToString(PageResult(listOf("a"), 1, 1)),
        )
        assertEquals(
            """{"valid":false,"errors":["invalid"],"warnings":[]}""",
            json.encodeToString(MetadataValidationResult(false, listOf("invalid"))),
        )
    }

    @Test
    fun `约定文件契约不包含方法入参出参`() {
        val command = ConventionFileCommand(
            featureId = 7,
            fileCode = "dailySync",
            name = "每日同步",
            className = "DailySyncJob",
            kind = ConventionFileKind.SCHEDULED_JOB,
        )
        val encoded = json.encodeToString(command)

        assertEquals(
            """{"id":null,"featureId":7,"fileCode":"dailySync","name":"每日同步","className":"DailySyncJob","kind":"SCHEDULED_JOB","status":1,"description":null}""",
            encoded,
        )
        assertFalse("parameter" in encoded)
        assertFalse("returnType" in encoded)
        assertFalse("method" in encoded)
    }

    @Test
    fun `纯 LSI 在 JVM 和 Wasm 共用同一序列化模型`() {
        val lsi = LsiConventionFile(
            fileCode = "userService",
            name = "用户服务",
            className = "UserService",
            kind = LsiConventionFileKind.SERVICE,
            packageName = "example.user",
            contributorId = "example-app",
        )

        assertEquals(
            """{"fileCode":"userService","name":"用户服务","className":"UserService","kind":"SERVICE","packageName":"example.user","contributorId":"example-app","description":null}""",
            json.encodeToString(lsi),
        )
    }
}
