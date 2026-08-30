package site.addzero.studio.workbench.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import site.addzero.studio.contract.AgentEvent

interface AgentStreamPort {
    fun create(input: JsonObject): Flow<AgentEvent>

    suspend fun cancel(responseId: String): JsonObject

    fun cancelActive()
}

class AgentEventAccumulator {
    private var event = "message"
    private var id: String? = null
    private val data = mutableListOf<String>()

    fun accept(line: String): AgentEvent? {
        if (line.isBlank()) {
            return flush()
        }
        when {
            line.startsWith("event:") -> event = line.substringAfter(':').trim()
            line.startsWith("id:") -> id = line.substringAfter(':').trim()
            line.startsWith("data:") -> data += line.substringAfter(':').trimStart()
        }
        return null
    }

    fun flush(): AgentEvent? {
        if (data.isEmpty()) {
            return null
        }
        val value = data.joinToString("\n")
        data.clear()
        if (value == "[DONE]") {
            return null
        }
        return AgentEvent(event = event, data = value, id = id).also {
            event = "message"
            id = null
        }
    }
}
