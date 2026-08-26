package site.addzero.platform.lowcode.generator

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

/** 模块级运行时低代码契约快照。 */
data class LowcodeRuntimeContractSnapshot(
    val routes: Map<String, LsiLowcodeRoute>,
    val contracts: Map<String, LsiLowcodeContract>,
)

/** 解析生成的 Repeatable Flyway 契约快照，用于构建校验和元数据回归。 */
object LowcodeRuntimeContractMigrationParser {
    fun parse(sql: String): LowcodeRuntimeContractSnapshot = LowcodeRuntimeContractSnapshot(
        routes = MODEL_ROW_PATTERN.findAll(sql).associate { match ->
            val modelCode = match.groupValues[1]
            modelCode to OBJECT_MAPPER.readValue(match.groupValues[2], LsiLowcodeRoute::class.java)
        },
        contracts = CONTRACT_ROW_PATTERN.findAll(sql).associate { match ->
            val contractCode = match.groupValues[1]
            contractCode to OBJECT_MAPPER.readValue(match.groupValues[2], LsiLowcodeContract::class.java)
        },
    )

    private val OBJECT_MAPPER = JsonMapper.builder()
        .addModule(kotlinModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
    private val MODEL_ROW_PATTERN = Regex(
        """\('MODEL:([^']+)',\s*'[^']+',\s*${Regex.escape(JSON_DOLLAR_QUOTE)}(.*?)${Regex.escape(JSON_DOLLAR_QUOTE)}::JSONB,\s*NULL\)""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val CONTRACT_ROW_PATTERN = Regex(
        """\('CONTRACT:([^']+)',\s*'[^']+',\s*NULL,\s*${Regex.escape(JSON_DOLLAR_QUOTE)}(.*?)${Regex.escape(JSON_DOLLAR_QUOTE)}::JSONB\)""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private const val JSON_DOLLAR_QUOTE = "\$lowcode\$"
}
