package site.addzero.platform.lowcode.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LowcodeMetadataDatabaseConfigTest {
    @Test
    fun `resolves studio database configuration from environment`() {
        val config = LowcodeMetadataDatabaseConfig.fromEnvironment(
            mapOf(
                "CODE_STUDIO_DB_JDBC_URL" to " jdbc:postgresql://localhost:5432/studio ",
                "CODE_STUDIO_DB_USERNAME" to " studio_user ",
                "CODE_STUDIO_DB_PASSWORD" to " studio_password ",
                "CODE_STUDIO_SCHEMA" to " example_dev ",
            ),
        )

        assertEquals("jdbc:postgresql://localhost:5432/studio", config.jdbcUrl)
        assertEquals("studio_user", config.username)
        assertEquals("studio_password", config.password)
        assertEquals("example_dev", config.schema)
    }

    @Test
    fun `reports the missing studio database variable`() {
        val error = assertThrows(IllegalStateException::class.java) {
            LowcodeMetadataDatabaseConfig.fromEnvironment(emptyMap())
        }

        assertEquals("低代码源码生成需要环境变量 CODE_STUDIO_DB_JDBC_URL", error.message)
    }

    @Test
    fun `rejects unsafe studio database schema`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            LowcodeMetadataDatabaseConfig(
                jdbcUrl = "jdbc:postgresql://localhost:5432/studio",
                username = "studio_user",
                password = "studio_password",
                schema = "invalid-schema",
            )
        }

        assertEquals("低代码元数据数据库 Schema 不合法: invalid-schema", error.message)
    }
}
