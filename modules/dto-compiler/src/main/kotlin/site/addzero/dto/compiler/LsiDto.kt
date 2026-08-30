package site.addzero.dto.compiler

/** 与上层文件布局解耦的 Kotlin DTO 源码。 */
data class KotlinDtoSource(
    val packageName: String,
    val fileName: String,
    val content: String,
)
