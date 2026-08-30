package site.addzero.studio.web

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.utils.io.readLine
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.koin.core.annotation.Single
import site.addzero.studio.contract.AgentEvent
import site.addzero.studio.contract.StudioApiFailure
import site.addzero.studio.workbench.transport.AgentEventAccumulator
import site.addzero.studio.workbench.transport.AgentStreamPort
import site.addzero.studio.workbench.transport.StudioSessionState

@Single(binds = [AgentStreamPort::class])
class AgentStreamAdapter(
    private val client: HttpClient,
    private val json: Json,
    private val session: StudioSessionState,
) : AgentStreamPort {
    private var activeJob: Job? = null

    override fun create(input: JsonObject): Flow<AgentEvent> = flow {
        activeJob = currentCoroutineContext().job
        client.preparePost("/v1/responses") {
            accept(ContentType.Text.EventStream)
            session.accessToken.takeIf(String::isNotBlank)?.let(::bearerAuth)
            setBody(input)
        }.execute { response ->
            if (response.status.value !in 200..299) {
                throw StudioApiFailure(response.status.value, null, response.bodyAsText())
            }
            val accumulator = AgentEventAccumulator()
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readLine() ?: break
                accumulator.accept(line)?.let { event -> emit(event) }
            }
            accumulator.flush()?.let { event -> emit(event) }
        }
        activeJob = null
    }

    override suspend fun cancel(responseId: String): JsonObject {
        val response = client.post("/v1/responses/$responseId/cancel") {
            session.accessToken.takeIf(String::isNotBlank)?.let(::bearerAuth)
        }
        if (response.status.value !in 200..299) {
            throw StudioApiFailure(response.status.value, null, response.bodyAsText())
        }
        return json.parseToJsonElement(response.bodyAsText()) as JsonObject
    }

    override fun cancelActive() {
        activeJob?.cancel()
        activeJob = null
    }
}
