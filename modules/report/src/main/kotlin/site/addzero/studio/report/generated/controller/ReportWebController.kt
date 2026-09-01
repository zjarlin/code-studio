package site.addzero.studio.report.generated.controller

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondResource
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import site.addzero.studio.server.StudioApiController

/** 提供报表 JAR 自带的前端资源和 SPA 深链接。 */
class ReportWebController : StudioApiController {
    override fun install(route: Route) {
        route.route(REPORT_PATH) {
            get {
                val resource = REPORT_INDEX_RESOURCE
                call.respondResource(resource)
            }
            get("/") {
                val resource = REPORT_INDEX_RESOURCE
                call.respondResource(resource)
            }
            get("{path...}") {
                val relativePath = call.request.path().removePrefix(REPORT_PATH).trimStart('/')
                val hasUnsafeSegment = relativePath.split('/').any { segment -> segment == "." || segment == ".." }
                if (hasUnsafeSegment) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val isStaticResource = relativePath.substringAfterLast('/').contains('.')
                val resource = if (isStaticResource) "$REPORT_RESOURCE_ROOT/$relativePath" else REPORT_INDEX_RESOURCE
                val exists = call.application.environment.classLoader.getResource(resource) != null
                if (!exists) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                call.respondResource(resource)
            }
        }
    }
}

private const val REPORT_PATH = "/report"
private const val REPORT_RESOURCE_ROOT = "report"
private const val REPORT_INDEX_RESOURCE = "$REPORT_RESOURCE_ROOT/index.html"
