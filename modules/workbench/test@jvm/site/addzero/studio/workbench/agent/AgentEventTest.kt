package site.addzero.studio.workbench.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import site.addzero.studio.contract.AgentEvent
import site.addzero.studio.workbench.transport.AgentEventAccumulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentEventTest {
    @Test
    fun `Agent 消息中的元数据 Patch 保留修订和待确认问题`() {
        val part = Json.parseToJsonElement(
            """{"type":"data-metadata-patch","data":{"tableId":"models","revision":"r1","patches":[{"rowKey":"1"}],"questions":["待确认"]}}""",
        ).jsonObject

        val command = decodeMetadataPatchPart(part, Json)

        assertEquals("models:r1", command?.key)
        assertEquals(1, command?.patches?.size)
        assertEquals(listOf("待确认"), command?.questions)
    }

    @Test
    fun `SSE 分帧合并多行 data`() {
        val parser = AgentEventAccumulator()

        assertNull(parser.accept("event: response.output_text.delta"))
        assertNull(parser.accept("id: event-1"))
        assertNull(parser.accept("data: {\"delta\":\"hello\"}"))
        val event = parser.accept("")

        assertEquals("response.output_text.delta", event?.event)
        assertEquals("event-1", event?.id)
        assertEquals("{\"delta\":\"hello\"}", event?.data)
    }

    @Test
    fun `Agent 文本增量和 response id 按事件归并`() {
        val json = Json
        val first = reduceAgentEvent("", null, AgentEvent("message", """{"response_id":"resp-1","delta":"你好"}"""), json)
        val second = reduceAgentEvent(first.text, first.responseId, AgentEvent("message", """{"delta":" Studio"}"""), json)

        assertEquals("你好 Studio", second.text)
        assertEquals("resp-1", second.responseId)
    }
}
