package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.constant.compiler.LsiConstant
import site.addzero.constant.compiler.LsiConstantType

class LowcodeConstantSourceGeneratorTest {
    @Test
    fun `generates documented constants under the owning feature`() {
        val files = LowcodeConstantSourceGenerator.generate(
            groups = listOf(
                LowcodeConstantGroupMeta(
                    groupCode = "messageStatus",
                    featurePackageName = "example.message",
                    contributorId = "example.message",
                    objectName = "MessageConstants",
                    description = "消息业务常量。",
                    constants = listOf(
                        LsiConstant("ENABLED_STATUS", LsiConstantType.INT, "1", "已启用状态。"),
                    ),
                ),
            ),
            contributorId = "example.message",
        )

        assertEquals(1, files.size)
        assertEquals("example.message.generated.constants", files.single().packageName)
        assertEquals(
            "src/main/kotlin/example/message/generated/constants/MessageConstants.kt",
            files.single().relativePath,
        )
        assertTrue(files.single().content.contains("/** 已启用状态。 */"))
        assertTrue(files.single().content.contains("const val ENABLED_STATUS: Int = 1"))
    }
}
