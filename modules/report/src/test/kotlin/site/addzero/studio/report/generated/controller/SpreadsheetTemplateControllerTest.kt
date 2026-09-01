package site.addzero.studio.report.generated.controller

import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson3.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import site.addzero.studio.report.withPostgresReportFixture
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpreadsheetTemplateControllerTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `上传 CAS 更新填充下载与删除`() {
        withPostgresReportFixture { fixture ->
            testApplication {
                application {
                    install(ContentNegotiation) { jackson() }
                    routing {
                        route("/console/api") {
                            SpreadsheetTemplateController(fixture.dataSource, fixture.schema).install(this)
                        }
                    }
                }

                val uploaded = client.post("/console/api/spreadsheet-templates") {
                    setBody(MultiPartFormDataContent(formData {
                        append("templateKey", "inspection-record")
                        append("name", "检验记录")
                        append("file", sampleWorkbook(), Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                            append(
                                HttpHeaders.ContentDisposition,
                                ContentDisposition.File.withParameter(ContentDisposition.Parameters.FileName, "sample.xlsx").toString(),
                            )
                        })
                    }))
                }
                assertEquals(HttpStatusCode.OK, uploaded.status)
                val created = mapper.readTree(uploaded.bodyAsText())["data"]
                assertEquals(1, created["revision"].asLong())
                assertEquals("检验记录", created["document"]["name"].asString())

                val page = mapper.readTree(client.get("/console/api/spreadsheet-templates?pageNo=1&pageSize=20").bodyAsText())
                assertEquals(1, page["data"]["totalRowCount"].asLong())

                val document = created["document"]
                val updateBody = """{
                    "expectedRevision":1,
                    "draft":{
                        "name":${document["name"]},
                        "description":${document["description"]},
                        "variables":${document["variables"]},
                        "bindings":${document["bindings"]},
                        "ledgers":${document["ledgers"]},
                        "edits":${document["edits"]}
                    }
                }""".trimIndent()
                val updated = client.put("/console/api/spreadsheet-templates/inspection-record") {
                    contentType(ContentType.Application.Json)
                    setBody(updateBody)
                }
                assertEquals(HttpStatusCode.OK, updated.status)
                val stale = client.put("/console/api/spreadsheet-templates/inspection-record") {
                    contentType(ContentType.Application.Json)
                    setBody(updateBody)
                }
                assertEquals(HttpStatusCode.Conflict, stale.status)

                val immutableStructure = client.put("/console/api/spreadsheet-templates/inspection-record") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{
                            "expectedRevision":2,
                            "draft":{
                                "name":"检验记录",
                                "source":${document["source"]}
                            }
                        }""".trimIndent(),
                    )
                }
                assertEquals(HttpStatusCode.BadRequest, immutableStructure.status)

                val filled = client.post("/console/api/spreadsheet-templates/inspection-record/fill") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"expectedRevision":2}""")
                }
                assertEquals(HttpStatusCode.OK, filled.status)
                assertTrue(filled.headers[HttpHeaders.ContentDisposition].orEmpty().contains("sample.xlsx"))
                assertTrue(filled.bodyAsBytes().size > 100)

                val staleFill = client.post("/console/api/spreadsheet-templates/inspection-record/fill") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"expectedRevision":1}""")
                }
                assertEquals(HttpStatusCode.Conflict, staleFill.status)

                val deleted = client.delete("/console/api/spreadsheet-templates/inspection-record")
                assertEquals(HttpStatusCode.OK, deleted.status)
                assertEquals(HttpStatusCode.NotFound, client.get("/console/api/spreadsheet-templates/inspection-record").status)
            }
        }
    }

    private fun sampleWorkbook(): ByteArray = XSSFWorkbook().use { workbook ->
        workbook.createSheet("Template").createRow(0).createCell(0).setCellValue("Example")
        ByteArrayOutputStream().use { output ->
            workbook.write(output)
            output.toByteArray()
        }
    }
}
