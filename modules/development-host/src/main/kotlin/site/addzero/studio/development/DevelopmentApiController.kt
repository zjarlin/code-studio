package site.addzero.studio.development

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondResource
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** 安装开发宿主自带的可调用示例接口及其 OpenAPI 文档。 */
internal object DevelopmentApiController {
    fun install(parent: Route) {
        parent.get("/hello") {
            val response = HelloResponse(message = "Hello, world!")
            call.respond(response)
        }
        parent.get("/v3/api-docs") {
            val resource = "site/addzero/studio/development/openapi.json"
            call.respondResource(resource)
        }
    }
}
