package site.addzero.studio.server

import io.ktor.server.routing.Route

/** 在指定 Studio API 边界内安装 Controller。 */
fun interface StudioApiController {
    fun install(route: Route)
}
