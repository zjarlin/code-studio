package site.addzero.platform.lowcode.generator

import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections

/** 可由宿主替换的可编辑源码脚手架模板。 */
class SourceTemplateCatalog private constructor(
    sources: Map<SourceTemplateKind, String>,
) {
    private val templates = sources.mapValues { (_, source) -> source.normalizeNewlines() }

    init {
        require(templates.keys == SourceTemplateKind.entries.toSet()) {
            "源码模板目录必须包含全部约定模板"
        }
        templates.forEach { (kind, template) -> kind.validate(template.normalizeNewlines()) }
    }

    fun source(kind: SourceTemplateKind): String = templates.getValue(kind)

    fun render(
        kind: SourceTemplateKind,
        values: Map<String, String>,
    ): String {
        val template = templates.getValue(kind).normalizeNewlines()
        val variables = TEMPLATE_VARIABLE.findAll(template)
            .map { match -> match.groupValues[1] }
            .toSet()
        val missingValues = variables - values.keys
        require(missingValues.isEmpty()) {
            "源码模板 ${kind.fileName} 缺少渲染值: ${missingValues.sorted().joinToString()}"
        }
        val rendered = variables.sorted().fold(template) { source, variable ->
            source.replace("{{$variable}}", values.getValue(variable))
        }
        require(!TEMPLATE_VARIABLE.containsMatchIn(rendered)) {
            "源码模板 ${kind.fileName} 存在未渲染变量"
        }
        return rendered.lineSequence()
            .joinToString("\n") { line -> line.trimEnd() }
            .trimEnd() + "\n"
    }

    companion object {
        val DEFAULT: SourceTemplateCatalog = SourceTemplateCatalog(DEFAULT_SOURCE_TEMPLATES)

        /** 从构建输入目录读取一套完整模板。 */
        fun read(directory: Path): SourceTemplateCatalog {
            require(Files.isDirectory(directory)) {
                "源码模板目录不存在: $directory"
            }
            val templates = SourceTemplateKind.entries.associateWith { kind ->
                val file = directory.resolve(kind.fileName)
                require(Files.isRegularFile(file)) {
                    "源码模板文件不存在: $file"
                }
                Files.readString(file)
            }
            return SourceTemplateCatalog(templates)
        }

        /** 从指定 contributor 的 JAR 资源读取模板，旧制品缺少模板时使用兼容默认值。 */
        fun load(
            contributorId: String,
            classLoader: ClassLoader = Thread.currentThread().contextClassLoader
                ?: SourceTemplateCatalog::class.java.classLoader,
        ): SourceTemplateCatalog {
            require(CONTRIBUTOR_ID.matches(contributorId)) {
                "模板 contributorId 不合法: $contributorId"
            }
            val loaded = SourceTemplateKind.entries.mapNotNull { kind ->
                val resources = Collections.list(classLoader.getResources(kind.resourcePath(contributorId)))
                    .sortedBy(URL::toExternalForm)
                if (resources.isEmpty()) {
                    return@mapNotNull null
                }
                val contents = resources.map(URL::readText).distinct()
                require(contents.size == 1) {
                    "classpath 包含不一致的源码模板: ${kind.resourcePath(contributorId)}"
                }
                kind to contents.single()
            }.toMap()
            if (loaded.isEmpty()) {
                return DEFAULT
            }
            val missing = SourceTemplateKind.entries.toSet() - loaded.keys
            require(missing.isEmpty()) {
                "contributor $contributorId 缺少源码模板: ${missing.map(SourceTemplateKind::fileName).sorted().joinToString()}"
            }
            return SourceTemplateCatalog(loaded)
        }
    }
}

/** 可编辑脚手架模板类型。 */
enum class SourceTemplateKind(
    val fileName: String,
    internal val requiredVariables: Set<String>,
) {
    CONTROLLER(
        fileName = "controller.kt.tpl",
        requiredVariables = setOf("header", "className", "serviceName", "controllerTypes", "routeKey"),
    ),
    SERVICE_IMPLEMENTATION(
        fileName = "service-implementation.kt.tpl",
        requiredVariables = setOf("header", "className", "implementationType", "entityName", "serviceName"),
    ),
    CONVENTION_SERVICE(
        fileName = "service.kt.tpl",
        requiredVariables = setOf("header", "className"),
    ),
    SCHEDULED_JOB(
        fileName = "scheduled-job.kt.tpl",
        requiredVariables = setOf("header", "className"),
    ),
    ;

    fun resourcePath(contributorId: String): String =
        "$SOURCE_TEMPLATE_RESOURCE_ROOT/$contributorId/$fileName"
}

private fun SourceTemplateKind.validate(template: String) {
    require(template.startsWith(HEADER_PLACEHOLDER)) {
        "源码模板 $fileName 必须以 $HEADER_PLACEHOLDER 开头"
    }
    val variables = TEMPLATE_VARIABLE.findAll(template)
        .map { match -> match.groupValues[1] }
        .toSet()
    val missing = requiredVariables - variables
    require(missing.isEmpty()) {
        "源码模板 $fileName 缺少变量: ${missing.sorted().joinToString()}"
    }
    val unknown = variables - (requiredVariables + DOCUMENTATION_VARIABLE)
    require(unknown.isEmpty()) {
        "源码模板 $fileName 包含未知变量: ${unknown.sorted().joinToString()}"
    }
}

const val SOURCE_TEMPLATE_RESOURCE_ROOT: String = "META-INF/code-studio/templates"

private val DEFAULT_SOURCE_TEMPLATES = mapOf(
    SourceTemplateKind.CONTROLLER to """
        {{header}}

        {{documentation}}
        @Single
        class {{className}}(
            override val service: {{serviceName}},
        ) : {{controllerTypes}} {
            override val routeKey = "{{routeKey}}"
        }
    """.trimIndent() + "\n",
    SourceTemplateKind.SERVICE_IMPLEMENTATION to """
        {{header}}

        {{documentation}}
        @Single
        class {{className}} : {{implementationType}}<{{entityName}}>(), {{serviceName}}
    """.trimIndent() + "\n",
    SourceTemplateKind.CONVENTION_SERVICE to """
        {{header}}

        {{documentation}}
        @Single
        class {{className}}
    """.trimIndent() + "\n",
    SourceTemplateKind.SCHEDULED_JOB to """
        {{header}}

        {{documentation}}
        @Single
        class {{className}} : ScheduledJob {
            override val schedule: String = "0 0 0 * * * 480o"

            override suspend fun execute() = Unit
        }
    """.trimIndent() + "\n",
)

private fun URL.readText(): String = openStream().bufferedReader().use { reader -> reader.readText() }

private fun String.normalizeNewlines(): String = replace("\r\n", "\n").replace('\r', '\n')

private val TEMPLATE_VARIABLE = Regex("""\{\{([a-z][A-Za-z0-9]*)}}""")
private val CONTRIBUTOR_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
private const val HEADER_PLACEHOLDER = "{{header}}"
private const val DOCUMENTATION_VARIABLE = "documentation"
