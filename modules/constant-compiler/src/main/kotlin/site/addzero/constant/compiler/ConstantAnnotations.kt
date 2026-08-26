package site.addzero.constant.compiler

/** Kotlin 编译期常量支持的值类型。 */
enum class ConstantType {
    BOOLEAN,
    INT,
    LONG,
    STRING,
}

/** 一条结构化常量元数据。 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ConstantMetadata(
    val name: String,
    val type: ConstantType,
    val value: String,
    val description: String,
)

/** 在当前声明的 `generated` 子包生成 Kotlin 常量对象。 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateConstants(
    val objectName: String,
    val description: String,
    val constants: Array<ConstantMetadata>,
)
