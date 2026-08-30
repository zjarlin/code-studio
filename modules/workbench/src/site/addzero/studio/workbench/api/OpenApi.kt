package site.addzero.studio.workbench.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class ApiOperation(
    val id: String,
    val method: String,
    val path: String,
    val summary: String,
    val description: String?,
    val tags: List<String>,
    val parameters: List<ApiParameter>,
    val requestBody: JsonObject?,
    val responses: JsonObject,
)

internal data class ApiParameter(
    val name: String,
    val location: String,
    val required: Boolean,
    val description: String?,
    val schema: JsonObject,
)

internal data class ApiGroup(
    val name: String,
    val operations: List<ApiOperation>,
)

internal fun collectApiGroups(document: JsonObject): List<ApiGroup> {
    val operations = document["paths"]?.jsonObject.orEmpty().flatMap { (path, pathValue) ->
        val pathItem = pathValue as? JsonObject ?: return@flatMap emptyList()
        pathItem.mapNotNull { (method, operationValue) ->
            if (method.lowercase() !in HTTP_METHODS) return@mapNotNull null
            val operation = operationValue as? JsonObject ?: return@mapNotNull null
            val tags = operation.stringList("tags").ifEmpty { listOf("未分组") }
            ApiOperation(
                id = operation.string("operationId") ?: "$method:$path",
                method = method.lowercase(),
                path = path,
                summary = operation.string("summary") ?: "${method.uppercase()} $path",
                description = operation.string("description"),
                tags = tags,
                parameters = (operation["parameters"] as? JsonArray).orEmpty().mapNotNull(::toParameter),
                requestBody = operation["requestBody"] as? JsonObject,
                responses = operation["responses"] as? JsonObject ?: JsonObject(emptyMap()),
            )
        }
    }.sortedWith(compareBy(ApiOperation::path, ApiOperation::method))
    return operations
        .flatMap { operation -> operation.tags.map { tag -> tag to operation } }
        .groupBy(Pair<String, ApiOperation>::first, Pair<String, ApiOperation>::second)
        .entries
        .sortedBy(Map.Entry<String, List<ApiOperation>>::key)
        .map { (name, grouped) -> ApiGroup(name, grouped) }
}

internal fun requestContentType(operation: ApiOperation): String? =
    (operation.requestBody?.get("content") as? JsonObject)?.keys?.firstOrNull()

internal fun requestBodySample(operation: ApiOperation, document: JsonObject): String {
    val content = operation.requestBody?.get("content") as? JsonObject ?: return ""
    val media = content.values.firstOrNull() as? JsonObject ?: return ""
    val example = media["example"] ?: schemaSample(media["schema"] as? JsonObject, document)
    return example?.let(::prettyJson).orEmpty()
}

internal fun multipartFields(operation: ApiOperation): List<ApiParameter> {
    if (requestContentType(operation) != "multipart/form-data") return emptyList()
    val content = operation.requestBody?.get("content") as? JsonObject ?: return emptyList()
    val media = content["multipart/form-data"] as? JsonObject ?: return emptyList()
    val schema = media["schema"] as? JsonObject ?: return emptyList()
    val required = schema.stringList("required").toSet()
    val properties = schema["properties"] as? JsonObject ?: return emptyList()
    return properties.map { (name, value) ->
        ApiParameter(name, "multipart", name in required, (value as? JsonObject)?.string("description"), value.jsonObject)
    }
}

private fun toParameter(value: JsonElement): ApiParameter? {
    val parameter = value as? JsonObject ?: return null
    return ApiParameter(
        name = parameter.string("name") ?: return null,
        location = parameter.string("in") ?: return null,
        required = parameter["required"]?.jsonPrimitive?.booleanOrNull == true,
        description = parameter.string("description"),
        schema = parameter["schema"] as? JsonObject ?: JsonObject(emptyMap()),
    )
}

private fun schemaSample(schema: JsonObject?, document: JsonObject, resolving: Set<String> = emptySet()): JsonElement? {
    schema ?: return null
    schema["example"]?.let { return it }
    schema["default"]?.let { return it }
    schema.string("\$ref")?.let { reference ->
        val name = reference.substringAfterLast('/')
        if (name in resolving) return JsonObject(emptyMap())
        val referenced = ((document["components"] as? JsonObject)?.get("schemas") as? JsonObject)?.get(name) as? JsonObject
        return schemaSample(referenced, document, resolving + name)
    }
    (schema["enum"] as? JsonArray)?.firstOrNull()?.let { return it }
    (schema["oneOf"] as? JsonArray)?.firstOrNull()?.let { return schemaSample(it as? JsonObject, document, resolving) }
    val type = schema.string("type")
    return when {
        type == "array" -> JsonArray(listOfNotNull(schemaSample(schema["items"] as? JsonObject, document, resolving)))
        type == "object" || schema["properties"] is JsonObject -> JsonObject(
            (schema["properties"] as? JsonObject).orEmpty().mapValues { (_, property) ->
                schemaSample(property as? JsonObject, document, resolving) ?: JsonPrimitive("")
            },
        )
        type == "integer" -> JsonPrimitive(1)
        type == "number" -> JsonPrimitive(0)
        type == "boolean" -> JsonPrimitive(false)
        else -> JsonPrimitive("")
    }
}

private fun JsonObject.string(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull

private fun JsonObject.stringList(name: String): List<String> =
    (get(name) as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }

private fun prettyJson(value: JsonElement): String = value.toString()

private val HTTP_METHODS = setOf("get", "post", "put", "patch", "delete", "head", "options")
