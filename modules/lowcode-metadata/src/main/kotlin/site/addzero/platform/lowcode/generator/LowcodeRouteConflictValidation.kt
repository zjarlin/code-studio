package site.addzero.platform.lowcode.generator

/** 校验业务 REST 路由不会覆盖元数据生成的通用路由。 */
internal fun LowcodeMetadata.requireNoGeneratedRouteConflicts() {
    val generatedRoutes = buildList {
        models.forEach { model ->
            model.routeConfig?.generatedRouteSignatures(model.modelCode)?.let(::addAll)
        }
        routeBindings.forEach { binding ->
            addAll(binding.route.generatedRouteSignatures(binding.routeCode))
        }
    }
    val routesBySignature = generatedRoutes.groupBy(OwnedRouteSignature::signature)
    val businessRoutes = buildList {
        models.forEach { model ->
            model.routeConfig?.customOperations.orEmpty().forEach { operation ->
                operation.businessRouteSignature("模型 ${model.modelCode}")?.let(::add)
            }
        }
        routeBindings.forEach { binding ->
            binding.route.customOperations.forEach { operation ->
                operation.businessRouteSignature("路由绑定 ${binding.routeCode}")?.let(::add)
            }
        }
        contracts.forEach { contract ->
            contract.operations.forEach { operation ->
                operation.businessRouteSignature("契约 ${contract.contractCode}")?.let(::add)
            }
        }
    }
    val conflicts = businessRoutes.flatMap { businessRoute ->
        routesBySignature[businessRoute.signature].orEmpty().map { generatedRoute ->
            "${businessRoute.owner} ${businessRoute.signature} 与 ${generatedRoute.owner} 的通用路由重复"
        }
    }.distinct()
    require(conflicts.isEmpty()) {
        "业务 REST 路径不能命中低代码通用路由：${conflicts.joinToString("；")}"
    }
}

private fun LsiLowcodeRoute.generatedRouteSignatures(owner: String): List<OwnedRouteSignature> {
    if (!generateController) {
        return emptyList()
    }
    val basePaths = listOf(path) + aliasPaths
    val operationRoutes = enabledOperations.flatMap { operation ->
        GENERATED_OPERATION_ROUTES[operation].orEmpty()
    }
    val excelRoutes = buildList {
        if (excel?.exportEnabled == true) {
            add(RouteSuffix(LowcodeHttpMethod.GET, "/export-excel"))
        }
        if (excel?.importEnabled == true) {
            add(RouteSuffix(LowcodeHttpMethod.GET, "/get-import-template"))
            add(RouteSuffix(LowcodeHttpMethod.POST, "/import"))
        }
    }
    return basePaths.flatMap { basePath ->
        (operationRoutes + excelRoutes).map { route ->
            OwnedRouteSignature(owner, RouteSignature(route.method, basePath.joinRoutePath(route.suffix)))
        }
    }
}

private fun LsiLowcodeCustomOperation.businessRouteSignature(owner: String): OwnedRouteSignature? {
    if (transport != LowcodeOperationTransport.HTTP || implementation == LowcodeOperationImplementation.SERVICE_ONLY) {
        return null
    }
    return OwnedRouteSignature(owner, RouteSignature(method, path.normalizedRoutePath()))
}

private fun String.joinRoutePath(suffix: String): String =
    "${trimEnd('/')}${suffix}".normalizedRoutePath()

private fun String.normalizedRoutePath(): String = if (length > 1) trimEnd('/') else this

private data class RouteSignature(
    val method: LowcodeHttpMethod,
    val path: String,
) {
    override fun toString(): String = "$method $path"
}

private data class OwnedRouteSignature(
    val owner: String,
    val signature: RouteSignature,
)

private data class RouteSuffix(
    val method: LowcodeHttpMethod,
    val suffix: String,
)

private val GENERATED_OPERATION_ROUTES = mapOf(
    "CREATE" to listOf(RouteSuffix(LowcodeHttpMethod.POST, "/create")),
    "UPSERT" to listOf(RouteSuffix(LowcodeHttpMethod.POST, "/upsert")),
    "UPDATE" to listOf(RouteSuffix(LowcodeHttpMethod.PUT, "/update")),
    "DELETE" to listOf(RouteSuffix(LowcodeHttpMethod.DELETE, "/delete")),
    "DELETE_LIST" to listOf(RouteSuffix(LowcodeHttpMethod.DELETE, "/delete-list")),
    "GET" to listOf(RouteSuffix(LowcodeHttpMethod.GET, "/get")),
    "PAGE" to listOf(RouteSuffix(LowcodeHttpMethod.GET, "/page")),
    "SIMPLE_LIST" to listOf(
        RouteSuffix(LowcodeHttpMethod.GET, "/simple-list"),
        RouteSuffix(LowcodeHttpMethod.GET, "/list"),
        RouteSuffix(LowcodeHttpMethod.GET, "/list-all"),
        RouteSuffix(LowcodeHttpMethod.GET, "/list-by-condition"),
        RouteSuffix(LowcodeHttpMethod.GET, "/list-simple"),
        RouteSuffix(LowcodeHttpMethod.GET, "/list-all-simple"),
    ),
    "LIST_BY_CONDITION" to listOf(RouteSuffix(LowcodeHttpMethod.POST, "/list-by-condition")),
    "TREE" to listOf(
        RouteSuffix(LowcodeHttpMethod.GET, "/tree"),
        RouteSuffix(LowcodeHttpMethod.GET, "/breadcrumb"),
        RouteSuffix(LowcodeHttpMethod.PUT, "/move"),
        RouteSuffix(LowcodeHttpMethod.PUT, "/change-parent"),
    ),
)
