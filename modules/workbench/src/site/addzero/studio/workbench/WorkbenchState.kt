package site.addzero.studio.workbench

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.core.annotation.Single
import site.addzero.studio.workbench.browser.BrowserPort
import site.addzero.studio.contract.StudioWorkspace
import site.addzero.studio.workbench.transport.StudioApi
import site.addzero.studio.workbench.transport.StudioSessionState

enum class StudioLanguage { ZH_CN, EN }

@Single
class WorkbenchState(
    private val api: StudioApi,
    private val session: StudioSessionState,
    private val browser: BrowserPort,
) {
    var workspace by mutableStateOf(StudioWorkspace.LIBRARY)
        private set

    var accessToken by mutableStateOf("")
        private set

    var darkTheme by mutableStateOf(false)
        private set

    var language by mutableStateOf(StudioLanguage.ZH_CN)
        private set

    var availableWorkspaces by mutableStateOf<List<StudioWorkspace>>(emptyList())
        private set

    var displayName by mutableStateOf("Code Studio")
        private set

    var loading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    suspend fun initialize() {
        loading = true
        error = null
        try {
            accessToken = browser.read(TOKEN_KEY).orEmpty()
            session.updateAccessToken(accessToken)
            darkTheme = browser.read(THEME_KEY) == "dark"
            language = if (browser.read(LANGUAGE_KEY) == "en") StudioLanguage.EN else StudioLanguage.ZH_CN
            val config = api.config()
            session.updateConfig(config)
            displayName = config.displayName.ifBlank { "Code Studio" }
            val supported = buildList {
                if ("metadata" in config.capabilities) add(StudioWorkspace.LIBRARY)
                if ("agent" in config.capabilities) add(StudioWorkspace.AGENT)
                if ("api" in config.capabilities) add(StudioWorkspace.API)
            }
            val documentationOnly = browser.query.substringAfter('?').split('&').any { it == "mode=api-docs" }
            availableWorkspaces = if (documentationOnly) supported.filter { it == StudioWorkspace.API } else supported
            workspace = if (documentationOnly) StudioWorkspace.API else workspace.takeIf(availableWorkspaces::contains)
                ?: availableWorkspaces.firstOrNull()
                ?: StudioWorkspace.LIBRARY
            if (availableWorkspaces.isEmpty()) {
                error = "宿主未启用 Studio 能力"
            }
        } catch (cause: Throwable) {
            error = cause.message ?: "读取 Studio 配置失败"
        } finally {
            loading = false
        }
    }

    fun select(workspace: StudioWorkspace) {
        this.workspace = workspace
    }

    fun updateAccessToken(value: String) {
        accessToken = value.trim()
        session.updateAccessToken(accessToken)
        if (accessToken.isEmpty()) browser.remove(TOKEN_KEY) else browser.write(TOKEN_KEY, accessToken)
    }

    fun toggleTheme() {
        darkTheme = !darkTheme
        browser.write(THEME_KEY, if (darkTheme) "dark" else "light")
    }

    fun toggleLanguage() {
        language = if (language == StudioLanguage.ZH_CN) StudioLanguage.EN else StudioLanguage.ZH_CN
        browser.write(LANGUAGE_KEY, if (language == StudioLanguage.EN) "en" else "zh-CN")
    }
}

private const val TOKEN_KEY = "studio.access-token"
private const val THEME_KEY = "studio.theme"
private const val LANGUAGE_KEY = "studio.language"
