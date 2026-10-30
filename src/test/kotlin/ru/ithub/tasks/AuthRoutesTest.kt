package ru.ithub.tasks

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import ru.ithub.tasks.model.ErrorResponse
import ru.ithub.tasks.model.Role
import ru.ithub.tasks.model.TokenResponse
import ru.ithub.tasks.model.UserResponse
import ru.ithub.tasks.security.JwtSettings
import ru.ithub.tasks.security.PasswordHasher
import ru.ithub.tasks.storage.UserRepository
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val settings = JwtSettings(expirationMs = 60_000)

    @Test
    fun `register stores bcrypt hash and ignores a client role`() = testApplication {
        val database = installApp(jwtSettings = settings)
        val response = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"Student","password":"secret12","role":"ADMIN"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertFalse(body.contains("secret12"))
        assertFalse(body.contains("passwordHash"))
        val created = json.decodeFromString<UserResponse>(body)
        assertEquals("student", created.login)
        assertEquals(Role.USER, created.role)

        val stored = UserRepository(database).findByLogin("student")!!
        assertEquals(created.id, stored.id)
        assertEquals(Role.USER, stored.role)
        assertNotEquals("secret12", stored.passwordHash)
        assertTrue(stored.passwordHash.startsWith("$2"))
        assertTrue(PasswordHasher(cost = 4).verify("secret12", stored.passwordHash))
    }

    @Test
    fun `duplicate login returns 409 and short password returns 400`() = testApplication {
        installApp(jwtSettings = settings)
        val first = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"student","password":"secret12"}""")
        }
        assertEquals(HttpStatusCode.Created, first.status)

        val duplicate = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"STUDENT","password":"another1"}""")
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals("conflict", json.decodeFromString<ErrorResponse>(duplicate.bodyAsText()).error)

        val short = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"other","password":"123"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, short.status)
    }

    @Test
    fun `login returns jwt with userId and role and rejects a wrong password`() = testApplication {
        installApp(jwtSettings = settings)
        val registered = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"student","password":"secret12"}""")
        }
        val userId = json.decodeFromString<UserResponse>(registered.bodyAsText()).id
        val response = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"Student","password":"secret12"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val token = json.decodeFromString<TokenResponse>(response.bodyAsText())
        assertEquals("Bearer", token.tokenType)
        assertEquals(userId, token.userId)
        assertEquals(Role.USER, token.role)
        assertEquals(60, token.expiresIn)

        val decoded = JWT.decode(token.token)
        assertEquals(userId, decoded.getClaim("userId").asInt())
        assertEquals("student", decoded.getClaim("login").asString())
        assertEquals("USER", decoded.getClaim("role").asString())
        assertTrue(decoded.expiresAt.after(Date.from(Instant.now())))

        val admin = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"admin","password":"admin123"}""")
        }
        assertEquals(Role.ADMIN, json.decodeFromString<TokenResponse>(admin.bodyAsText()).role)

        val wrong = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"student","password":"secret12-wrong"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertFalse(wrong.bodyAsText().contains("token"))

        val missing = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"login":"nobody","password":"secret12"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, missing.status)
    }

    @Test
    fun `token without userId or for unknown user is rejected`() = testApplication {
        installApp(jwtSettings = settings)
        val withoutClaim = JWT.create()
            .withIssuer(settings.issuer)
            .withAudience(settings.audience)
            .withExpiresAt(Date.from(Instant.now().plusSeconds(60)))
            .sign(Algorithm.HMAC256(settings.secret))
        val noClaim = client.post("/tasks") {
            header(HttpHeaders.Authorization, "Bearer $withoutClaim")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Без claim"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, noClaim.status)

        val unknownUser = JWT.create()
            .withIssuer(settings.issuer)
            .withAudience(settings.audience)
            .withClaim("userId", 999)
            .withExpiresAt(Date.from(Instant.now().plusSeconds(60)))
            .sign(Algorithm.HMAC256(settings.secret))
        val unknown = client.post("/tasks") {
            header(HttpHeaders.Authorization, "Bearer $unknownUser")
            contentType(ContentType.Application.Json)
            setBody("""{"title":"Чужой пользователь"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, unknown.status)

        val expired = JWT.create()
            .withIssuer(settings.issuer)
            .withAudience(settings.audience)
            .withClaim("userId", 1)
            .withExpiresAt(Date.from(Instant.now().minusSeconds(60)))
            .sign(Algorithm.HMAC256(settings.secret))
        val expiredResponse = client.delete("/tasks/1") {
            header(HttpHeaders.Authorization, "Bearer $expired")
        }
        assertEquals(HttpStatusCode.Unauthorized, expiredResponse.status)

        val stillThere = client.get("/tasks/1")
        assertEquals(HttpStatusCode.OK, stillThere.status)
    }
}
