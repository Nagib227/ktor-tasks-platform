package ru.ithub.tasks

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import ru.ithub.tasks.plugins.configureRequestLogging
import ru.ithub.tasks.plugins.configureStatusPages
import ru.ithub.tasks.routes.configureRouting
import ru.ithub.tasks.security.AuthService
import ru.ithub.tasks.security.JwtService
import ru.ithub.tasks.security.JwtSettings
import ru.ithub.tasks.security.NotificationHub
import ru.ithub.tasks.security.PasswordHasher
import ru.ithub.tasks.security.configureAuthentication
import ru.ithub.tasks.storage.DatabaseFactory
import ru.ithub.tasks.storage.TaskRepository
import ru.ithub.tasks.storage.UserRepository

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    val secret = System.getenv("JWT_SECRET") ?: "ktor-tasks-platform-dev-secret-32b"
    val database = DatabaseFactory.connectFromEnv()
    embeddedServer(Netty, port = port, host = host) {
        module(database = database, jwtSettings = JwtSettings(secret = secret))
    }.start(wait = true)
}

fun Application.module(
    database: Database,
    passwordHasher: PasswordHasher = PasswordHasher(),
    jwtSettings: JwtSettings = JwtSettings(),
    seed: Boolean = true,
) {
    DatabaseFactory.migrate(database)
    val users = UserRepository(database)
    val tasks = TaskRepository(database)
    if (seed) {
        val admin = users.ensureAdmin(passwordHasher)
        tasks.seed(admin.id)
    }
    val jwt = JwtService(jwtSettings)
    val auth = AuthService(users, passwordHasher, jwt)
    val hub = NotificationHub()
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
            },
        )
    }
    install(WebSockets)
    configureStatusPages()
    configureRequestLogging()
    configureAuthentication(jwt, users)
    configureRouting(tasks, users, auth, jwt, hub)
    log.info("Маршруты задач, роли, WebSocket и Swagger зарегистрированы")
}
