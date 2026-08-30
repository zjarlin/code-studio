package site.addzero.studio.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import org.koin.plugin.module.dsl.startKoin
import site.addzero.studio.workbench.WorkbenchApp
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalComposeUiApi::class, ExperimentalWasmJsInterop::class)
fun main() {
    startKoin<StudioWebApplication>()
    removeElement(".loading")
    ComposeViewport {
        WorkbenchApp()
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(selector) => document.querySelector(selector)?.remove()")
private external fun removeElement(selector: String)
