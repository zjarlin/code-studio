package site.addzero.platform.lowcode.generator

import site.addzero.constant.compiler.KotlinConstantSourceGenerator

/** 将功能常量元数据适配到通用常量编译器。 */
object LowcodeConstantSourceGenerator {
    fun generate(
        groups: List<LowcodeConstantGroupMeta>,
        contributorId: String,
    ): List<LowcodeGeneratedFile> = groups
        .filter { group -> group.contributorId == contributorId }
        .sortedWith(compareBy(LowcodeConstantGroupMeta::featurePackageName, LowcodeConstantGroupMeta::objectName))
        .map { group ->
            val layout = group.featurePackageName.generatedLayout()
            val source = KotlinConstantSourceGenerator.generate(
                group.toLsiConstantGroup(),
            )
            LowcodeGeneratedFile(
                packageName = source.packageName,
                fileName = source.fileName,
                relativePath = layout.relativeSourcePath(
                    kind = LowcodeGeneratedResourceKind.CONSTANTS,
                    fileName = source.fileName,
                ),
                content = generatedByStudio(source.content),
            )
        }
}
