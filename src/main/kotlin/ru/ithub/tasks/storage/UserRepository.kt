package ru.ithub.tasks.storage

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import ru.ithub.tasks.model.ConflictException
import ru.ithub.tasks.model.Role
import ru.ithub.tasks.model.User
import ru.ithub.tasks.security.PasswordHasher

class UserRepository(private val database: Database) {
    fun findById(id: Int): User? = transaction(database) {
        UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.toUser()
    }

    fun findByLogin(login: String): User? = transaction(database) {
        UsersTable.selectAll().where { UsersTable.login eq login }.singleOrNull()?.toUser()
    }

    fun list(): List<User> = transaction(database) {
        UsersTable.selectAll().map { it.toUser() }.sortedBy { it.id }
    }

    fun create(login: String, passwordHash: String, role: Role): User = transaction(database) {
        val existing = UsersTable.selectAll().where { UsersTable.login eq login }.singleOrNull()
        if (existing != null) {
            throw ConflictException("Пользователь с логином «$login» уже существует")
        }
        val id = UsersTable.insert {
            it[UsersTable.login] = login
            it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.role] = role.name
        }[UsersTable.id]
        User(id = id, login = login, passwordHash = passwordHash, role = role)
    }

    fun ensureAdmin(passwords: PasswordHasher): User {
        findByLogin(ADMIN_LOGIN)?.let { return it }
        return create(ADMIN_LOGIN, passwords.hash(ADMIN_PASSWORD), Role.ADMIN)
    }

    private fun ResultRow.toUser(): User = User(
        id = this[UsersTable.id],
        login = this[UsersTable.login],
        passwordHash = this[UsersTable.passwordHash],
        role = Role.valueOf(this[UsersTable.role]),
    )

    companion object {
        const val ADMIN_LOGIN = "admin"
        const val ADMIN_PASSWORD = "admin123"
    }
}
