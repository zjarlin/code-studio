package site.addzero.studio.contract

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import site.addzero.platform.lowcode.generator.LsiConventionFile
import site.addzero.platform.lowcode.generator.LsiConventionFileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

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
    fun `旧瞬态属性缺省类型时按草稿处理`() {
        val property = json.decodeFromString<TransientPropertyCommand>(
            """{"propertyCode":"displayName","label":"显示名称","kotlinType":"kotlin.String"}""",
        )

        assertEquals(TransientKind.DRAFT, property.kind)
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

    @Test
    fun `目录约定使用稳定键并确定性排序`() {
        val contribution = listOf(
            LsiCatalogEntry(
                routeKey = "studio.library",
                elementKey = "studio.library.create",
                parentKey = "studio.library",
                kind = LsiCatalogEntryKind.ELEMENT,
                name = "新建",
                permissions = listOf("POST:/studio/library/**", "GET:/studio/library/**"),
            ),
            LsiCatalogEntry(
                routeKey = "studio.library",
                path = "/console/studio/library",
                parentKey = "studio",
                kind = LsiCatalogEntryKind.ROUTE,
                name = "库",
            ),
            LsiCatalogEntry(
                routeKey = "studio",
                path = "/console/studio/library",
                kind = LsiCatalogEntryKind.SCENE,
                name = "Studio",
            ),
        )

        val encoded = CatalogContributions.encode(contribution)
        val decoded = CatalogContributions.decode(encoded)
        val entries = CatalogContributions.resolve(listOf(decoded))

        assertEquals(listOf("studio", "studio.library", "studio.library.create"), entries.map(LsiCatalogEntry::key))
        assertEquals(
            listOf("GET:/studio/library/**", "POST:/studio/library/**"),
            entries.last().permissions,
        )
        assertEquals(encoded, CatalogContributions.encode(decoded))
    }

    @Test
    fun `目录约定拒绝孤立元素和无效父场景`() {
        val orphan = listOf(
            LsiCatalogEntry(
                routeKey = "studio.library",
                elementKey = "studio.library.create",
                parentKey = "studio.library",
                kind = LsiCatalogEntryKind.ELEMENT,
                name = "新建",
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            CatalogContributions.resolve(listOf(orphan))
        }

        val invalidParent = listOf(
            LsiCatalogEntry(
                routeKey = "studio",
                path = "/console/studio/library",
                kind = LsiCatalogEntryKind.SCENE,
                name = "Studio",
            ),
            LsiCatalogEntry(
                routeKey = "studio.library",
                path = "/console/studio/library",
                parentKey = "studio.api-docs",
                kind = LsiCatalogEntryKind.ROUTE,
                name = "库",
            ),
            LsiCatalogEntry(
                routeKey = "studio.api-docs",
                path = "/console/studio/api-docs",
                parentKey = "studio",
                kind = LsiCatalogEntryKind.ROUTE,
                name = "API 文档",
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            CatalogContributions.resolve(listOf(invalidParent))
        }
    }
}
