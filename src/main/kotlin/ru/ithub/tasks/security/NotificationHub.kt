package ru.ithub.tasks.security

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import ru.ithub.tasks.model.TaskEvent
import java.util.concurrent.ConcurrentHashMap

class NotificationHub {
    private val sessions = ConcurrentHashMap.newKeySet<WebSocketSession>()
    private val json = Json { encodeDefaults = true }

    fun join(session: WebSocketSession) {
        sessions.add(session)
    }

    fun leave(session: WebSocketSession) {
        sessions.remove(session)
    }

    suspend fun broadcast(event: TaskEvent) {
        val text = json.encodeToString(event)
        val closed = mutableListOf<WebSocketSession>()
        for (session in sessions) {
            try {
                session.send(Frame.Text(text))
            } catch (_: Exception) {
                closed.add(session)
            }
        }
        sessions.removeAll(closed.toSet())
    }
}
