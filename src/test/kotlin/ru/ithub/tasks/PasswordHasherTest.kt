package ru.ithub.tasks

import ru.ithub.tasks.security.PasswordHasher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordHasherTest {
    private val hasher = PasswordHasher(cost = 4)

    @Test
    fun `hash is not the password and verifies only the same password`() {
        val password = "secret12"
        val hash = hasher.hash(password)
        val second = hasher.hash(password)

        assertNotEquals(password, hash)
        assertTrue(hash.startsWith("$2"))
        assertNotEquals(hash, second)
        assertTrue(hasher.verify(password, hash))
        assertFalse(hasher.verify("other-password", hash))
    }
}
