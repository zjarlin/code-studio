package site.addzero.studio.runtime

/** 生成器访问的宿主运行时符号。 */
data class GenerationTargetProfile(
    val id: String,
    val symbols: Map<String, String>,
    val capabilities: Set<String> = emptySet(),
) {
    init {
        require(id.isNotBlank()) {
            "生成目标 id 不能为空"
        }
        require(symbols.keys.none(String::isBlank)) {
            "生成目标符号名不能为空"
        }
        require(symbols.keys.all { key ->
            TARGET_SYMBOL_KEY.matches(key) && TARGET_SYMBOL_PREFIXES.any(key::startsWith)
        }) {
            "生成目标符号必须使用稳定语义键: ${symbols.keys.sorted()}"
        }
        require(symbols.values.none(String::isBlank)) {
            "生成目标符号值不能为空"
        }
        require(symbols.values.all(TARGET_SYMBOL_VALUE::matches)) {
            "生成目标符号值必须是 Kotlin 包名、类型或函数限定名: ${symbols.values.sorted()}"
        }
        require(capabilities.all(TARGET_CAPABILITY::matches)) {
            "生成目标 capability 必须是稳定的小写标识: ${capabilities.sorted()}"
        }
    }
}

private val TARGET_SYMBOL_KEY = Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*")
private val TARGET_SYMBOL_VALUE = Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+")
private val TARGET_CAPABILITY = Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*")
private val TARGET_SYMBOL_PREFIXES = setOf("runtime.", "extension.")

/** Studio 访问策略所需的框架无关请求信息。 */
data class StudioAccessRequest(
    val method: String,
    val path: String,
    val headers: Map<String, List<String>>,
)

/** 由宿主决定当前请求能否访问 Studio。 */
fun interface StudioAccessPolicy {
    suspend fun isAllowed(request: StudioAccessRequest): Boolean
}

/** 由宿主使用已有授权语义判定目录权限。 */
fun interface StudioPermissionPolicy {
    suspend fun isGranted(request: StudioAccessRequest, permission: String): Boolean
}

/** 单应用 Studio 宿主配置。 */
data class StudioConfig(
    val contributorId: String,
    val apiBaseUrl: String,
    val displayName: String = contributorId,
    val openApiPath: String = "/v3/api-docs",
    val editableContributorId: String = contributorId,
    val capabilities: Set<String> = emptySet(),
    val enabled: Boolean = false,
) {
    init {
        require(contributorId.isNotBlank()) {
            "Studio contributorId 不能为空"
        }
        require(displayName.isNotBlank()) {
            "Studio displayName 不能为空"
        }
        require(apiBaseUrl.startsWith('/')) {
            "Studio apiBaseUrl 必须以 / 开头"
        }
        require(openApiPath.startsWith('/')) {
            "Studio openApiPath 必须以 / 开头"
        }
        require(editableContributorId == contributorId) {
            "Studio 只能编辑当前宿主的元数据贡献"
        }
        require(capabilities.none(String::isBlank)) {
            "Studio capability 不能为空"
        }
    }
}
