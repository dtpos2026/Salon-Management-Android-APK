package com.dtpos.salonmanager.services.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupCryptoException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { WRONG_PASSWORD, CORRUPT, NOT_ENCRYPTED }
}

/**
 * Optional password protection for backups: AES-256-GCM with a PBKDF2-HMAC-SHA256 derived key.
 *
 * Layout: "SALONENC" | version(1) | iterations(4) | salt(16) | iv(12) | ciphertext+tag
 * GCM authenticates the whole file, so a wrong password or any tampering is detected.
 */
object BackupCrypto {
    private val MAGIC = "SALONENC".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    const val DEFAULT_ITERATIONS = 150_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val HEADER_SIZE = MAGIC.size + 1 + 4 + SALT_BYTES + IV_BYTES

    private val random = SecureRandom()

    fun isEncrypted(data: ByteArray): Boolean =
        data.size >= MAGIC.size && MAGIC.indices.all { data[it] == MAGIC[it] }

    fun encrypt(plain: ByteArray, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        require(password.isNotEmpty()) { "password required" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        val header = ByteBuffer.allocate(HEADER_SIZE)
            .put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(iv).array()
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if (!isEncrypted(data)) throw BackupCryptoException(BackupCryptoException.Reason.NOT_ENCRYPTED)
        if (data.size <= HEADER_SIZE) throw BackupCryptoException(BackupCryptoException.Reason.CORRUPT)
        val buffer = ByteBuffer.wrap(data)
        buffer.position(MAGIC.size)
        if (buffer.get() != VERSION) throw BackupCryptoException(BackupCryptoException.Reason.CORRUPT)
        val iterations = buffer.int
        if (iterations !in 1_000..5_000_000) throw BackupCryptoException(BackupCryptoException.Reason.CORRUPT)
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(data, 0, HEADER_SIZE)
            cipher.doFinal(data, HEADER_SIZE, data.size - HEADER_SIZE)
        } catch (e: AEADBadTagException) {
            throw BackupCryptoException(BackupCryptoException.Reason.WRONG_PASSWORD, e)
        } catch (e: java.security.GeneralSecurityException) {
            throw BackupCryptoException(BackupCryptoException.Reason.CORRUPT, e)
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
