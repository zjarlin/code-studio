package site.addzero.platform.lowcode.generator

import kotlinx.serialization.Serializable

@Serializable
enum class LsiConventionFileKind {
    SERVICE,
    SCHEDULED_JOB,
}

@Serializable
data class LsiConventionFile(
    val fileCode: String,
    val name: String,
    val className: String,
    val kind: LsiConventionFileKind,
    val packageName: String,
    val contributorId: String,
    val description: String? = null,
) {
    init {
        require(fileCode.matches(FILE_CODE)) { "约定文件编码必须使用小驼峰标识: $fileCode" }
        require(name.isNotBlank()) { "约定文件注释不能为空: $fileCode" }
        require(className.matches(KOTLIN_CLASS_NAME)) { "约定文件类名不合法: $className" }
        require(className.endsWith(kind.classNameSuffix)) {
            "${kind.displayName}类名必须以 ${kind.classNameSuffix} 结尾: $className"
        }
        require(packageName.isNotBlank() && ".generated" !in packageName) {
            "约定文件必须归属业务功能包: $packageName"
        }
        require(contributorId.isNotBlank()) { "约定文件 contributorId 不能为空: $fileCode" }
    }
}

internal val LsiConventionFileKind.classNameSuffix: String
    get() = when (this) {
        LsiConventionFileKind.SERVICE -> "Service"
        LsiConventionFileKind.SCHEDULED_JOB -> "Job"
    }

private val LsiConventionFileKind.displayName: String
    get() = when (this) {
        LsiConventionFileKind.SERVICE -> "Service"
        LsiConventionFileKind.SCHEDULED_JOB -> "定时任务"
    }

private val FILE_CODE = Regex("[a-z][A-Za-z0-9]*")
private val KOTLIN_CLASS_NAME = Regex("[A-Z][A-Za-z0-9_]*")
