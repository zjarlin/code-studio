package site.addzero.studio.report.generated.controller

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson3.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import site.addzero.studio.contract.report.ReportCreateCommand
import site.addzero.studio.contract.report.ReportDocument
import site.addzero.studio.contract.report.ReportPublishCommand
import site.addzero.studio.contract.report.ReportRowSpec
import site.addzero.studio.contract.report.ReportTextBlock
import site.addzero.studio.contract.report.ReportUpdateCommand
import site.addzero.studio.metadata.withPostgresFixture
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path
import kotlin.test.assertEquals

class ReportControllerTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }
    private val mapper = jacksonObjectMapper()

    @Test
    fun `HTTP CRUD 发布幂等快照隔离与撤回`(@TempDir resources: Path) {
        withPostgresFixture(resources) { fixture ->
            testApplication {
                application {
                    install(ContentNegotiation) {
                        jackson()
                    }
                    routing {
                        route("/console/api") {
                            ReportController(fixture.dataSource, fixture.schema).install(this)
                        }
                    }
                }

                val badPage = client.get("/console/api/reports?pageNo=0&pageSize=20")
                assertEquals(HttpStatusCode.BadRequest, badPage.status)
                assertEquals(400, badPage.result()["code"].asInt())
                val missing = client.get("/console/api/reports/missing-report")
                assertEquals(HttpStatusCode.NotFound, missing.status)
                assertEquals(404, missing.result()["code"].asInt())
                val malformed = client.postJson("/console/api/reports", "{")
                assertEquals(HttpStatusCode.BadRequest, malformed.status)
                assertEquals(400, malformed.result()["code"].asInt())

                val create = ReportCreateCommand("sales-report", document("草稿一"))
                val created = client.postJson("/console/api/reports", json.encodeToString(create)).result()
                assertEquals(1, created["data"]["revision"].asLong())
                assertEquals("草稿一", created["data"]["document"]["name"].asString())

                val page = client.get("/console/api/reports?pageNo=1&pageSize=20").result()
                assertEquals(1, page["data"]["totalRowCount"].asLong())

                val update = ReportUpdateCommand(1, document("草稿二"))
                val updated = client.putJson("/console/api/reports/sales-report", json.encodeToString(update)).result()
                assertEquals(2, updated["data"]["revision"].asLong())

                val stale = client.putJson("/console/api/reports/sales-report", json.encodeToString(update))
                assertEquals(HttpStatusCode.Conflict, stale.status)
                assertEquals(409, stale.result()["code"].asInt())

                val publish = json.encodeToString(ReportPublishCommand(2))
                val published = client.postJson("/console/api/reports/sales-report/publication", publish).result()
                val repeated = client.postJson("/console/api/reports/sales-report/publication", publish).result()
                assertEquals(2, published["data"]["publishedRevision"].asLong())
                assertEquals(published["data"], repeated["data"])

                val editAfterPublish = ReportUpdateCommand(2, document("草稿三"))
                client.putJson("/console/api/reports/sales-report", json.encodeToString(editAfterPublish)).result()
                val currentPublication = client.get("/console/api/published-reports/sales-report").result()
                assertEquals("草稿二", currentPublication["data"]["document"]["name"].asString())

                val withdrawn = client.delete("/console/api/reports/sales-report/publication").result()
                assertEquals(true, withdrawn["data"].asBoolean())
                assertEquals(
                    HttpStatusCode.NotFound,
                    client.get("/console/api/published-reports/sales-report").status,
                )

                val deleted = client.delete("/console/api/reports/sales-report").result()
                assertEquals(true, deleted["data"].asBoolean())
                val repeatedDelete = client.delete("/console/api/reports/sales-report")
                assertEquals(HttpStatusCode.NotFound, repeatedDelete.status)
                assertEquals(404, repeatedDelete.result()["code"].asInt())
            }
        }
    }

    @Test
    fun `发布拒绝没有内容的草稿`(@TempDir resources: Path) {
        withPostgresFixture(resources) { fixture ->
            testApplication {
                application {
                    install(ContentNegotiation) {
                        jackson()
                    }
                    routing {
                        route("/console/api") {
                            ReportController(fixture.dataSource, fixture.schema).install(this)
                        }
                    }
                }

                val create = ReportCreateCommand("empty-report", ReportDocument(name = "空报表"))
                client.postJson("/console/api/reports", json.encodeToString(create)).result()
                val command = json.encodeToString(ReportPublishCommand(1))
                val response = client.postJson("/console/api/reports/empty-report/publication", command)

                assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
                assertEquals(422, response.result()["code"].asInt())
            }
        }
    }

    private fun document(name: String): ReportDocument = ReportDocument(
        name = name,
        rows = listOf(
            ReportRowSpec(
                key = "rowOne",
                blocks = listOf(ReportTextBlock("title", name)),
            ),
        ),
    )

    private suspend fun io.ktor.client.HttpClient.postJson(path: String, body: String): HttpResponse = post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun io.ktor.client.HttpClient.putJson(path: String, body: String): HttpResponse = put(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpResponse.result(): JsonNode = mapper.readTree(bodyAsText())
}
