package site.addzero.studio.contract

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val CATALOG_CONTRIBUTION_RESOURCE: String = "META-INF/code-studio/catalog.json"

@Serializable
enum class LsiCatalogEntryKind {
    SCENE,
    ROUTE,
    ELEMENT,
}

/** 约定文件编译后的路由与界面元素元数据。 */
@Serializable
data class LsiCatalogEntry(
    val routeKey: String,
    val elementKey: String? = null,
    val path: String? = null,
    val parentKey: String? = null,
    val kind: LsiCatalogEntryKind,
    val name: String,
    val description: String? = null,
    val icon: String? = null,
    val order: Int = 0,
    val permissions: List<String> = emptyList(),
    val enabled: Boolean = true,
) {
    init {
        require(name.isNotBlank()) {
            "目录条目名称不能为空"
        }
        require(description == null || description.isNotBlank()) {
            "目录条目说明不能是空白字符串"
        }
        require(icon == null || CATALOG_ICON_KEY.matches(icon)) {
            "目录图标必须是稳定语义键: $icon"
        }
        require(permissions.none(String::isBlank)) {
            "目录权限不能为空"
        }
        require(permissions.distinct().size == permissions.size) {
            "目录权限不能重复: ${permissions.sorted()}"
        }
        when (kind) {
            LsiCatalogEntryKind.SCENE -> requireScene()
            LsiCatalogEntryKind.ROUTE -> requireRoute()
            LsiCatalogEntryKind.ELEMENT -> requireElement()
        }
    }

    val key: String
        get() = when (kind) {
            LsiCatalogEntryKind.SCENE,
            LsiCatalogEntryKind.ROUTE,
            -> routeKey

            LsiCatalogEntryKind.ELEMENT -> requireNotNull(elementKey)
        }

    private fun requireScene() {
        requireStableRouteKey()
        require(elementKey == null) {
            "场景不能声明 elementKey: $elementKey"
        }
        requireCatalogPath()
        require(parentKey == null) {
            "场景不能声明 parentKey: $parentKey"
        }
    }

    private fun requireRoute() {
        requireStableRouteKey()
        require(elementKey == null) {
            "路由不能声明 elementKey: $elementKey"
        }
        requireCatalogPath()
        requireStableParentKey()
    }

    private fun requireElement() {
        requireStableRouteKey()
        require(elementKey != null && CATALOG_KEY.matches(elementKey)) {
            "elementKey 必须是稳定小写标识: $elementKey"
        }
        require(path == null) {
            "界面元素不能声明 path: $path"
        }
        requireStableParentKey()
        require(parentKey == routeKey) {
            "界面元素 $elementKey 的 parentKey 必须与所属 routeKey 一致"
        }
    }

    private fun requireStableRouteKey() {
        require(CATALOG_KEY.matches(routeKey)) {
            "routeKey 必须是稳定小写标识: $routeKey"
        }
    }

    private fun requireCatalogPath() {
        require(path != null && path.startsWith('/') && !path.contains("//")) {
            "目录路径必须是以 / 开头的绝对路径: $path"
        }
    }

    private fun requireStableParentKey() {
        require(parentKey != null && CATALOG_KEY.matches(parentKey)) {
            "parentKey 必须是稳定小写标识: $parentKey"
        }
    }
}

/** 对约定编译产物执行确定性编解码和全局结构校验。 */
object CatalogContributions {
    fun decode(content: String): List<LsiCatalogEntry> = json.decodeFromString(content)

    fun encode(entries: List<LsiCatalogEntry>): String =
        json.encodeToString(entries.canonical()) + "\n"

    fun resolve(contributions: Iterable<List<LsiCatalogEntry>>): List<LsiCatalogEntry> {
        val entries = contributions.flatten()
        require(entries.map(LsiCatalogEntry::key).distinct().size == entries.size) {
            "目录贡献存在全局键冲突"
        }
        val routeEntries = entries.filter { entry -> entry.kind != LsiCatalogEntryKind.ELEMENT }
        val concreteRoutes = routeEntries.filter { entry -> entry.kind == LsiCatalogEntryKind.ROUTE }
        require(concreteRoutes.mapNotNull(LsiCatalogEntry::path).distinct().size == concreteRoutes.size) {
            "目录贡献存在重复路径"
        }
        val routesByKey = routeEntries.associateBy(LsiCatalogEntry::key)
        entries.forEach { entry ->
            when (entry.kind) {
                LsiCatalogEntryKind.SCENE -> Unit
                LsiCatalogEntryKind.ROUTE -> require(routesByKey[entry.parentKey]?.kind == LsiCatalogEntryKind.SCENE) {
                    "路由 ${entry.key} 必须归属到场景: ${entry.parentKey}"
                }

                LsiCatalogEntryKind.ELEMENT -> require(routesByKey[entry.routeKey]?.kind == LsiCatalogEntryKind.ROUTE) {
                    "界面元素 ${entry.key} 必须归属到具体路由: ${entry.routeKey}"
                }
            }
        }
        routeEntries.filter { entry -> entry.kind == LsiCatalogEntryKind.SCENE }.forEach { scene ->
            require(concreteRoutes.any { route -> route.parentKey == scene.key && route.path == scene.path }) {
                "场景 ${scene.key} 的默认路径没有对应子路由: ${scene.path}"
            }
        }
        return entries.canonical()
    }

    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }
}

private fun List<LsiCatalogEntry>.canonical(): List<LsiCatalogEntry> = map { entry ->
    entry.copy(permissions = entry.permissions.sorted())
}.sortedWith(compareBy(LsiCatalogEntry::kind, LsiCatalogEntry::order, LsiCatalogEntry::key))

private val CATALOG_KEY = Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*")
private val CATALOG_ICON_KEY = Regex("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")
