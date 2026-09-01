package site.addzero.studio.server.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.addzero.studio.contract.LsiCatalogEntry
import site.addzero.studio.contract.LsiCatalogEntryKind
import site.addzero.studio.runtime.StudioAccessRequest
import site.addzero.studio.runtime.StudioPermissionPolicy
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource

internal class StudioCatalogService(
    private val baseEntries: List<LsiCatalogEntry>,
    private val overrideReader: CatalogOverrideReader,
    private val permissionPolicy: StudioPermissionPolicy,
) : StudioCatalogProvider {
    override suspend fun entries(request: StudioAccessRequest): List<LsiCatalogEntry> {
        val overrides = withContext(Dispatchers.IO) {
            overrideReader.read()
        }
        val effectiveEntries = baseEntries.map { entry -> entry.applyOverride(overrides[entry]) }
        val permissionGrants = effectiveEntries
            .flatMap(LsiCatalogEntry::permissions)
            .distinct()
            .sorted()
            .associateWith { permission -> permissionPolicy.isGranted(request, permission) }
        val permittedEntries = effectiveEntries.filter { entry ->
            entry.enabled && entry.permissions.all { permission -> permissionGrants.getValue(permission) }
        }
        return permittedEntries.retainReachableEntries()
    }
}

internal fun interface CatalogOverrideReader {
    fun read(): CatalogOverrides
}

internal data class CatalogOverrides(
    val routes: Map<String, CatalogEntryOverride> = emptyMap(),
    val elements: Map<String, CatalogEntryOverride> = emptyMap(),
) {
    operator fun get(entry: LsiCatalogEntry): CatalogEntryOverride? = when (entry.kind) {
        LsiCatalogEntryKind.SCENE,
        LsiCatalogEntryKind.ROUTE,
        -> routes[entry.key]

        LsiCatalogEntryKind.ELEMENT -> elements[entry.key]
    }
}

internal data class CatalogEntryOverride(
    val name: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val order: Int? = null,
    val permissions: List<String>? = null,
    val enabled: Boolean? = null,
)

internal class JdbcCatalogOverrideReader(
    private val dataSource: DataSource,
    private val schemaName: String,
) : CatalogOverrideReader {
    init {
        require(STUDIO_SCHEMA_NAME.matches(schemaName)) {
            "Studio schema 不是安全的 PostgreSQL 标识符: $schemaName"
        }
    }

    override fun read(): CatalogOverrides = dataSource.connection.use { connection ->
        CatalogOverrides(
            routes = connection.readOverrides("catalog_route_override", "route_key"),
            elements = connection.readOverrides("catalog_element_override", "element_key"),
        )
    }

    private fun Connection.readOverrides(table: String, keyColumn: String): Map<String, CatalogEntryOverride> {
        val sql = """
            SELECT $keyColumn, name, description, icon, order_no, permissions, enabled
            FROM $schemaName.$table
            ORDER BY $keyColumn
        """.trimIndent()
        return prepareStatement(sql).use { statement ->
            statement.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        put(rows.getString(keyColumn), rows.toOverride())
                    }
                }
            }
        }
    }
}

private fun ResultSet.toOverride(): CatalogEntryOverride = CatalogEntryOverride(
    name = getString("name"),
    description = getString("description"),
    icon = getString("icon"),
    order = getInt("order_no").takeUnless { wasNull() },
    permissions = getArray("permissions")?.array?.let { value ->
        (value as Array<*>).map { permission -> permission.toString() }
    },
    enabled = getBoolean("enabled").takeUnless { wasNull() },
)

private fun LsiCatalogEntry.applyOverride(override: CatalogEntryOverride?): LsiCatalogEntry {
    if (override == null) {
        return this
    }
    return copy(
        name = override.name ?: name,
        description = override.description ?: description,
        icon = override.icon ?: icon,
        order = override.order ?: order,
        permissions = override.permissions ?: permissions,
        enabled = override.enabled ?: enabled,
    )
}

private fun List<LsiCatalogEntry>.retainReachableEntries(): List<LsiCatalogEntry> {
    val routesByKey = filter { entry -> entry.kind != LsiCatalogEntryKind.ELEMENT }
        .associateBy(LsiCatalogEntry::key)
    val visibleRoutes = routesByKey.values
        .filter { entry -> entry.kind == LsiCatalogEntryKind.ROUTE }
        .filter { entry -> routesByKey[entry.parentKey]?.kind == LsiCatalogEntryKind.SCENE }
    val visibleRouteKeys = visibleRoutes.mapTo(mutableSetOf(), LsiCatalogEntry::key)
    val routesByScene = visibleRoutes.groupBy(LsiCatalogEntry::parentKey)
    return mapNotNull { entry ->
        when (entry.kind) {
            LsiCatalogEntryKind.SCENE -> entry.withVisibleDefaultRoute(routesByScene[entry.key].orEmpty())
            LsiCatalogEntryKind.ROUTE -> entry.takeIf { entry.key in visibleRouteKeys }
            LsiCatalogEntryKind.ELEMENT -> entry.takeIf { entry.routeKey in visibleRouteKeys }
        }
    }.sortedWith(compareBy(LsiCatalogEntry::kind, LsiCatalogEntry::order, LsiCatalogEntry::key))
}

private fun LsiCatalogEntry.withVisibleDefaultRoute(routes: List<LsiCatalogEntry>): LsiCatalogEntry? {
    if (routes.isEmpty()) {
        return null
    }
    if (routes.any { route -> route.path == path }) {
        return this
    }
    val defaultRoute = routes.minWith(compareBy(LsiCatalogEntry::order, LsiCatalogEntry::key))
    return copy(path = defaultRoute.path)
}

private val STUDIO_SCHEMA_NAME = Regex("[a-z_][a-z0-9_]{0,62}")
