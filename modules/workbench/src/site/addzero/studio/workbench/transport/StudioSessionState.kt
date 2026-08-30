package site.addzero.studio.workbench.transport

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.core.annotation.Single
import site.addzero.studio.contract.StudioClientConfig

@Single
class StudioSessionState {
    var accessToken by mutableStateOf("")
        private set

    var config by mutableStateOf<StudioClientConfig?>(null)
        private set

    fun updateAccessToken(value: String) {
        accessToken = value.trim()
    }

    fun updateConfig(value: StudioClientConfig) {
        config = value
    }
}
