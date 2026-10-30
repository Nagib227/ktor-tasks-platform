package ru.ithub.tasks.security

import at.favre.lib.crypto.bcrypt.BCrypt

/** Хэширует пароль через BCrypt и проверяет его сверкой хэша. */
class PasswordHasher(private val cost: Int = DEFAULT_COST) {
    init {
        require(cost in 4..15) { "BCrypt cost должен быть от 4 до 15" }
    }

    fun hash(password: String): String =
        BCrypt.withDefaults().hashToString(cost, password.toCharArray())

    fun verify(password: String, passwordHash: String): Boolean =
        try {
            BCrypt.verifyer().verify(password.toCharArray(), passwordHash).verified
        } catch (_: IllegalArgumentException) {
            false
        }

    private companion object {
        const val DEFAULT_COST = 10
    }
}
