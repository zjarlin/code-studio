package site.addzero.openapi.compiler

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

/**
 * 让 OpenAPI Schema 与平台 JSON 序列化规则保持一致。
 *
 * LSI 继续使用 integer/int64 表达逻辑 Long；这里只处理公开文档中的 Schema 节点，
 * 不递归 example、default 或扩展字段内的业务对象。
 */
internal object JsonWireSchemaNormalizer {
    fun normalize(document: ObjectNode) {
        document.objectValue("components")?.normalizeComponents()
        document.forEachObjectValue("paths") { pathItem -> pathItem.normalizePathItem() }
        document.forEachObjectValue("webhooks") { pathItem -> pathItem.normalizePathItem() }
    }

    private fun ObjectNode.normalizeComponents() {
        forEachObjectValue("schemas") { schema -> schema.normalizeSchema() }
        forEachObjectValue("parameters") { parameter -> parameter.normalizeParameter() }
        forEachObjectValue("headers") { header -> header.normalizeHeader() }
        forEachObjectValue("requestBodies") { requestBody -> requestBody.normalizeRequestBody() }
        forEachObjectValue("responses") { response -> response.normalizeResponse() }
        forEachObjectValue("callbacks") { callback -> callback.normalizeCallback() }
        forEachObjectValue("pathItems") { pathItem -> pathItem.normalizePathItem() }
    }

    private fun ObjectNode.normalizePathItem() {
        forEachArrayObject("parameters") { parameter -> parameter.normalizeParameter() }
        OPENAPI_OPERATION_KEYS.forEach { key ->
            objectValue(key)?.normalizeOperation()
        }
    }

    private fun ObjectNode.normalizeOperation() {
        forEachArrayObject("parameters") { parameter -> parameter.normalizeParameter() }
        objectValue("requestBody")?.normalizeRequestBody()
        forEachObjectValue("responses") { response -> response.normalizeResponse() }
        forEachObjectValue("callbacks") { callback -> callback.normalizeCallback() }
    }

    private fun ObjectNode.normalizeCallback() {
        properties().forEach { (_, value) ->
            (value as? ObjectNode)?.normalizePathItem()
        }
    }

    private fun ObjectNode.normalizeParameter() {
        val longSchema = objectValue("schema")?.normalizeSchema() == true
        if (longSchema) {
            normalizeDirectExamples()
        }
        normalizeContent()
    }

    private fun ObjectNode.normalizeHeader() {
        val longSchema = objectValue("schema")?.normalizeSchema() == true
        if (longSchema) {
            normalizeDirectExamples()
        }
        normalizeContent()
    }

    private fun ObjectNode.normalizeRequestBody() {
        normalizeContent()
    }

    private fun ObjectNode.normalizeResponse() {
        forEachObjectValue("headers") { header -> header.normalizeHeader() }
        normalizeContent()
    }

    private fun ObjectNode.normalizeContent() {
        forEachObjectValue("content") { mediaType -> mediaType.normalizeMediaType() }
    }

    private fun ObjectNode.normalizeMediaType() {
        val longSchema = objectValue("schema")?.normalizeSchema() == true
        if (longSchema) {
            normalizeDirectExamples()
        }
        forEachObjectValue("encoding") { encoding ->
            encoding.forEachObjectValue("headers") { header -> header.normalizeHeader() }
        }
    }

    private fun ObjectNode.normalizeSchema(): Boolean {
        val longSchema = normalizeLongType()
        if (longSchema) {
            normalizeSchemaLiterals()
        }
        SCHEMA_MAP_KEYS.forEach { key ->
            forEachObjectValue(key) { schema -> schema.normalizeSchema() }
        }
        SCHEMA_SINGLE_KEYS.forEach { key ->
            objectValue(key)?.normalizeSchema()
        }
        SCHEMA_ARRAY_KEYS.forEach { key ->
            forEachArrayObject(key) { schema -> schema.normalizeSchema() }
        }
        return longSchema
    }

    private fun ObjectNode.normalizeLongType(): Boolean {
        if (get("format")?.asString() != "int64") {
            return false
        }
        val typeNode = get("type") ?: return false
        val normalized = when {
            typeNode.isString && typeNode.asString() == "integer" -> {
                put("type", "string")
                true
            }
            typeNode.isArray && typeNode.values().asSequence().any { value -> value.asString() == "integer" } -> {
                val wireTypes = typeNode.values().asSequence()
                    .map { value -> if (value.asString() == "integer") "string" else value.asString() }
                    .distinct()
                    .toList()
                val types = putArray("type")
                wireTypes.forEach(types::add)
                true
            }
            else -> false
        }
        if (normalized) {
            remove("format")
        }
        return normalized
    }

    private fun ObjectNode.normalizeSchemaLiterals() {
        listOf("example", "default", "const").forEach { name -> normalizeIntegralField(name) }
        listOf("enum", "examples").forEach { name -> normalizeIntegralArrayField(name) }
    }

    private fun ObjectNode.normalizeDirectExamples() {
        normalizeIntegralField("example")
        val examples = objectValue("examples") ?: return
        val entries = examples.properties().asSequence().toList()
        entries.forEach { (name, example) ->
            if (example.isIntegralNumber) {
                examples.put(name, example.numberValue().toString())
            } else {
                (example as? ObjectNode)?.normalizeIntegralField("value")
            }
        }
    }

    private fun ObjectNode.normalizeIntegralField(name: String) {
        val value = get(name)?.takeIf(JsonNode::isIntegralNumber) ?: return
        put(name, value.numberValue().toString())
    }

    private fun ObjectNode.normalizeIntegralArrayField(name: String) {
        val valuesNode = get(name)?.takeIf(JsonNode::isArray) ?: return
        val values = valuesNode.values().asSequence().toList()
        if (values.none(JsonNode::isIntegralNumber)) {
            return
        }
        val normalized = putArray(name)
        values.forEach { value ->
            if (value.isIntegralNumber) {
                normalized.add(value.numberValue().toString())
            } else {
                normalized.add(value)
            }
        }
    }

    private fun ObjectNode.objectValue(name: String): ObjectNode? = get(name) as? ObjectNode

    private fun ObjectNode.forEachObjectValue(name: String, action: (ObjectNode) -> Unit) {
        objectValue(name)?.properties()?.forEach { (_, value) ->
            (value as? ObjectNode)?.let(action)
        }
    }

    private fun ObjectNode.forEachArrayObject(name: String, action: (ObjectNode) -> Unit) {
        val values = get(name)?.takeIf(JsonNode::isArray) ?: return
        values.values().forEach { value ->
            (value as? ObjectNode)?.let(action)
        }
    }

    private val OPENAPI_OPERATION_KEYS = listOf("get", "put", "post", "delete", "options", "head", "patch", "trace")
    private val SCHEMA_MAP_KEYS = listOf("properties", "patternProperties", "dependentSchemas", "\$defs", "definitions")
    private val SCHEMA_SINGLE_KEYS = listOf(
        "items",
        "contains",
        "additionalProperties",
        "unevaluatedProperties",
        "propertyNames",
        "not",
        "if",
        "then",
        "else",
        "contentSchema",
    )
    private val SCHEMA_ARRAY_KEYS = listOf("allOf", "anyOf", "oneOf", "prefixItems")
}
