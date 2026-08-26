package site.addzero.constant.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.validate

/** 收集常量元数据并在处理结束时统一生成源码。 */
class ConstantProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        object : SymbolProcessor {
            private val groups = mutableSetOf<LsiConstantGroup>()
            private val sourceFiles = mutableMapOf<String, KSFile>()

            override fun process(resolver: Resolver): List<KSAnnotated> {
                val symbols = resolver.getSymbolsWithAnnotation(GENERATE_CONSTANTS_ANNOTATION).toList()
                val deferred = symbols.filterNot { symbol -> symbol.validate() }
                symbols.filter { symbol -> symbol.validate() }
                    .filterIsInstance<KSClassDeclaration>()
                    .forEach { declaration ->
                        declaration.toLsiConstantGroup(environment.logger)?.let { group ->
                            groups += group
                            declaration.containingFile?.let { file -> sourceFiles[group.qualifiedName] = file }
                        }
                    }
                return deferred
            }

            override fun finish() {
                groups.sortedBy(LsiConstantGroup::qualifiedName).forEach { group ->
                    generateSource(environment.codeGenerator, group, sourceFiles[group.qualifiedName])
                }
            }
        }
}

private fun KSClassDeclaration.toLsiConstantGroup(logger: KSPLogger): LsiConstantGroup? {
    val annotation = annotations.firstOrNull { candidate ->
        candidate.annotationType.resolve().declaration.qualifiedName?.asString() == GENERATE_CONSTANTS_ANNOTATION
    } ?: return null
    return runCatching {
        val constants = annotation.argument("constants")
            .asAnnotationList()
            .map(KSAnnotation::toLsiConstant)
        LsiConstantGroup(
            packageName = "${packageName.asString()}.generated",
            objectName = annotation.stringArgument("objectName"),
            description = annotation.stringArgument("description"),
            constants = constants,
        )
    }.getOrElse { error ->
        logger.error(error.message ?: "常量元数据解析失败", this)
        null
    }
}

private fun KSAnnotation.toLsiConstant(): LsiConstant = LsiConstant(
    name = stringArgument("name"),
    type = LsiConstantType.valueOf(argument("type").toString().substringAfterLast('.')),
    value = stringArgument("value"),
    description = stringArgument("description"),
)

private fun KSAnnotation.stringArgument(name: String): String = argument(name) as? String
    ?: error("注解参数 $name 必须是字符串")

private fun KSAnnotation.argument(name: String): Any? = arguments
    .firstOrNull { argument -> argument.name?.asString() == name }
    ?.value
    ?: error("缺少注解参数: $name")

private fun Any?.asAnnotationList(): List<KSAnnotation> = (this as? List<*>)
    ?.map { value -> value as? KSAnnotation ?: error("常量元数据格式不合法") }
    ?: error("常量元数据必须是数组")

private fun generateSource(
    codeGenerator: CodeGenerator,
    group: LsiConstantGroup,
    sourceFile: KSFile?,
) {
    val source = KotlinConstantSourceGenerator.generate(group)
    val dependencies = sourceFile
        ?.let { file -> Dependencies(aggregating = false, file) }
        ?: Dependencies.ALL_FILES
    codeGenerator.createNewFile(
        dependencies = dependencies,
        packageName = source.packageName,
        fileName = source.fileName,
    ).bufferedWriter().use { writer -> writer.write(source.content) }
}

private val LsiConstantGroup.qualifiedName: String
    get() = "$packageName.$objectName"

private const val GENERATE_CONSTANTS_ANNOTATION = "site.addzero.constant.compiler.GenerateConstants"
