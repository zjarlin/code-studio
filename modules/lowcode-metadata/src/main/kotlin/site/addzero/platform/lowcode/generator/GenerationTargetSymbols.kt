package site.addzero.platform.lowcode.generator

/** 生成代码依赖的宿主语义符号；profile 使用这些稳定键提供实际包名或类型。 */
object GenerationTargetSymbols {
    const val PERSISTENCE_MODEL_PACKAGE = "runtime.persistence-model-package"
    const val LOWCODE_RUNTIME_PACKAGE = "runtime.lowcode-package"
    const val WEB_RUNTIME_PACKAGE = "runtime.web-package"
    const val CORE_RUNTIME_PACKAGE = "runtime.core-package"
    const val AUDIT_PRINCIPAL = "runtime.audit-principal"
    const val DICTIONARY_ANNOTATION = "runtime.dictionary-annotation"
    const val AGENT_RUNTIME_PACKAGE = "extension.agent-package"

    val keys: Set<String> = setOf(
        PERSISTENCE_MODEL_PACKAGE,
        LOWCODE_RUNTIME_PACKAGE,
        WEB_RUNTIME_PACKAGE,
        CORE_RUNTIME_PACKAGE,
        AUDIT_PRINCIPAL,
        DICTIONARY_ANNOTATION,
        AGENT_RUNTIME_PACKAGE,
    )

    fun reference(key: String): String {
        return requireNotNull(references[key]) { "未知的生成目标语义符号: $key" }
    }

    fun referencedKeys(content: String): Set<String> = referencePattern.findAll(content)
        .map { match -> keysByReference.getValue(match.value) }
        .toSortedSet()

    fun render(content: String, symbols: Map<String, String>): String = referencePattern.replace(content) { match ->
        symbols.getValue(keysByReference.getValue(match.value))
    }

    private val references = mapOf(
        PERSISTENCE_MODEL_PACKAGE to "code.studio.target.runtime_persistence_model",
        LOWCODE_RUNTIME_PACKAGE to "code.studio.target.runtime_lowcode",
        WEB_RUNTIME_PACKAGE to "code.studio.target.runtime_web",
        CORE_RUNTIME_PACKAGE to "code.studio.target.runtime_core",
        AUDIT_PRINCIPAL to "code.studio.target.RuntimeAuditPrincipal",
        DICTIONARY_ANNOTATION to "code.studio.target.RuntimeDictionaryAnnotation",
        AGENT_RUNTIME_PACKAGE to "code.studio.target.extension_agent",
    )
    private val keysByReference = references.entries.associate { (key, reference) -> reference to key }
    private val referencePattern = Regex(
        references.values.sortedByDescending(String::length).joinToString("|") { reference ->
            Regex.escape(reference)
        },
    )
}

fun generationTargetSymbol(key: String): String = GenerationTargetSymbols.reference(key)
