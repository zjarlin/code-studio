package site.addzero.studio.workbench.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal enum class TypeScriptClient { AXIOS, ALOVA }

internal fun generateTypeScriptRequest(
    operation: ApiOperation,
    client: TypeScriptClient,
): String {
    val functionName = operation.id.toIdentifier().replaceFirstChar(Char::lowercase)
        .ifBlank { "${operation.method}Request" }
    val typeName = functionName.replaceFirstChar(Char::uppercase)
    val pathParameters = operation.parameters.filter { it.location == "path" }
    val queryParameters = operation.parameters.filter { it.location == "query" }
    val headerParameters = operation.parameters.filter { it.location == "header" }
    val body = requestContentType(operation)
    val declarations = buildList {
        if (pathParameters.isNotEmpty()) add(renderParameters("${typeName}Path", pathParameters))
        if (queryParameters.isNotEmpty()) add(renderParameters("${typeName}Query", queryParameters))
        if (headerParameters.isNotEmpty()) add(renderParameters("${typeName}Headers", headerParameters))
        if (body != null) add("export type ${typeName}Body = unknown")
        add("export type ${typeName}Response = unknown")
    }
    val arguments = buildList {
        if (pathParameters.isNotEmpty()) add("path: ${typeName}Path")
        if (queryParameters.isNotEmpty()) add("params: ${typeName}Query")
        if (headerParameters.isNotEmpty()) add("headers: ${typeName}Headers")
        if (body != null) add("data: ${typeName}Body")
    }
    val url = operation.path.replace(Regex("\\{([^}]+)}")) { match -> "\${path.${match.groupValues[1]}}" }
    val call = when (client) {
        TypeScriptClient.AXIOS -> "request.request<${typeName}Response>({ url: `$url`, method: '${operation.method.uppercase()}'${if (queryParameters.isNotEmpty()) ", params" else ""}${if (headerParameters.isNotEmpty()) ", headers" else ""}${if (body != null) ", data" else ""} })"
        TypeScriptClient.ALOVA -> "alovaInstance.${operation.method.replaceFirstChar(Char::uppercase)}<${typeName}Response>(`$url`${if (body != null) ", data" else ""})"
    }
    val import = if (client == TypeScriptClient.AXIOS) {
        "import request from '@/config/axios'"
    } else {
        "import alovaInstance from '@/utils/alova'"
    }
    return buildString {
        appendLine(import)
        appendLine()
        appendLine(declarations.joinToString("\n\n"))
        appendLine()
        append("export function $functionName(${arguments.joinToString()}) {\n  return $call\n}\n")
    }
}

private fun renderParameters(name: String, parameters: List<ApiParameter>): String = buildString {
    appendLine("export interface $name {")
    parameters.forEach { parameter ->
        append("  ")
        append(parameter.name.quoteIfNeeded())
        if (!parameter.required) append('?')
        append(": ")
        append(parameter.schema.toTypeScriptType())
        appendLine()
    }
    append('}')
}

private fun JsonObject.toTypeScriptType(): String {
    val enums = this["enum"] as? JsonArray
    if (!enums.isNullOrEmpty()) {
        return enums.joinToString(" | ") { value -> "'${value.jsonPrimitive.contentOrNull.orEmpty()}'" }
    }
    return when (this["type"]?.jsonPrimitive?.contentOrNull) {
        "integer", "number" -> "number"
        "boolean" -> "boolean"
        "array" -> "unknown[]"
        "object" -> "Record<string, unknown>"
        else -> "string"
    }
}

private fun String.toIdentifier(): String = split(Regex("[^A-Za-z0-9]+"))
    .filter(String::isNotBlank)
    .joinToString("") { it.replaceFirstChar(Char::uppercase) }
    .let { value -> if (value.firstOrNull()?.isDigit() == true) "request$value" else value }

private fun String.quoteIfNeeded(): String = if (matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*"))) this else "'$this'"
