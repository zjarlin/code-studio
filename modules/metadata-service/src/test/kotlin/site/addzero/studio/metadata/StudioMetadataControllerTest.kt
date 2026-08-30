package site.addzero.studio.metadata

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson3.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path

class StudioMetadataControllerTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `构造时拒绝不安全 schema`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            StudioMetadataController(
                UnconnectedDataSource(),
                "studio;drop schema public",
                EDITABLE_ID,
                testTargetProfile(),
            )
        }

        assertTrue(error.message.orEmpty().contains("schema"))
    }

    @Test
    fun `六类元数据通过真实 HTTP 和 PostgreSQL 完成自治 CRUD`(@TempDir resources: Path) {
        withPostgresFixture(resources) { fixture ->
            testApplication {
                application {
                    install(ContentNegotiation) {
                        jackson()
                    }
                    routing {
                        route("/studio/api") {
                            fixture.controller.install(this)
                        }
                    }
                }

                val libraries = client.get("/studio/api/lowcode/library/page?pageNo=1&pageSize=1000").result()
                assertEquals(2, libraries["data"]["list"].size())

                val libraryValidation = client.postJson(
                    "/studio/api/lowcode/library/validate",
                    libraryCommand(fixture.editableLibraryId),
                ).result()
                assertTrue(libraryValidation["data"]["valid"].asBoolean())

                val updatedLibrary = libraryCommand(fixture.editableLibraryId).deepCopy()
                updatedLibrary.put("displayName", "Editable Library Updated")
                assertEquals(0, client.putJson("/studio/api/lowcode/library/update", updatedLibrary).result()["code"].asInt())
                val libraryDetail = client.get(
                    "/studio/api/lowcode/library/detail?id=${fixture.editableLibraryId}",
                ).result()
                assertEquals("Editable Library Updated", libraryDetail["data"]["displayName"].asString())

                val featureCommand = mapper.readTree(
                    """{"libraryId":${fixture.editableLibraryId},"parentId":null,"featureCode":"catalog","name":"Catalog","description":"Catalog feature"}""",
                )
                val featureValidation = client.postJson(
                    "/studio/api/lowcode/library-feature/validate",
                    featureCommand,
                ).result()
                assertTrue(featureValidation["data"]["valid"].asBoolean())
                val featureId = client.postJson(
                    "/studio/api/lowcode/library-feature/create",
                    featureCommand,
                ).result()["data"]["id"].asLong()
                val featureDetail = client.get("/studio/api/lowcode/library-feature/detail?id=$featureId").result()
                assertEquals("catalog", featureDetail["data"]["featureCode"].asString())

                val modelCommand = modelCommand(featureId)
                assertValid(client, "/studio/api/lowcode/model/validate", modelCommand)
                val modelId = client.postJson("/studio/api/lowcode/model/add", modelCommand).result()["data"].asLong()
                val modelDetail = client.get("/studio/api/lowcode/model/detail?id=$modelId").result()
                assertEquals(EDITABLE_ID, modelDetail["data"]["contributorId"].asString())
                assertEquals(1, modelDetail["data"]["fields"].size())
                val modelPage = client.postJson(
                    "/studio/api/lowcode/model/page",
                    """{"pageNumber":1,"pageSize":1,"condition":{"keyword":"Catalog"}}""",
                ).result()["data"]
                assertEquals(1, modelPage["rows"].size())
                assertEquals(1, modelPage["totalRowCount"].asInt())
                assertEquals(1, modelPage["totalPageCount"].asInt())
                assertEquals(1, modelPage["rows"][0]["fields"].size())
                assertEquals(1, modelPage["rows"][0]["queries"][0]["items"].size())

                val dtoCommand = dtoCommand(featureId)
                assertValid(client, "/studio/api/lowcode/dto/validate", dtoCommand)
                val dtoId = client.postJson("/studio/api/lowcode/dto/add", dtoCommand).result()["data"].asLong()
                assertEquals("CatalogView", client.get("/studio/api/lowcode/dto/detail?id=$dtoId").result()["data"]["className"].asString())
                assertEquals(1, client.postJson("/studio/api/lowcode/dto/list", "{}").result()["data"].size())

                val serviceConvention = conventionFileCommand(featureId, "catalogApplication", "CatalogApplicationService", "SERVICE")
                assertValid(client, "/studio/api/lowcode/convention-file/validate", serviceConvention)
                val serviceConventionId = client.postJson(
                    "/studio/api/lowcode/convention-file/add",
                    serviceConvention,
                ).result()["data"].asLong()
                val jobConvention = conventionFileCommand(featureId, "refreshCatalog", "RefreshCatalogJob", "SCHEDULED_JOB")
                assertValid(client, "/studio/api/lowcode/convention-file/validate", jobConvention)
                val jobConventionId = client.postJson(
                    "/studio/api/lowcode/convention-file/add",
                    jobConvention,
                ).result()["data"].asLong()
                val conventionFiles = client.postJson(
                    "/studio/api/lowcode/convention-file/list",
                    "{}",
                ).result()["data"]
                assertEquals(2, conventionFiles.size())
                val jobDetail = client.get(
                    "/studio/api/lowcode/convention-file/detail?id=$jobConventionId",
                ).result()["data"]
                assertEquals("example.editable.catalog.job", jobDetail["packageName"].asString())

                val constantCommand = constantCommand(featureId)
                assertValid(client, "/studio/api/lowcode/constant/validate", constantCommand)
                val savedConstant = client.postJson(
                    "/studio/api/lowcode/constant/save",
                    constantCommand,
                ).result()["data"]
                val constantId = savedConstant["id"].asLong()
                assertEquals("ENABLED", savedConstant["constants"][0]["name"].asString())
                assertEquals(1, client.postJson(
                    "/studio/api/lowcode/constant/list",
                    """{"featureId":$featureId}""",
                ).result()["data"].size())
                assertEquals("CatalogFlags", client.get(
                    "/studio/api/lowcode/constant/detail?id=$constantId",
                ).result()["data"]["objectName"].asString())

                val modelPreview = client.get("/studio/api/lowcode/model/preview?id=$modelId").result()["data"]
                assertTrue(modelPreview["files"].any { file -> file["filePath"].asString().endsWith("CatalogRecord.kt") })
                assertTrue(modelPreview["files"].any { file -> file["filePath"].asString().endsWith(".sql") })
                assertArchive(client.get("/studio/api/lowcode/model/download?id=$modelId"))

                val dtoPreview = client.get("/studio/api/lowcode/dto/preview?id=$dtoId").result()["data"]
                assertTrue(dtoPreview["files"].any { file -> file["filePath"].asString().endsWith("CatalogView.kt") })
                assertArchive(client.get("/studio/api/lowcode/dto/download?id=$dtoId"))

                val libraryPreviewResult = client.get(
                    "/studio/api/lowcode/library/preview?id=${fixture.editableLibraryId}&featureId=$featureId",
                ).result()
                assertEquals(0, libraryPreviewResult["code"].asInt(), libraryPreviewResult.toString())
                val libraryPreview = libraryPreviewResult["data"]
                assertTrue(libraryPreview["files"].size() > 0)
                assertTrue(libraryPreview["files"].none { file -> "code.studio.target" in file["content"].asString() })
                assertTrue(libraryPreview["files"].any { file ->
                    file["filePath"].asString().endsWith("/service/CatalogApplicationService.kt")
                })
                assertTrue(libraryPreview["files"].any { file ->
                    file["filePath"].asString().endsWith("/job/RefreshCatalogJob.kt")
                })

                val reuseCommand = dtoCommand(featureId).deepCopy() as tools.jackson.databind.node.ObjectNode
                reuseCommand.put("id", dtoId)
                val reuseAnalysisResult = client.postJson(
                    "/studio/api/lowcode/dto/reuse-analysis",
                    reuseCommand,
                ).result()
                assertEquals(0, reuseAnalysisResult["code"].asInt(), reuseAnalysisResult.toString())
                val reuseAnalysis = reuseAnalysisResult["data"]
                assertTrue(reuseAnalysis["draftQualifiedName"].asString().endsWith(".CatalogView"))
                assertTrue(reuseAnalysis["candidates"].size() > 0)

                assertSuccessfulDelete(client, "/studio/api/lowcode/constant", constantId)
                assertSuccessfulDelete(client, "/studio/api/lowcode/convention-file", serviceConventionId)
                assertSuccessfulDelete(client, "/studio/api/lowcode/convention-file", jobConventionId)
                assertSuccessfulDelete(client, "/studio/api/lowcode/dto", dtoId)
                assertSuccessfulDelete(client, "/studio/api/lowcode/model", modelId)
                val featureDelete = client.delete(
                    "/studio/api/lowcode/library-feature/delete?id=$featureId",
                ).result()
                assertEquals(0, featureDelete["code"].asInt())

                val libraryDelete = client.deleteJson(
                    "/studio/api/lowcode/library/delete",
                    "[${fixture.editableLibraryId}]",
                ).result()
                assertEquals(0, libraryDelete["code"].asInt())
                val remainingLibraries = client.get(
                    "/studio/api/lowcode/library/page?pageNo=1&pageSize=1000",
                ).result()
                assertEquals(1, remainingLibraries["data"]["list"].size())
            }
        }
    }

    @Test
    fun `跨 contributor 写入被拒绝且失败事务完整回滚`(@TempDir resources: Path) {
        withPostgresFixture(resources) { fixture ->
            testApplication {
                application {
                    install(ContentNegotiation) {
                        jackson()
                    }
                    routing {
                        route("/studio/api") {
                            fixture.controller.install(this)
                        }
                    }
                }

                val foreignFeature = mapper.readTree(
                    """{"libraryId":${fixture.dependencyLibraryId},"featureCode":"forbidden","name":"Forbidden"}""",
                )
                val featureResponse = client.postJson(
                    "/studio/api/lowcode/library-feature/create",
                    foreignFeature,
                )
                assertEquals(HttpStatusCode.Forbidden, featureResponse.status)
                assertEquals(403, featureResponse.result()["code"].asInt())

                val foreignModel = modelCommand(fixture.dependencyFeatureId)
                val modelResponse = client.postJson("/studio/api/lowcode/model/add", foreignModel)
                assertEquals(HttpStatusCode.Forbidden, modelResponse.status)

                val foreignDelete = client.deleteJson(
                    "/studio/api/lowcode/library/delete",
                    "[${fixture.dependencyLibraryId}]",
                )
                assertEquals(HttpStatusCode.Forbidden, foreignDelete.status)

                val modelId = client.postJson(
                    "/studio/api/lowcode/model/add",
                    modelCommand(fixture.editableFeatureId),
                ).result()["data"].asLong()
                val invalidUpdate = modelCommand(fixture.editableFeatureId).deepCopy()
                invalidUpdate.put("id", modelId)
                invalidUpdate.put("name", "Must Roll Back")
                (invalidUpdate["fields"][0] as tools.jackson.databind.node.ObjectNode).put("maxLength", -1)

                val failedUpdate = client.putJson("/studio/api/lowcode/model/update", invalidUpdate)
                assertEquals(HttpStatusCode.BadRequest, failedUpdate.status)
                val unchanged = client.get("/studio/api/lowcode/model/detail?id=$modelId").result()["data"]
                assertEquals("Catalog Record", unchanged["name"].asString())
                assertTrue(unchanged["fields"][0]["maxLength"].isNull)
            }
        }
    }

    private suspend fun assertValid(client: HttpClient, path: String, command: JsonNode) {
        val result = client.postJson(path, command).result()
        assertTrue(result["data"]["valid"].asBoolean(), result.toString())
    }

    private suspend fun assertSuccessfulDelete(client: HttpClient, path: String, id: Long) {
        val response = client.deleteJson(path, "[$id]").result()
        assertEquals(0, response["code"].asInt())
        assertTrue(response["data"].asBoolean())
    }

    private fun libraryCommand(id: Long): tools.jackson.databind.node.ObjectNode = mapper.readTree(
        """
        {
          "id": $id,
          "code": "$EDITABLE_ID",
          "displayName": "Editable Library",
          "version": 1,
          "status": 1,
          "spec": {
            "schemaVersion": 3,
            "description": null,
            "contributorId": "$EDITABLE_ID",
            "packagePrefix": "example.editable",
            "scanPackage": "example.editable",
            "kind": "BUSINESS",
            "runtimeDependencies": [],
            "supportedIdentityModes": ["LOCAL"],
            "applicationSelectable": true,
            "dataScope": {"tenantScoped": false, "userScoped": false, "departmentScoped": false}
          }
        }
        """.trimIndent(),
    ) as tools.jackson.databind.node.ObjectNode

    private fun modelCommand(featureId: Long): tools.jackson.databind.node.ObjectNode = mapper.readTree(
        """
        {
          "featureId": $featureId,
          "modelCode": "catalogRecord",
          "name": "Catalog Record",
          "className": "CatalogRecord",
          "tableName": "catalog_record",
          "modelType": "ENTITY",
          "status": 1,
          "version": 1,
          "entityConfig": {},
          "routeConfig": null,
          "remark": null,
          "fields": [{
            "orderNo": 1,
            "fieldCode": "title",
            "label": "Title",
            "kotlinType": "kotlin.String",
            "dbColumn": "title",
            "required": true,
            "listVisible": true,
            "formVisible": true,
            "formControl": "INPUT",
            "serialized": false,
            "key": false,
            "createWritable": true,
            "updateWritable": true
          }],
          "queries": [],
          "relations": []
        }
        """.trimIndent(),
    ) as tools.jackson.databind.node.ObjectNode

    private fun dtoCommand(featureId: Long): JsonNode = mapper.readTree(
        """
        {
          "featureId": $featureId,
          "dtoCode": "catalogView",
          "name": "Catalog View",
          "className": "CatalogView",
          "kind": "STRUCTURE",
          "visibility": "PUBLIC",
          "sourceModelCode": null,
          "selectionMode": "EXPLICIT",
          "excludedPaths": [],
          "fields": [{
            "name": "title",
            "sourcePath": "title",
            "description": "Title",
            "nullability": "NON_NULL",
            "schema": null,
            "kotlinType": {"qualifiedName": "kotlin.String", "arguments": [], "nullable": false},
            "validations": [],
            "annotations": []
          }, {
            "name": "status",
            "sourcePath": "status",
            "description": "Status",
            "nullability": "NON_NULL",
            "schema": null,
            "kotlinType": {"qualifiedName": "kotlin.String", "arguments": [], "nullable": false},
            "validations": [],
            "annotations": []
          }],
          "annotations": [],
          "superTypes": [],
          "status": 1,
          "version": 1,
          "description": "Catalog projection"
        }
        """.trimIndent(),
    )

    private fun constantCommand(featureId: Long): JsonNode = mapper.readTree(
        """
        {
          "featureId": $featureId,
          "groupCode": "catalogFlags",
          "objectName": "CatalogFlags",
          "description": "Catalog feature flags",
          "constants": [{
            "name": "ENABLED",
            "type": "BOOLEAN",
            "value": "true",
            "description": "Whether catalog is enabled"
          }]
        }
        """.trimIndent(),
    )

    private fun conventionFileCommand(
        featureId: Long,
        fileCode: String,
        className: String,
        kind: String,
    ): JsonNode = mapper.readTree(
        """
        {
          "featureId": $featureId,
          "fileCode": "$fileCode",
          "name": "$className",
          "className": "$className",
          "kind": "$kind",
          "status": 1,
          "description": null
        }
        """.trimIndent(),
    )

    private suspend fun HttpClient.postJson(path: String, body: Any): HttpResponse = post(path) {
        contentType(ContentType.Application.Json)
        setBody(jsonBody(body))
    }

    private suspend fun HttpClient.putJson(path: String, body: Any): HttpResponse = put(path) {
        contentType(ContentType.Application.Json)
        setBody(jsonBody(body))
    }

    private suspend fun HttpClient.deleteJson(path: String, body: Any): HttpResponse = delete(path) {
        contentType(ContentType.Application.Json)
        setBody(jsonBody(body))
    }

    private fun jsonBody(body: Any): String = if (body is String) body else mapper.writeValueAsString(body)

    private suspend fun assertArchive(response: HttpResponse) {
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("application/zip", response.headers[HttpHeaders.ContentType])
        assertTrue(response.headers[HttpHeaders.ContentDisposition].orEmpty().startsWith("attachment;"))
        assertTrue(response.bodyAsText().isNotEmpty())
    }

    private suspend fun HttpResponse.result(): JsonNode = mapper.readTree(bodyAsText())
}
