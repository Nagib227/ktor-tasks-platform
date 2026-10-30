package ru.ithub.tasks.storage

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DatabaseFactory {
    fun connectFromEnv(): Database {
        val url = System.getenv("DATABASE_URL")
            ?: "jdbc:h2:file:./data/tasks;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        val postgres = url.startsWith("jdbc:postgresql:")
        val user = System.getenv("DATABASE_USER") ?: if (postgres) "tasks" else "sa"
        val password = System.getenv("DATABASE_PASSWORD") ?: if (postgres) "tasks" else ""
        val driver = if (postgres) "org.postgresql.Driver" else "org.h2.Driver"
        return connectWithRetry(url, driver, user, password)
    }

    @Suppress("DEPRECATION")
    fun migrate(database: Database) {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(UsersTable, TasksTable)
        }
    }

    private fun connectWithRetry(url: String, driver: String, user: String, password: String): Database {
        var last: Exception? = null
        repeat(30) { attempt ->
            try {
                val database = Database.connect(url, driver = driver, user = user, password = password)
                transaction(database) {
                    exec("SELECT 1")
                }
                return database
            } catch (error: Exception) {
                last = error
                if (attempt < 29) {
                    Thread.sleep(1000)
                }
            }
        }
        throw IllegalStateException("Не удалось подключиться к базе: $url", last)
    }
}
