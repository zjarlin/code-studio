package site.addzero.studio.devhost

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import site.addzero.studio.runtime.METADATA_CONTRIBUTOR_RESOURCE
import site.addzero.studio.runtime.MetadataContributors
import java.nio.file.Files
import java.nio.file.Path

class DevHostConfigTest {
    @Test
    fun `读取 workspace 本地数据库和端口配置`(@TempDir workspace: Path) {
        writeTargetProfile(workspace)
        val configFile = workspace.resolve(".code-studio/local.yaml")
        Files.createDirectories(configFile.parent)
        Files.writeString(
            configFile,
            """
            database:
              url: jdbc:postgresql://localhost:5432/studio
              username: studio_user
              password: studio_password
              maximumPoolSize: 3
            server:
              port: 8181
            """.trimIndent(),
        )

        val config = DevHostConfigLoader.load(workspace, emptyMap())

        assertEquals("jdbc:postgresql://localhost:5432/studio", config.database.jdbcUrl)
        assertEquals("studio_user", config.database.username)
        assertEquals(3, config.database.maximumPoolSize)
        assertEquals(8181, config.port)
        assertEquals("application", config.targetProfile.id)
    }

    @Test
    fun `环境数据源覆盖本地配置`(@TempDir workspace: Path) {
        writeTargetProfile(workspace)
        val environment = mapOf(
            "CODE_STUDIO_DB_JDBC_URL" to "jdbc:postgresql://localhost:5432/environment",
            "CODE_STUDIO_DB_USERNAME" to "environment_user",
            "CODE_STUDIO_DB_PASSWORD" to "environment_password",
            "CODE_STUDIO_PORT" to "8282",
        )

        val config = DevHostConfigLoader.load(workspace, environment)

        assertEquals("jdbc:postgresql://localhost:5432/environment", config.database.jdbcUrl)
        assertEquals("environment_user", config.database.username)
        assertEquals(8282, config.port)
    }

    @Test
    fun `严格读取 workspace 生成目标配置`(@TempDir workspace: Path) {
        writeTargetProfile(workspace)

        val profile = DevHostConfigLoader.loadTargetProfile(workspace)

        assertEquals("application", profile.id)
        assertEquals(
            listOf(
                "extension.agent-package",
                "runtime.audit-principal",
                "runtime.core-package",
                "runtime.dictionary-annotation",
                "runtime.lowcode-package",
                "runtime.persistence-model-package",
                "runtime.web-package",
            ),
            profile.symbols.keys.toList(),
        )
        assertEquals(setOf("agent"), profile.capabilities)
    }

    @Test
    fun `拒绝缺失的 workspace 生成目标配置`(@TempDir workspace: Path) {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DevHostConfigLoader.loadTargetProfile(workspace)
        }

        assertTrue(error.message.orEmpty().contains("target-profile.json"))
    }

    @Test
    fun `拒绝生成目标配置未知字段`(@TempDir workspace: Path) {
        writeTargetProfile(
            workspace,
            DEFAULT_TARGET_PROFILE.replace("\n}", ",\n  \"unexpected\": true\n}"),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            DevHostConfigLoader.loadTargetProfile(workspace)
        }

        assertTrue(error.cause?.message.orEmpty().contains("未知字段"))
    }

    @Test
    fun `schema 由 contributor id 稳定净化且不允许环境改写`() {
        val schema = devSchema("System.User")

        assertEquals("code_studio_dev_system_user", schema)
        assertThrows(IllegalArgumentException::class.java) {
            validateRequestedSchema(schema, mapOf("CODE_STUDIO_SCHEMA" to "another_schema"))
        }
    }

    @Test
    fun `库开发宿主从所选模块读取贡献清单`(@TempDir module: Path) {
        val manifest = module.resolve("src/main/resources").resolve(METADATA_CONTRIBUTOR_RESOURCE)
        Files.createDirectories(manifest.parent)
        Files.writeString(
            manifest,
            """
            {
              "formatVersion": 1,
              "id": "example-library",
              "migrationLocation": "classpath:db/studio/metadata/example-library",
              "requires": []
            }
            """.trimIndent(),
        )

        DevHostModule.load(module, module).use { loaded ->
            assertEquals("example-library", loaded.contributor.id)
        }
    }

    @Test
    fun `库开发宿主只装载目标贡献的传递依赖`(@TempDir workspace: Path) {
        val dependency = createContributor(workspace, "dependency-library")
        val unrelated = createContributor(workspace, "unrelated-library")
        val selected = createContributor(
            workspace = workspace,
            id = "selected-library",
            requires = listOf("dependency-library"),
        )
        createLegacyMigration(dependency, "V1__dependency.sql")
        createLegacyMigration(selected, "V2__selected.sql")
        createLegacyMigration(unrelated, "V3__unrelated.sql")

        lateinit var temporaryClasspath: Path
        DevHostModule.load(workspace, selected).use { loaded ->
            temporaryClasspath = loaded.temporaryClasspath
            val visibleIds = MetadataContributors.load(loaded.classLoader).map { contributor -> contributor.id }

            assertEquals(listOf("dependency-library", "selected-library"), visibleIds)
            assertNotNull(
                loaded.classLoader.getResource(
                    "db/studio/metadata/dependency-library/V1__dependency.sql",
                ),
            )
            assertNotNull(
                loaded.classLoader.getResource(
                    "db/studio/metadata/selected-library/V2__selected.sql",
                ),
            )
            assertNull(
                loaded.classLoader.getResource(
                    "db/studio/metadata/unrelated-library/V3__unrelated.sql",
                ),
            )
        }
        assertFalse(Files.exists(temporaryClasspath))
    }

    @Test
    fun `库开发宿主忽略构建产物中的重复清单`(@TempDir workspace: Path) {
        val selected = createContributor(workspace, "selected-library")
        val builtManifest = workspace.resolve("build/generated/src/main/resources")
            .resolve(METADATA_CONTRIBUTOR_RESOURCE)
        Files.createDirectories(builtManifest.parent)
        Files.copy(
            selected.resolve("src/main/resources").resolve(METADATA_CONTRIBUTOR_RESOURCE),
            builtManifest,
        )

        DevHostModule.load(workspace, selected).use { loaded ->
            assertEquals("selected-library", loaded.contributor.id)
        }
    }

    @Test
    fun `库开发宿主优先打包模块自治迁移`(@TempDir workspace: Path) {
        val selected = createContributor(workspace, "selected-library")
        createLegacyMigration(selected, "V1__legacy.sql")
        createAutonomousMigration(selected, "V2__baseline.sql")

        DevHostModule.load(workspace, selected).use { loaded ->
            assertNotNull(
                loaded.classLoader.getResource(
                    "db/studio/metadata/selected-library/V2__baseline.sql",
                ),
            )
            assertNull(
                loaded.classLoader.getResource(
                    "db/studio/metadata/selected-library/V1__legacy.sql",
                ),
            )
        }
    }

    private fun createContributor(
        workspace: Path,
        id: String,
        requires: List<String> = emptyList(),
    ): Path {
        val module = workspace.resolve(id)
        val manifest = module.resolve("src/main/resources").resolve(METADATA_CONTRIBUTOR_RESOURCE)
        Files.createDirectories(manifest.parent)
        val dependencies = requires.joinToString(prefix = "[", postfix = "]") { requiredId ->
            "\"$requiredId\""
        }
        Files.writeString(
            manifest,
            """
            {
              "formatVersion": 1,
              "id": "$id",
              "migrationLocation": "classpath:db/studio/metadata/$id",
              "requires": $dependencies
            }
            """.trimIndent(),
        )
        return module
    }

    private fun createLegacyMigration(
        module: Path,
        fileName: String,
    ) {
        val migration = module.resolve("src/main/lowcode-metadata/db/migration").resolve(fileName)
        Files.createDirectories(migration.parent)
        Files.writeString(migration, "SELECT 1;")
    }

    private fun createAutonomousMigration(
        module: Path,
        fileName: String,
    ) {
        val migration = module.resolve("src/main/lowcode-metadata/db/studio/migration").resolve(fileName)
        Files.createDirectories(migration.parent)
        Files.writeString(migration, "SELECT 1;")
    }

    private fun writeTargetProfile(
        workspace: Path,
        content: String = DEFAULT_TARGET_PROFILE,
    ) {
        val profile = workspace.resolve(".code-studio/target-profile.json")
        Files.createDirectories(profile.parent)
        Files.writeString(profile, content)
    }

    private companion object {
        val DEFAULT_TARGET_PROFILE: String =
            """
            {
              "id": "application",
              "symbols": {
                "runtime.web-package": "example.runtime.web",
                "runtime.persistence-model-package": "example.persistence",
                "runtime.dictionary-annotation": "example.dictionary.Dict",
                "runtime.lowcode-package": "example.runtime.lowcode",
                "runtime.core-package": "example.runtime.core",
                "runtime.audit-principal": "example.identity.AuditPrincipal",
                "extension.agent-package": "example.runtime.agent"
              },
              "capabilities": ["agent"]
            }
            """.trimIndent()
    }
}
