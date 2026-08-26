package site.addzero.studio.metadata

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

internal fun JsonNode.requiredObject(name: String): ObjectNode {
    val value = get(name)
    if (value !is ObjectNode) {
        badRequest("$name 必须是 JSON 对象")
    }
    return value
}

internal fun JsonNode.objectOrEmpty(name: String, mapper: ObjectMapper): ObjectNode {
    val value = get(name)
    if (value == null || value.isNull) {
        return mapper.createObjectNode()
    }
    if (value !is ObjectNode) {
        badRequest("$name 必须是 JSON 对象")
    }
    return value
}

internal fun JsonNode.arrayOrEmpty(name: String, mapper: ObjectMapper): ArrayNode {
    val value = get(name)
    if (value == null || value.isNull) {
        return mapper.createArrayNode()
    }
    if (value !is ArrayNode) {
        badRequest("$name 必须是 JSON 数组")
    }
    return value
}

internal fun JsonNode.requiredText(name: String): String {
    val value = get(name)?.takeUnless(JsonNode::isNull)?.asString()?.trim().orEmpty()
    if (value.isEmpty()) {
        badRequest("$name 不能为空")
    }
    return value
}

internal fun JsonNode.optionalText(name: String): String? = get(name)
    ?.takeUnless(JsonNode::isNull)
    ?.asString()
    ?.trim()
    ?.takeIf(String::isNotEmpty)

internal fun JsonNode.requiredLong(name: String): Long {
    val value = get(name)?.takeUnless(JsonNode::isNull)
    return value?.asString()?.toLongOrNull() ?: badRequest("$name 必须是整数")
}

internal fun JsonNode.optionalLong(name: String): Long? = get(name)
    ?.takeUnless(JsonNode::isNull)
    ?.asString()
    ?.toLongOrNull()
    ?: if (has(name) && !get(name).isNull) {
        badRequest("$name 必须是整数")
    } else {
        null
    }

internal fun JsonNode.intOrDefault(name: String, default: Int): Int {
    val value = get(name)?.takeUnless(JsonNode::isNull) ?: return default
    return value.asString().toIntOrNull() ?: badRequest("$name 必须是整数")
}

internal fun JsonNode.booleanOrDefault(name: String, default: Boolean): Boolean {
    val value = get(name)?.takeUnless(JsonNode::isNull) ?: return default
    if (!value.isBoolean) {
        badRequest("$name 必须是布尔值")
    }
    return value.asBoolean()
}

internal fun JsonNode.requiredIds(): List<Long> {
    if (!isArray) {
        badRequest("请求体必须是 ID 数组")
    }
    val ids = buildList {
        this@requiredIds.forEach { value ->
            add(value.asString().toLongOrNull() ?: badRequest("ID 必须是整数"))
        }
    }.distinct()
    if (ids.isEmpty()) {
        badRequest("ID 数组不能为空")
    }
    return ids
}

internal fun ObjectMapper.readObject(value: String?): ObjectNode {
    if (value.isNullOrBlank()) {
        return createObjectNode()
    }
    val node = readTree(value)
    if (node !is ObjectNode) {
        error("数据库 JSONB 值不是对象")
    }
    return node
}

internal fun ObjectMapper.readArray(value: String?): ArrayNode {
    if (value.isNullOrBlank()) {
        return createArrayNode()
    }
    val node = readTree(value)
    if (node !is ArrayNode) {
        error("数据库 JSONB 值不是数组")
    }
    return node
}

internal fun ObjectNode.putNullable(name: String, value: String?) {
    if (value == null) {
        putNull(name)
    } else {
        put(name, value)
    }
}

internal fun ObjectNode.putNullable(name: String, value: Long?) {
    if (value == null) {
        putNull(name)
    } else {
        put(name, value)
    }
}

internal fun ObjectNode.putNode(name: String, value: JsonNode) {
    set(name, value)
}
