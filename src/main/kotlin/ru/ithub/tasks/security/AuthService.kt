package ru.ithub.tasks.security

import ru.ithub.tasks.model.BadRequestException
import ru.ithub.tasks.model.LoginRequest
import ru.ithub.tasks.model.RegisterRequest
import ru.ithub.tasks.model.Role
import ru.ithub.tasks.model.TokenResponse
import ru.ithub.tasks.model.UnauthorizedException
import ru.ithub.tasks.model.UserResponse
import ru.ithub.tasks.storage.UserRepository

class AuthService(
    private val users: UserRepository,
    private val passwords: PasswordHasher,
    private val jwt: JwtService,
) {
    fun register(request: RegisterRequest): UserResponse {
        val login = normalizeLogin(request.login)
        requirePassword(request.password)
        val user = users.create(login, passwords.hash(request.password), Role.USER)
        return UserResponse(id = user.id, login = user.login, role = user.role)
    }

    fun login(request: LoginRequest): TokenResponse {
        val login = request.login.trim().lowercase()
        val user = users.findByLogin(login)
        if (user == null || !passwords.verify(request.password, user.passwordHash)) {
            throw UnauthorizedException("Неверный логин или пароль")
        }
        return TokenResponse(
            token = jwt.issue(user),
            expiresIn = jwt.expirationSeconds,
            userId = user.id,
            role = user.role,
        )
    }

    private fun normalizeLogin(raw: String): String {
        val login = raw.trim().lowercase()
        if (!LOGIN_PATTERN.matches(login)) {
            throw BadRequestException("Логин: 3–32 символа, латиница, цифры, _ и -")
        }
        return login
    }

    private fun requirePassword(password: String) {
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw BadRequestException("Пароль не короче $MIN_PASSWORD_LENGTH символов")
        }
        if (password.length > MAX_PASSWORD_LENGTH) {
            throw BadRequestException("Пароль не длиннее $MAX_PASSWORD_LENGTH символов")
        }
    }

    private companion object {
        val LOGIN_PATTERN = Regex("^[a-z0-9_-]{3,32}$")
        const val MIN_PASSWORD_LENGTH = 6
        const val MAX_PASSWORD_LENGTH = 72
    }
}
