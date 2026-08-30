package site.addzero.studio.contract

import kotlinx.serialization.Serializable

@Serializable
enum class ConventionFileKind { SERVICE, SCHEDULED_JOB }

@Serializable
data class ConventionFileCommand(
    val id: Long? = null,
    val featureId: Long,
    val fileCode: String,
    val name: String,
    val className: String,
    val kind: ConventionFileKind,
    val status: Int = 1,
    val description: String? = null,
)

@Serializable
data class ConventionFileView(
    val id: Long,
    val featureId: Long,
    val fileCode: String,
    val name: String,
    val className: String,
    val kind: ConventionFileKind,
    val status: Int,
    val description: String? = null,
    val packageName: String,
    val contributorId: String,
)
