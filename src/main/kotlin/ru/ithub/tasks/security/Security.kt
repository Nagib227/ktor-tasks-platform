package ru.ithub.tasks.security

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import ru.ithub.tasks.model.ForbiddenException
import ru.ithub.tasks.model.Role
import ru.ithub.tasks.model.UnauthorizedException
import ru.ithub.tasks.model.User
import ru.ithub.tasks.storage.UserRepository

fun Application.configureAuthentication(jwt: JwtService, users: UserRepository) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwt.settings.realm
            verifier(jwt.verifier())
            validate { credential ->
                val userId = runCatching { credential.payload.getClaim("userId").asInt() }.getOrNull()
                if (userId != null && users.findById(userId) != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                throw UnauthorizedException("Нужен действительный токен в заголовке Authorization: Bearer")
            }
        }
    }
}

fun ApplicationCall.requireUser(users: UserRepository): User {
    val principal = principal<JWTPrincipal>()
        ?: throw UnauthorizedException("Нужен действительный токен в заголовке Authorization: Bearer")
    val userId = runCatching { principal.payload.getClaim("userId").asInt() }.getOrNull()
        ?: throw UnauthorizedException("Нужен действительный токен в заголовке Authorization: Bearer")
    return users.findById(userId)
        ?: throw UnauthorizedException("Нужен действительный токен в заголовке Authorization: Bearer")
}

fun ApplicationCall.requireAdmin(users: UserRepository): User {
    val user = requireUser(users)
    if (user.role != Role.ADMIN) {
        throw ForbiddenException("Маршрут доступен только администратору")
    }
    return user
}
