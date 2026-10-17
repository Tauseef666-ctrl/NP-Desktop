/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.backup

import org.koin.core.annotation.Singleton
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-based authenticated encryption for desktop backup archives, using only standard
 * primitives from the JDK's JCE (no custom cryptography):
 *
 *  - key derivation: PBKDF2 with HMAC-SHA-256, [ITERATIONS] iterations, a fresh 128-bit salt;
 *  - encryption: AES-256 in GCM mode (128-bit authentication tag), a fresh 96-bit nonce.
 *
 * [encrypt] returns a [BackupEnvelope]-framed archive; [decrypt] returns the plaintext or `null`.
 * A wrong passphrase or any tampering surfaces as a GCM tag failure and therefore `null` — the
 * caller must treat that as "cannot read", never as empty-but-valid data.
 */
@Singleton
class JVMBackupCipher {

    val isAvailable: Boolean = true

    /** Derives a key from [passphrase] and returns the framed ciphertext, or `null` on failure. */
    fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray? {
        if (passphrase.isEmpty()) return null
        return try {
            val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
            val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                deriveKey(passphrase, salt, ITERATIONS),
                GCMParameterSpec(TAG_BITS, nonce)
            )
            val ciphertext = cipher.doFinal(plaintext)
            val header = BackupEncryptionHeader(
                iterations = ITERATIONS,
                saltBase64 = Base64.getEncoder().encodeToString(salt),
                nonceBase64 = Base64.getEncoder().encodeToString(nonce)
            )
            BackupEnvelope.encode(header, ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decrypts a [BackupEnvelope]-framed archive with [passphrase]. Returns `null` when the envelope
     * is malformed, belongs to another app, the passphrase is wrong, or the ciphertext was tampered.
     */
    fun decrypt(envelope: ByteArray, passphrase: String): ByteArray? {
        if (passphrase.isEmpty()) return null
        val (header, ciphertext) = BackupEnvelope.decode(envelope) ?: return null
        if (header.appName != BackupOps.APP_NAME) return null
        if (header.schemaVersion != BackupOps.ENCRYPTED_SCHEMA_VERSION) return null
        if (header.algorithm != BackupEncryptionHeader.ALGORITHM_AES_GCM) return null
        if (header.kdf != BackupEncryptionHeader.KDF_PBKDF2_HMAC_SHA256) return null
        if (header.iterations <= 0) return null
        return try {
            val salt = Base64.getDecoder().decode(header.saltBase64)
            val nonce = Base64.getDecoder().decode(header.nonceBase64)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                deriveKey(passphrase, salt, header.iterations),
                GCMParameterSpec(TAG_BITS, nonce)
            )
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS)
        val secret = SecretKeyFactory.getInstance(KDF).generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KDF = "PBKDF2WithHmacSHA256"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val SALT_BYTES = 16
        const val NONCE_BYTES = 12
        const val ITERATIONS = 120_000
        val random = SecureRandom()
    }
}
