package ru.ithub.tasks.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import ru.ithub.tasks.model.User
import java.time.Instant
import java.util.Date

data class JwtSettings(
    val secret: String = "ktor-tasks-jwt-dev-secret-key-32bytes",
    val issuer: String = "ktor-tasks",
    val audience: String = "ktor-tasks-api",
    val realm: String = "tasks",
    val expirationMs: Long = 60 * 60 * 1000,
) {
    init {
        require(secret.toByteArray(Charsets.UTF_8).size >= 32) {
            "Секрет JWT должен быть не короче 32 байт"
        }
    }
}

class JwtService(val settings: JwtSettings = JwtSettings()) {
    private val algorithm = Algorithm.HMAC256(settings.secret)
    val expirationSeconds: Long = settings.expirationMs / 1000

    fun issue(user: User): String =
        JWT.create()
            .withIssuer(settings.issuer)
            .withAudience(settings.audience)
            .withClaim("userId", user.id)
            .withClaim("login", user.login)
            .withClaim("role", user.role.name)
            .withExpiresAt(Date.from(Instant.now().plusMillis(settings.expirationMs)))
            .sign(algorithm)

    fun userIdFrom(token: String?): Int? {
        if (token.isNullOrBlank()) {
            return null
        }
        return runCatching { verifier().verify(token).getClaim("userId").asInt() }.getOrNull()
    }

    fun verifier(): JWTVerifier =
        JWT.require(algorithm)
            .withIssuer(settings.issuer)
            .withAudience(settings.audience)
            .build()
}
