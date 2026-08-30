package site.addzero.platform.lowcode.generator

/** 生成源码在功能目录下的资源类型。 */
enum class LowcodeGeneratedResourceKind(
    val directoryName: String,
) {
    CONTROLLER("controller"),
    SERVICE("service"),
    DTO("dto"),
    ENTITY("entity"),
    ENUMS("enums"),
    CONSTANTS("constants"),
}

/** 首次创建后由业务代码维护的源码类型。 */
enum class LowcodeScaffoldResourceKind(
    val packageSuffix: String,
) {
    CONTROLLER("controller"),
    SERVICE("service"),
    SERVICE_IMPLEMENTATION("service.impl"),
    SCHEDULED_JOB("job"),
}

/**
 * 将业务功能包稳定映射为确定性生成目录和一次性业务脚手架目录。
 *
 * 元数据只保存业务功能包；源码目录和包名始终由这里推导。
 */
data class LowcodeGeneratedLayout(
    val featurePackage: String,
) {
    init {
        require(featurePackage.isNotBlank()) { "功能包名不能为空" }
        require('.' in featurePackage) { "功能包名必须包含功能根包: $featurePackage" }
        require(".generated" !in featurePackage) { "功能包名不能包含 generated: $featurePackage" }
    }

    val generatedFeaturePackage: String = "$featurePackage.generated"

    fun packageName(kind: LowcodeGeneratedResourceKind): String =
        "$generatedFeaturePackage.${kind.directoryName}"

    fun qualifiedName(kind: LowcodeGeneratedResourceKind, className: String): String =
        "${packageName(kind)}.$className"

    fun relativeSourcePath(
        kind: LowcodeGeneratedResourceKind,
        fileName: String,
    ): String {
        val packagePath = packageName(kind).replace('.', '/')
        return "src/main/kotlin/$packagePath/$fileName.kt"
    }

    fun relativeFeatureFilePath(fileName: String): String {
        val packagePath = featurePackage.replace('.', '/')
        return "src/main/kotlin/$packagePath/$fileName"
    }

    fun scaffoldPackageName(kind: LowcodeScaffoldResourceKind): String =
        "$featurePackage.${kind.packageSuffix}"

    fun relativeScaffoldSourcePath(
        kind: LowcodeScaffoldResourceKind,
        fileName: String,
    ): String {
        val packagePath = scaffoldPackageName(kind).replace('.', '/')
        return "src/main/kotlin/$packagePath/$fileName.kt"
    }
}

fun String.generatedLayout(): LowcodeGeneratedLayout = LowcodeGeneratedLayout(this)
