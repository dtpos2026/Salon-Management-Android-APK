package com.dtpos.salonmanager.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Salted PBKDF2-HMAC-SHA256 hashing for the app PIN / password and recovery code.
 * Plain-text secrets are never stored. Encoded form: `pbkdf2$<iterations>$<salt b64>$<hash b64>`.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PREFIX = "pbkdf2"
    const val DEFAULT_ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    private val random = SecureRandom()

    fun hash(secret: CharArray, iterations: Int = DEFAULT_ITERATIONS): String {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val derived = derive(secret, salt, iterations)
        val encoder = Base64.getEncoder()
        return listOf(PREFIX, iterations.toString(), encoder.encodeToString(salt), encoder.encodeToString(derived))
            .joinToString("$")
    }

    fun verify(secret: CharArray, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        return try {
            val decoder = Base64.getDecoder()
            val salt = decoder.decode(parts[2])
            val expected = decoder.decode(parts[3])
            MessageDigest.isEqual(expected, derive(secret, salt, iterations))
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun derive(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Human-friendly one-time recovery code, e.g. "7KQM-2XWD-HP9R" (no ambiguous characters). */
    fun newRecoveryCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (0 until 3).joinToString("-") {
            (0 until 4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        }
    }

    fun normalizeRecoveryCode(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }
}
