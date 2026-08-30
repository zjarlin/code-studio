package site.addzero.studio.contract

import kotlinx.serialization.Serializable

@Serializable
data class ConstantItemCommand(
    val id: Long? = null,
    val name: String,
    val type: String,
    val value: String,
    val description: String,
)

@Serializable
data class ConstantCommand(
    val id: Long? = null,
    val featureId: Long,
    val groupCode: String,
    val objectName: String,
    val description: String,
    val status: Int = 1,
    val constants: List<ConstantItemCommand>,
)

@Serializable
data class ConstantListCommand(
    val featureId: Long? = null,
)
