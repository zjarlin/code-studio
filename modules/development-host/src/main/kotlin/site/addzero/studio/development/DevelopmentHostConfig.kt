package site.addzero.studio.development

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.yaml.YamlConfig
import site.addzero.studio.runtime.GenerationTargetProfile
import site.addzero.studio.runtime.GenerationTargetProfiles
import java.nio.file.Files
import java.nio.file.Path

private const val DEFAULT_PORT: Int = 8080
private const val DEFAULT_MAXIMUM_POOL_SIZE: Int = 4
private val TARGET_PROFILE_PATH: Path = Path.of(".code-studio/target-profile.json")

internal data class DevelopmentHostDatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int,
) {
    init {
        require(jdbcUrl.startsWith("jdbc:postgresql:")) {
            "库开发宿主只支持 PostgreSQL JDBC URL"
        }
        require(username.isNotBlank()) {
            "数据库 username 不能为空"
        }
        require(password.isNotBlank()) {
            "数据库 password 不能为空"
        }
        require(maximumPoolSize > 0) {
            "数据库 maximumPoolSize 必须大于 0"
        }
    }
}

internal data class DevelopmentHostConfig(
    val database: DevelopmentHostDatabaseConfig,
    val port: Int,
    val targetProfile: GenerationTargetProfile,
) {
    init {
        require(port in 1..65535) {
            "Studio 端口必须位于 1..65535"
        }
    }
}

internal object DevelopmentHostConfigLoader {
    fun load(
        workspace: Path,
        environment: Map<String, String> = System.getenv(),
        targetProfile: GenerationTargetProfile = loadTargetProfile(workspace),
    ): DevelopmentHostConfig {
        val localFile = workspace.resolve(".code-studio/local.yaml")
        val localConfig = if (Files.exists(localFile)) {
            requireNotNull(YamlConfig(localFile.toString())) {
                "Studio 本地配置无法读取: $localFile"
            }
        } else {
            null
        }
        val jdbcUrl = requiredValue(environment, "CODE_STUDIO_DB_JDBC_URL", localConfig, "database.url")
        val username = requiredValue(environment, "CODE_STUDIO_DB_USERNAME", localConfig, "database.username")
        val password = requiredValue(environment, "CODE_STUDIO_DB_PASSWORD", localConfig, "database.password")
        val maximumPoolSize = optionalValue(
            environment,
            "CODE_STUDIO_DB_MAXIMUM_POOL_SIZE",
            localConfig,
            "database.maximumPoolSize",
        )?.toInt() ?: DEFAULT_MAXIMUM_POOL_SIZE
        val port = optionalValue(environment, "CODE_STUDIO_PORT", localConfig, "server.port")
            ?.toInt()
            ?: DEFAULT_PORT
        val database = DevelopmentHostDatabaseConfig(jdbcUrl, username, password, maximumPoolSize)
        return DevelopmentHostConfig(database, port, targetProfile)
    }

    fun loadTargetProfile(workspace: Path): GenerationTargetProfile {
        val profileFile = workspace.resolve(TARGET_PROFILE_PATH)
        require(Files.isRegularFile(profileFile)) {
            "库开发宿主缺少 GenerationTargetProfile: $profileFile"
        }
        return GenerationTargetProfiles.read(profileFile.toUri().toURL())
    }

    private fun requiredValue(
        environment: Map<String, String>,
        environmentName: String,
        localConfig: ApplicationConfig?,
        configPath: String,
    ): String = requireNotNull(optionalValue(environment, environmentName, localConfig, configPath)) {
        "缺少 Studio 本地配置: $environmentName 或 $configPath"
    }

    private fun optionalValue(
        environment: Map<String, String>,
        environmentName: String,
        localConfig: ApplicationConfig?,
        configPath: String,
    ): String? = environment[environmentName]
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: localConfig?.propertyOrNull(configPath)?.getString()?.trim()?.takeIf(String::isNotEmpty)
}
