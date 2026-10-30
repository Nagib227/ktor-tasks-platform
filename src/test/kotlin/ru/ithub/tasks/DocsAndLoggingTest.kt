package ru.ithub.tasks

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocsAndLoggingTest {
    @Test
    fun `swagger ui and openapi spec list every route`() = testApplication {
        installApp()

        val ui = client.get("/swagger")
        assertEquals(HttpStatusCode.OK, ui.status)
        assertTrue(ui.headers[HttpHeaders.ContentType].orEmpty().contains("text/html"))
        assertTrue(ui.bodyAsText().contains("Swagger UI"))

        val spec = client.get("/swagger/documentation.yaml")
        assertEquals(HttpStatusCode.OK, spec.status)
        val yaml = spec.bodyAsText()
        assertTrue(yaml.contains("openapi: 3.0.3"))
        listOf(
            "/:",
            "/tasks:",
            "/tasks/{id}:",
            "/register:",
            "/login:",
            "/users:",
            "/notifications:",
            "bearerAuth:",
            "Forbidden:",
            "CreateTaskRequest:",
            "UpdateTaskRequest:",
        ).forEach { marker ->
            assertTrue(yaml.contains(marker), "В спецификации нет $marker")
        }

        val docs = client.get("/openapi")
        assertEquals(HttpStatusCode.OK, docs.status)
        assertTrue(docs.headers[HttpHeaders.ContentType].orEmpty().contains("text/html"))
    }

    @Test
    fun `call logging writes method path and status`() = testApplication {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        root.addAppender(appender)
        try {
            installApp()
            appender.list.clear()

            assertEquals(HttpStatusCode.OK, client.get("/tasks").status)
            val denied = client.post("/tasks") {
                contentType(ContentType.Application.Json)
                setBody("""{"title":"Без токена"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, denied.status)

            val messages = appender.list.map { it.message }
            assertTrue(messages.any { it.contains("GET /tasks -> 200") }, messages.joinToString())
            assertTrue(messages.any { it.contains("POST /tasks -> 401") }, messages.joinToString())
        } finally {
            root.detachAppender(appender)
        }
    }
}
