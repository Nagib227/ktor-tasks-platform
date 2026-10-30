package ru.ithub.tasks

import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import ru.ithub.tasks.model.Task
import ru.ithub.tasks.model.TaskEvent
import ru.ithub.tasks.model.TokenResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `websocket broadcasts created and deleted tasks`() = testApplication {
        installApp()
        val user = login("student", "secret12", register = true)
        val admin = login("admin", "admin123", register = false)
        val sockets = createClient {
            install(WebSockets)
        }

        sockets.webSocket("/notifications?token=${user.token}") {
            val hello = incoming.receive() as Frame.Text
            assertTrue(hello.readText().contains("connected"))

            val created = client.post("/tasks") {
                header(HttpHeaders.Authorization, "Bearer ${user.token}")
                contentType(ContentType.Application.Json)
                setBody("""{"title":"Событие в эфире","priority":"HIGH"}""")
            }
            assertEquals(HttpStatusCode.Created, created.status)
            val task = json.decodeFromString<Task>(created.bodyAsText())

            val createdEvent = json.decodeFromString<TaskEvent>((incoming.receive() as Frame.Text).readText())
            assertEquals("task.created", createdEvent.type)
            assertEquals(task, createdEvent.task)

            val deleted = client.delete("/tasks/${task.id}") {
                header(HttpHeaders.Authorization, "Bearer ${admin.token}")
            }
            assertEquals(HttpStatusCode.NoContent, deleted.status)
            val deletedEvent = json.decodeFromString<TaskEvent>((incoming.receive() as Frame.Text).readText())
            assertEquals("task.deleted", deletedEvent.type)
            assertEquals(task.id, deletedEvent.task.id)
        }
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.login(
        login: String,
        password: String,
        register: Boolean,
    ): TokenResponse {
        if (register) {
            val registered = client.post("/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"login":"$login","password":"$password"}""")
            }
            assertEquals(HttpStatusCode.Created, registered.status)
        }
        val loggedIn = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"$login","password":"$password"}""")
        }
        assertEquals(HttpStatusCode.OK, loggedIn.status)
        return json.decodeFromString(loggedIn.bodyAsText())
    }
}
