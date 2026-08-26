package site.addzero.platform.lowcode.generator

import site.addzero.constant.compiler.LsiConstant
import site.addzero.constant.compiler.LsiConstantGroup

/** 归属于一个功能目录的常量组快照。 */
data class LowcodeConstantGroupMeta(
    val groupCode: String,
    val featurePackageName: String,
    val contributorId: String,
    val objectName: String,
    val description: String,
    val constants: List<LsiConstant>,
)

/** 将低代码常量组收敛为通用常量 LSI。 */
fun LowcodeConstantGroupMeta.toLsiConstantGroup(): LsiConstantGroup {
    val packageName = featurePackageName.generatedLayout()
        .packageName(LowcodeGeneratedResourceKind.CONSTANTS)
    return LsiConstantGroup(
        packageName = packageName,
        objectName = objectName,
        description = description,
        constants = constants,
    )
}
