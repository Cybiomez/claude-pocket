package dev.claudepocket.net

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

data class SseEvent(val seq: Long, val type: String, val session: String, val data: JsonObject)

sealed interface SseState {
    data class Event(val ev: SseEvent) : SseState
    object Open : SseState
    data class Closed(val error: String?) : SseState
}

// Одно SSE-соединение на приложение; события всех сессий, клиент фильтрует.
fun sseFlow(api: ApiClient, afterSeq: () -> Long): Flow<SseState> = callbackFlow {
    val req = api.authedRequest(api.streamUrl(afterSeq())).build()
    val source = EventSources.createFactory(api.sseHttp).newEventSource(req, object : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            trySend(SseState.Open)
        }
        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            try {
                val o = json.parseToJsonElement(data).jsonObject
                trySend(
                    SseState.Event(
                        SseEvent(
                            seq = o["seq"]?.jsonPrimitive?.longOrNull ?: 0L,
                            type = type ?: o["type"]?.jsonPrimitive?.content ?: "",
                            session = o["session"]?.jsonPrimitive?.content ?: "",
                            data = o,
                        )
                    )
                )
            } catch (_: Exception) { /* мусорная строка — пропускаем */ }
        }
        override fun onClosed(eventSource: EventSource) { trySend(SseState.Closed(null)); close() }
        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            trySend(SseState.Closed(t?.message ?: "HTTP ${response?.code}")); close()
        }
    })
    awaitClose { source.cancel() }
}
