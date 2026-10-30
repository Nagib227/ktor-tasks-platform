package ru.ithub.tasks

import io.ktor.server.testing.ApplicationTestBuilder
import org.jetbrains.exposed.v1.jdbc.Database
import ru.ithub.tasks.security.JwtSettings
import ru.ithub.tasks.security.PasswordHasher
import java.util.UUID

fun testDatabase(): Database {
    val name = "t" + UUID.randomUUID().toString().replace("-", "")
    return Database.connect(
        url = "jdbc:h2:mem:$name;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver",
        user = "sa",
        password = "",
    )
}

fun ApplicationTestBuilder.installApp(
    database: Database = testDatabase(),
    passwordHasher: PasswordHasher = PasswordHasher(cost = 4),
    jwtSettings: JwtSettings = JwtSettings(expirationMs = 60 * 60 * 1000),
    seed: Boolean = true,
): Database {
    application {
        module(database, passwordHasher, jwtSettings, seed)
    }
    return database
}
