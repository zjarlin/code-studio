package site.addzero.constant.compiler

/** 跨元数据提供端的常量值类型。 */
enum class LsiConstantType {
    BOOLEAN,
    INT,
    LONG,
    STRING,
}

/** 一条已解析常量。 */
data class LsiConstant(
    val name: String,
    val type: LsiConstantType,
    val value: String,
    val description: String,
)

/** 一个将生成为 Kotlin `object` 的常量组。 */
data class LsiConstantGroup(
    val packageName: String,
    val objectName: String,
    val description: String,
    val constants: List<LsiConstant>,
)
