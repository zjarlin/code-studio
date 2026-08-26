package site.addzero.studio.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondResource
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import site.addzero.studio.runtime.MetadataContributor
import site.addzero.studio.runtime.MetadataContributors
import site.addzero.studio.runtime.StudioAccessPolicy
import site.addzero.studio.runtime.StudioAccessRequest
import site.addzero.studio.runtime.StudioConfig
import javax.sql.DataSource

/** 在宿主应用中启动数据库迁移并安装 Studio Controller。 */
fun Application.installStudio(
    dataSource: DataSource,
    config: StudioConfig,
    accessPolicy: StudioAccessPolicy,
    classLoader: ClassLoader = environment.classLoader,
    metadataSchema: String = DEFAULT_STUDIO_SCHEMA,
    apiControllers: List<StudioApiController> = emptyList(),
) {
    if (!config.enabled) {
        return
    }
    val contributors = MetadataContributors.load(classLoader)
    require(contributors.any { contributor -> contributor.id == config.contributorId }) {
        "Studio 宿主未声明元数据贡献: ${config.contributorId}"
    }
    StudioMigrator(dataSource, classLoader, metadataSchema).migrate(contributors)
    routing {
        val controller = StudioController(config, accessPolicy, contributors, apiControllers)
        controller.install(this)
    }
}

/** 安装 Studio HTTP 传输边界。 */
class StudioController(
    private val config: StudioConfig,
    private val accessPolicy: StudioAccessPolicy,
    private val contributors: List<MetadataContributor>,
    private val apiControllers: List<StudioApiController> = emptyList(),
) {
    fun install(parent: Route) {
        if (!config.enabled) {
            return
        }
        parent.route("/studio") {
            install(StudioAccess) {
                policy = accessPolicy
            }
            get {
                val resource = "studio/index.html"
                call.respondResource(resource)
            }
            get("/") {
                val resource = "studio/index.html"
                call.respondResource(resource)
            }
            get("/config") {
                val response = config.toClientConfig()
                call.respond(response)
            }
            route("/api") {
                get("/contributors") {
                    val response = contributors.map { contributor ->
                        contributor.toSummary(config.editableContributorId)
                    }
                    call.respond(response)
                }
                apiControllers.forEach { controller ->
                    controller.install(this)
                }
            }
            staticResources("", "studio", index = "index.html")
        }
    }
}

private data class StudioErrorResponse(
    val error: String,
)

private class StudioAccessConfig {
    lateinit var policy: StudioAccessPolicy
}

private val StudioAccess = createRouteScopedPlugin(
    name = "StudioAccess",
    createConfiguration = ::StudioAccessConfig,
) {
    val policy = pluginConfig.policy
    onCall { call ->
        val request = call.toAccessRequest()
        val allowed = policy.isAllowed(request)
        if (!allowed) {
            val response = StudioErrorResponse("Forbidden")
            call.respond(HttpStatusCode.Forbidden, response)
        }
    }
}

private fun ApplicationCall.toAccessRequest(): StudioAccessRequest {
    val headers = request.headers.entries().associate { entry -> entry.key to entry.value }
    return StudioAccessRequest(
        method = request.httpMethod.value,
        path = request.path(),
        headers = headers,
    )
}
