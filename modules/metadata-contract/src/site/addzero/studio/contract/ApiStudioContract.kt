package site.addzero.studio.contract

import kotlinx.serialization.Serializable

@Serializable
data class ApiRequestCommand(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val multipart: List<ApiMultipartPart> = emptyList(),
)

@Serializable
data class ApiMultipartPart(
    val name: String,
    val value: String? = null,
    val bytes: ByteArray? = null,
    val fileName: String? = null,
    val contentType: String? = null,
)

@Serializable
data class ApiResponseView(
    val status: Int,
    val statusText: String,
    val durationMillis: Long,
    val headers: Map<String, String>,
    val bodyText: String? = null,
    val bytes: ByteArray? = null,
    val fileName: String? = null,
    val contentType: String? = null,
    val requestUrl: String,
    val curl: String,
)

@Serializable
data class ApiAuthSession(
    val id: String,
    val name: String,
    val token: String,
)

@Serializable
data class ApiHistoryEntry(
    val id: String,
    val method: String,
    val url: String,
    val status: Int,
    val durationMillis: Long,
    val createdAt: Long,
)
