package ru.ithub.tasks.storage

import org.jetbrains.exposed.v1.core.Table

object UsersTable : Table("users") {
    val id = integer("id").autoIncrement()
    val login = varchar("login", 32).uniqueIndex()
    val passwordHash = varchar("password_hash", 100)
    val role = varchar("role", 16)

    override val primaryKey = PrimaryKey(id)
}

object TasksTable : Table("tasks") {
    val id = integer("id").autoIncrement()
    val title = varchar("title", 200)
    val done = bool("done")
    val priority = varchar("priority", 16)
    val ownerId = integer("owner_id").references(UsersTable.id)

    override val primaryKey = PrimaryKey(id)
}
