package site.addzero.studio.clientcontract

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.AdditionalProperties
import io.ktor.openapi.Components
import io.ktor.openapi.GenericElement
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.MediaType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Operation
import io.ktor.openapi.PathItem
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.RequestBody
import io.ktor.openapi.jsonSchema
import io.ktor.server.routing.openapi.CollectSchemaReferences
import io.ktor.server.routing.openapi.OperationMapping
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import site.addzero.studio.contract.AgentConversationCommand
import site.addzero.studio.contract.AgentConversationModelCommand
import site.addzero.studio.contract.AgentConversationView
import site.addzero.studio.contract.AgentMessageView
import site.addzero.studio.contract.AgentProviderModel
import site.addzero.studio.contract.AgentProviderSettingsCommand
import site.addzero.studio.contract.AgentProviderSettingsView
import site.addzero.studio.contract.CommonResult
import site.addzero.studio.contract.ExampleHelloResponse
import site.addzero.studio.contract.LsiCatalogEntry
import site.addzero.studio.contract.StudioClientConfig
import site.addzero.studio.contract.report.PublishedReportListPage
import site.addzero.studio.contract.report.PublishedReportView
import site.addzero.studio.contract.report.ReportCreateCommand
import site.addzero.studio.contract.report.ReportListPage
import site.addzero.studio.contract.report.ReportPublicationView
import site.addzero.studio.contract.report.ReportPublishCommand
import site.addzero.studio.contract.report.ReportUpdateCommand
import site.addzero.studio.contract.report.ReportView
import site.addzero.studio.contract.report.SpreadsheetTemplateFillCommand
import site.addzero.studio.contract.report.SpreadsheetTemplateListPage
import site.addzero.studio.contract.report.SpreadsheetTemplateUpdateCommand
import site.addzero.studio.contract.report.SpreadsheetTemplateView

/** Console 的客户端契约，路径和传输类型不再在前端重复声明。 */
object ConsoleClientContract {
    fun generate(): String {
        val schemas = linkedMapOf<String, JsonSchema>()
        val collectSchemas = CollectSchemaReferences { schema ->
            val title = schema.title ?: return@CollectSchemaReferences null
            val name = title.componentName()
            schemas[name] = if (name == "JsonElement") {
                JSON_ELEMENT_SCHEMA
            } else {
                schema.normalizeDiscriminator(schemas).copy(title = name)
            }
            name
        }
        val document = OpenApiDoc(
            info = OpenApiInfo(
                title = "Console Client API",
                version = "1.0.0",
                description = "Generated from shared transport contracts.",
            ),
            paths = clientPaths().mapValues { (_, reference) ->
                val path = requireNotNull(reference.valueOrNull())
                ReferenceOr.Value(path.mapOperations(collectSchemas))
            },
            components = Components(schemas = schemas),
        )
        val encoded = Json.parseToJsonElement(JSON.encodeToString(document))
        return JSON.encodeToString(encoded.normalizeComponentReferences()) + "\n"
    }
}

private fun clientPaths(): Map<String, ReferenceOr<PathItem>> = linkedMapOf(
    "/hello" to pathItem(
        get = operation("getHello", "Example") {
            jsonResponse<ExampleHelloResponse>()
        },
    ),
    "/studio/config" to pathItem(
        get = operation("getStudioConfig", "Console") {
            jsonResponse<StudioClientConfig>()
        },
    ),
    "/console/api/config" to pathItem(
        get = operation("getConsoleConfig", "Console") {
            jsonResponse<CommonResult<StudioClientConfig>>()
        },
    ),
    "/console/api/catalog" to pathItem(
        get = operation("getConsoleCatalog", "Console") {
            jsonResponse<CommonResult<List<LsiCatalogEntry>>>()
        },
    ),
    "/agent/settings" to pathItem(
        get = applicationOperation("getAgentSettings", "Agent") {
            jsonResponse<CommonResult<AgentProviderSettingsView>>()
        },
        put = applicationOperation("updateAgentSettings", "Agent") {
            jsonBody<AgentProviderSettingsCommand>()
            jsonResponse<CommonResult<AgentProviderSettingsView>>()
        },
    ),
    "/agent/models" to pathItem(
        get = applicationOperation("listAgentModels", "Agent") {
            jsonResponse<CommonResult<List<AgentProviderModel>>>()
        },
    ),
    "/agent/conversations" to pathItem(
        get = applicationOperation("listAgentConversations", "Agent") {
            jsonResponse<CommonResult<List<AgentConversationView>>>()
        },
        post = applicationOperation("createAgentConversation", "Agent") {
            jsonBody<AgentConversationCommand>()
            jsonResponse<CommonResult<Long>>()
        },
        delete = applicationOperation("deleteAgentConversations", "Agent") {
            jsonBody<List<Long>>()
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
    "/agent/conversations/model" to pathItem(
        put = applicationOperation("updateAgentConversationModel", "Agent") {
            jsonBody<AgentConversationModelCommand>()
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
    "/agent/messages" to pathItem(
        get = applicationOperation("listAgentMessages", "Agent") {
            longQueryParameter("id", required = true)
            jsonResponse<CommonResult<List<AgentMessageView>>>()
        },
    ),
    "/console/api/reports" to pathItem(
        get = operation("listReports", "Reports") {
            pageParameters()
            jsonResponse<CommonResult<ReportListPage>>()
        },
        post = operation("createReport", "Reports") {
            jsonBody<ReportCreateCommand>()
            jsonResponse<CommonResult<ReportView>>()
        },
    ),
    "/console/api/reports/{reportKey}" to pathItem(
        get = operation("getReport", "Reports") {
            pathParameter("reportKey")
            jsonResponse<CommonResult<ReportView>>()
        },
        put = operation("updateReport", "Reports") {
            pathParameter("reportKey")
            jsonBody<ReportUpdateCommand>()
            jsonResponse<CommonResult<ReportView>>()
        },
        delete = operation("deleteReport", "Reports") {
            pathParameter("reportKey")
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
    "/console/api/reports/{reportKey}/publication" to pathItem(
        post = operation("publishReport", "Reports") {
            pathParameter("reportKey")
            jsonBody<ReportPublishCommand>()
            jsonResponse<CommonResult<ReportPublicationView>>()
        },
        delete = operation("unpublishReport", "Reports") {
            pathParameter("reportKey")
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
    "/console/api/published-reports" to pathItem(
        get = operation("listPublishedReports", "Reports") {
            pageParameters()
            jsonResponse<CommonResult<PublishedReportListPage>>()
        },
    ),
    "/console/api/published-reports/{reportKey}" to pathItem(
        get = operation("getPublishedReport", "Reports") {
            pathParameter("reportKey")
            jsonResponse<CommonResult<PublishedReportView>>()
        },
    ),
    "/console/api/spreadsheet-templates" to pathItem(
        get = operation("listSpreadsheetTemplates", "Spreadsheet Templates") {
            pageParameters()
            jsonResponse<CommonResult<SpreadsheetTemplateListPage>>()
        },
        post = operation("importSpreadsheetTemplate", "Spreadsheet Templates") {
            spreadsheetImportBody()
            jsonResponse<CommonResult<SpreadsheetTemplateView>>()
        },
    ),
    "/console/api/spreadsheet-templates/{templateKey}" to pathItem(
        get = operation("getSpreadsheetTemplate", "Spreadsheet Templates") {
            pathParameter("templateKey")
            jsonResponse<CommonResult<SpreadsheetTemplateView>>()
        },
        put = operation("updateSpreadsheetTemplate", "Spreadsheet Templates") {
            pathParameter("templateKey")
            jsonBody<SpreadsheetTemplateUpdateCommand>()
            jsonResponse<CommonResult<SpreadsheetTemplateView>>()
        },
        delete = operation("deleteSpreadsheetTemplate", "Spreadsheet Templates") {
            pathParameter("templateKey")
            jsonResponse<CommonResult<Boolean>>()
        },
    ),
    "/console/api/spreadsheet-templates/{templateKey}/fill" to pathItem(
        post = operation("fillSpreadsheetTemplate", "Spreadsheet Templates") {
            pathParameter("templateKey")
            jsonBody<SpreadsheetTemplateFillCommand>()
            binaryResponse()
        },
    ),
) + metadataClientPaths()

internal fun pathItem(
    get: Operation? = null,
    post: Operation? = null,
    put: Operation? = null,
    delete: Operation? = null,
): ReferenceOr<PathItem> = ReferenceOr.Value(
    PathItem(get = get, post = post, put = put, delete = delete),
)

private fun PathItem.mapOperations(mapping: OperationMapping): PathItem = copy(
    get = get?.let(mapping::map),
    post = post?.let(mapping::map),
    put = put?.let(mapping::map),
    delete = delete?.let(mapping::map),
    options = options?.let(mapping::map),
    head = head?.let(mapping::map),
    patch = patch?.let(mapping::map),
    trace = trace?.let(mapping::map),
)

private fun JsonSchema.normalizeDiscriminator(schemas: MutableMap<String, JsonSchema>): JsonSchema {
    val current = discriminator ?: return this
    val mapping = current.mapping?.mapValues { (_, reference) ->
        "#/components/schemas/${reference.substringAfterLast('/').componentName()}"
    }
    mapping?.forEach { (value, reference) ->
        val component = reference.substringAfterLast('/')
        val subtype = schemas[component] ?: return@forEach
        schemas[component] = subtype.copy(
            properties = subtype.properties.orEmpty() + (
                current.propertyName to ReferenceOr.Value(
                    JsonSchema(type = JsonType.STRING, enum = listOf(GenericElement(value))),
                )
                ),
            required = (subtype.required.orEmpty() + current.propertyName).distinct(),
        )
    }
    return copy(discriminator = current.copy(mapping = mapping))
}

private fun String.componentName(): String = substringAfterLast('.').replace(NON_COMPONENT_CHARACTER, "")

private fun JsonElement.normalizeComponentReferences(): JsonElement = when (this) {
    is JsonArray -> JsonArray(map(JsonElement::normalizeComponentReferences))
    is JsonObject -> JsonObject(mapValues { (key, value) ->
        if (key == "\$ref" && value is JsonPrimitive && value.isString) {
            JsonPrimitive(value.content.normalizeComponentReference())
        } else {
            value.normalizeComponentReferences()
        }
    })
    else -> this
}

private fun String.normalizeComponentReference(): String {
    if (!startsWith(COMPONENT_REFERENCE_PREFIX)) {
        return this
    }
    val component = removePrefix(COMPONENT_REFERENCE_PREFIX).componentName()
    return "$COMPONENT_REFERENCE_PREFIX$component"
}

internal fun operation(
    operationId: String,
    tag: String,
    configure: Operation.Builder.() -> Unit,
): Operation = Operation.build {
    this.operationId = operationId
    tag(tag)
    configure()
}

private fun applicationOperation(
    operationId: String,
    tag: String,
    configure: Operation.Builder.() -> Unit,
): Operation = operation(operationId, tag) {
    extension("x-client-base", "application")
    configure()
}

internal inline fun <reified T : Any> Operation.Builder.jsonBody() {
    requestBody {
        required = true
        schema = jsonSchema<T>()
    }
}

internal inline fun <reified T : Any> Operation.Builder.jsonResponse() {
    responses {
        HttpStatusCode.OK {
            description = "Success"
            schema = jsonSchema<T>()
        }
    }
}

internal fun Operation.Builder.binaryResponse() {
    responses {
        HttpStatusCode.OK {
            description = "Generated spreadsheet"
            ContentType.Application.OctetStream {
                schema = BINARY_SCHEMA
            }
        }
    }
}

internal fun Operation.Builder.pageParameters() {
    parameters {
        query("pageNo") {
            schema = INTEGER_SCHEMA
        }
        query("pageSize") {
            schema = INTEGER_SCHEMA
        }
    }
}

internal fun Operation.Builder.longQueryParameter(name: String, required: Boolean = false) {
    parameters {
        query(name) {
            schema = LONG_SCHEMA
            this.required = required
        }
    }
}

private fun Operation.Builder.pathParameter(name: String) {
    parameters {
        path(name) {
            schema = STRING_SCHEMA
        }
    }
}

private fun Operation.Builder.spreadsheetImportBody() {
    requestBody = RequestBody(
        required = true,
        content = mapOf(
            ContentType.MultiPart.FormData to MediaType(
                schema = ReferenceOr.Value(
                    JsonSchema(
                        type = JsonType.OBJECT,
                        properties = mapOf(
                            "templateKey" to ReferenceOr.Value(STRING_SCHEMA),
                            "name" to ReferenceOr.Value(STRING_SCHEMA),
                            "file" to ReferenceOr.Value(BINARY_SCHEMA),
                        ),
                        required = listOf("templateKey", "name", "file"),
                        additionalProperties = AdditionalProperties.Allowed(false),
                    ),
                ),
            ),
        ),
    )
}

private val JSON = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = false
    explicitNulls = false
}
private val STRING_SCHEMA = JsonSchema(type = JsonType.STRING)
private val INTEGER_SCHEMA = JsonSchema(type = JsonType.INTEGER, format = "int32")
private val LONG_SCHEMA = JsonSchema(type = JsonType.INTEGER, format = "int64")
private val BINARY_SCHEMA = JsonSchema(type = JsonType.STRING, format = "binary")
private val JSON_ELEMENT_SCHEMA = JsonSchema(
    title = "JsonElement",
    anyOf = listOf(
        ReferenceOr.Value(JsonSchema(type = JsonType.STRING)),
        ReferenceOr.Value(JsonSchema(type = JsonType.NUMBER)),
        ReferenceOr.Value(JsonSchema(type = JsonType.BOOLEAN)),
        ReferenceOr.Value(JsonSchema(type = JsonType.NULL)),
        ReferenceOr.Value(JsonSchema(type = JsonType.ARRAY)),
        ReferenceOr.Value(
            JsonSchema(
                type = JsonType.OBJECT,
                additionalProperties = AdditionalProperties.Allowed(true),
            ),
        ),
    ),
)
private val NON_COMPONENT_CHARACTER = Regex("[^A-Za-z0-9_]")
private const val COMPONENT_REFERENCE_PREFIX = "#/components/schemas/"
