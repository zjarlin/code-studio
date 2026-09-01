package site.addzero.studio.contract

import kotlinx.serialization.Serializable

@Serializable
data class ExampleHelloResponse(
    val message: String,
    val category: String,
    val value: Int,
    val imagePath: String,
)
