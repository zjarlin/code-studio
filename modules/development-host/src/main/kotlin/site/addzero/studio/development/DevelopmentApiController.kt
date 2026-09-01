package site.addzero.studio.development

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.response.respondResource
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import site.addzero.platform.web.Controller
import site.addzero.studio.contract.ExampleHelloResponse

/** 安装开发宿主自带的可调用示例接口及其 OpenAPI 文档。 */
internal object DevelopmentApiController : Controller {
    override val routeKey = "/hello"

    override fun install(route: Route) {
        route.get("/hello") {
            val response = ExampleHelloResponse(
                message = "Hello, world!",
                category = "Example",
                value = 1,
                imagePath = "/studio/favicon.svg",
            )
            call.respond(response)
        }
        route.get("/v3/api-docs") {
            val resource = "site/addzero/studio/development/openapi.json"
            call.respondResource(resource)
        }
    }
}
