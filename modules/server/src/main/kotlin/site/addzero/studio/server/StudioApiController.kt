package site.addzero.studio.server

import io.ktor.server.routing.Route

/** 在统一的 `/studio/api` 边界内安装宿主元数据 Controller。 */
fun interface StudioApiController {
    fun install(route: Route)
}
