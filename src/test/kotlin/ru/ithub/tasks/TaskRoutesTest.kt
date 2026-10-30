package ru.ithub.tasks

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import ru.ithub.tasks.model.ErrorResponse
import ru.ithub.tasks.model.Priority
import ru.ithub.tasks.model.Role
import ru.ithub.tasks.model.Task
import ru.ithub.tasks.model.TokenResponse
import ru.ithub.tasks.model.UserResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `read routes stay public`() = testApplication {
        installApp()
        val list = client.get("/tasks")
        assertEquals(HttpStatusCode.OK, list.status)
        assertTrue(list.headers[HttpHeaders.ContentType].orEmpty().contains("application/json"))
        assertEquals(listOf(1, 2, 3), json.decodeFromString<List<Task>>(list.bodyAsText()).map { it.id })

        val one = client.get("/tasks/2")
        assertEquals(HttpStatusCode.OK, one.status)
        assertEquals(true, json.decodeFromString<Task>(one.bodyAsText()).done)

        val missing = client.get("/tasks/99")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals("not_found", json.decodeFromString<ErrorResponse>(missing.bodyAsText()).error)

        val invalid = client.get("/tasks/abc")
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
    }

    @Test
    fun `writes without token or with a broken token return 401`() = testApplication {
        installApp()
        assertEquals(HttpStatusCode.Unauthorized, client.post("/tasks") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Без токена"}""")
        }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.put("/tasks/1") {
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Без токена","done":true,"priority":"LOW"}""")
        }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.delete("/tasks/1").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/users").status)

        val broken = client.post("/tasks") {
            header(HttpHeaders.Authorization, "Bearer not-a-jwt")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Битый токен"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, broken.status)
        assertEquals("unauthorized", json.decodeFromString<ErrorResponse>(broken.bodyAsText()).error)
        assertTrue(broken.headers[HttpHeaders.ContentType].orEmpty().contains("application/json"))

        assertEquals(HttpStatusCode.OK, client.get("/tasks/1").status)
    }

    @Test
    fun `user creates and updates own task but cannot delete or list users`() = testApplication {
        installApp()
        val user = accessToken("student")

        val created = client.post("/tasks") {
            bearer(user.token)
            contentType(ContentType.Application.Json)
            setBody("""{"id":999,"title":"  Проверить API  ","priority":"LOW"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals("/tasks/4", created.headers[HttpHeaders.Location])
        val task = json.decodeFromString<Task>(created.bodyAsText())
        assertEquals(4, task.id)
        assertEquals(user.userId, task.ownerId)
        assertEquals("Проверить API", task.title)
        assertEquals(false, task.done)
        assertEquals(Priority.LOW, task.priority)

        val foreign = client.put("/tasks/1") {
            bearer(user.token)
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Чужая","done":true,"priority":"LOW"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, foreign.status)
        assertEquals("forbidden", json.decodeFromString<ErrorResponse>(foreign.bodyAsText()).error)

        val updated = client.put("/tasks/4") {
            bearer(user.token)
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Готово","done":true,"priority":"HIGH"}""")
        }
        assertEquals(HttpStatusCode.OK, updated.status)
        val afterUpdate = json.decodeFromString<Task>(updated.bodyAsText())
        assertEquals("Готово", afterUpdate.title)
        assertEquals(true, afterUpdate.done)
        assertEquals(Priority.HIGH, afterUpdate.priority)
        assertEquals(user.userId, afterUpdate.ownerId)
        assertEquals(afterUpdate, json.decodeFromString<Task>(client.get("/tasks/4").bodyAsText()))

        val deniedDelete = client.delete("/tasks/4") { bearer(user.token) }
        assertEquals(HttpStatusCode.Forbidden, deniedDelete.status)
        assertEquals(HttpStatusCode.Forbidden, client.delete("/tasks/99") { bearer(user.token) }.status)
        assertEquals(HttpStatusCode.OK, client.get("/tasks/4").status)

        val users = client.get("/users") { bearer(user.token) }
        assertEquals(HttpStatusCode.Forbidden, users.status)

        val duplicate = client.post("/tasks") {
            bearer(user.token)
            contentType(ContentType.Application.Json)
            setBody("""{"title":"изучить маршруты ktor"}""")
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)

        val blank = client.post("/tasks") {
            bearer(user.token)
            contentType(ContentType.Application.Json)
            setBody("""{"title":"   "}""")
        }
        assertEquals(HttpStatusCode.BadRequest, blank.status)
    }

    @Test
    fun `admin updates any task deletes it and lists users without hashes`() = testApplication {
        installApp()
        val user = accessToken("student")
        val admin = accessToken("admin", "admin123", register = false)
        assertEquals(Role.ADMIN, admin.role)

        val created = client.post("/tasks") {
            bearer(user.token)
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Задача студента","priority":"MEDIUM"}""")
        }
        val task = json.decodeFromString<Task>(created.bodyAsText())

        val updated = client.put("/tasks/${task.id}") {
            bearer(admin.token)
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Проверено","done":true,"priority":"HIGH"}""")
        }
        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals(user.userId, json.decodeFromString<Task>(updated.bodyAsText()).ownerId)

        val missingUpdate = client.put("/tasks/99") {
            bearer(admin.token)
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Нет такой","done":false,"priority":"LOW"}""")
        }
        assertEquals(HttpStatusCode.NotFound, missingUpdate.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/tasks/99") { bearer(admin.token) }.status)

        val deleted = client.delete("/tasks/${task.id}") { bearer(admin.token) }
        assertEquals(HttpStatusCode.NoContent, deleted.status)
        assertEquals("", deleted.bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.get("/tasks/${task.id}").status)

        val users = client.get("/users") { bearer(admin.token) }
        assertEquals(HttpStatusCode.OK, users.status)
        val body = users.bodyAsText()
        assertFalse(body.contains("passwordHash"))
        assertFalse(body.contains("secret12"))
        val listed = json.decodeFromString<List<UserResponse>>(body)
        assertTrue(listed.any { it.login == "admin" && it.role == Role.ADMIN })
        assertTrue(listed.any { it.login == "student" && it.role == Role.USER })
    }

    private suspend fun ApplicationTestBuilder.accessToken(
        login: String,
        password: String = "secret12",
        register: Boolean = true,
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

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }
}
