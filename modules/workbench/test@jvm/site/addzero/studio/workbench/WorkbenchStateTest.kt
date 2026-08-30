package site.addzero.studio.workbench

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.serialization.json.Json
import site.addzero.studio.workbench.library.LibraryResourceKind
import site.addzero.studio.workbench.library.LibraryWorkspaceState
import site.addzero.studio.workbench.library.ResourceSelection
import site.addzero.studio.workbench.transport.StudioApi
import site.addzero.studio.workbench.transport.StudioSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkbenchStateTest {
    @Test
    fun `草稿阻止选择切换并可显式放弃`() {
        val session = StudioSessionState()
        val api = StudioApi(HttpClient(MockEngine { error("测试不应发起请求") }), Json { ignoreUnknownKeys = true }, session)
        val state = LibraryWorkspaceState(api, session)
        state.newResource(LibraryResourceKind.LIBRARY)

        val switched = state.requestSelection(ResourceSelection(LibraryResourceKind.MODEL, 9))

        assertFalse(switched)
        assertTrue(state.dirty)
        assertNotNull(state.pendingSelection)
        state.discardAndSelect()
        assertFalse(state.dirty)
        assertEquals(ResourceSelection(LibraryResourceKind.MODEL, 9), state.selection)
        api.close()
    }
}
