/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Self-describing header written at the front of every encrypted backup archive. It records exactly
 * which authenticated-encryption scheme and key-derivation function produced the ciphertext, so a
 * reader never has to guess parameters. Only standard, named primitives are ever written here
 * ([algorithm] `AES-GCM`, [kdf] `PBKDF2WithHmacSHA256`); no custom cryptography.
 *
 * @param appName Owning application marker, checked against [BackupOps.APP_NAME].
 * @param schemaVersion Encrypted-layout version, checked against [BackupOps.ENCRYPTED_SCHEMA_VERSION].
 * @param algorithm AEAD cipher name (for example `AES-GCM`).
 * @param kdf Key-derivation function name.
 * @param iterations PBKDF2 iteration count used for the key.
 * @param saltBase64 Base64 of the random KDF salt.
 * @param nonceBase64 Base64 of the random AEAD nonce/IV.
 */
@Serializable
data class BackupEncryptionHeader(
    val appName: String = BackupOps.APP_NAME,
    val schemaVersion: Int = BackupOps.ENCRYPTED_SCHEMA_VERSION,
    val algorithm: String = ALGORITHM_AES_GCM,
    val kdf: String = KDF_PBKDF2_HMAC_SHA256,
    val iterations: Int,
    val saltBase64: String,
    val nonceBase64: String
) {
    companion object {
        const val ALGORITHM_AES_GCM = "AES-GCM"
        const val KDF_PBKDF2_HMAC_SHA256 = "PBKDF2WithHmacSHA256"
    }
}

/**
 * Pure framing for an encrypted backup archive: `MAGIC | headerLength(4, big-endian) | headerJson |
 * ciphertext`. The fixed [MAGIC] prefix lets a reader detect an encrypted archive (and ask for a
 * passphrase) without any key material, while [encode]/[decode] never perform cryptography — they
 * only assemble and split bytes, so they are deterministic and fully unit-testable.
 */
object BackupEnvelope {

    /** ASCII marker identifying an encrypted NewPipe+ backup archive. */
    const val MAGIC = "NPPENC1"

    private const val MAGIC_BYTES = 7
    private const val LENGTH_BYTES = 4
    private const val PREFIX_BYTES = MAGIC_BYTES + LENGTH_BYTES

    private val magicBytes = MAGIC.encodeToByteArray()
    private val json = Json { ignoreUnknownKeys = true }

    /** True when [bytes] starts with the encrypted-archive [MAGIC] marker. */
    fun isEncrypted(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC_BYTES) return false
        for (index in magicBytes.indices) {
            if (bytes[index] != magicBytes[index]) return false
        }
        return true
    }

    /** Assembles the framed archive: magic, 4-byte big-endian header length, header JSON, ciphertext. */
    fun encode(header: BackupEncryptionHeader, ciphertext: ByteArray): ByteArray {
        val headerBytes = json.encodeToString(header).encodeToByteArray()
        val result = ByteArray(PREFIX_BYTES + headerBytes.size + ciphertext.size)
        magicBytes.copyInto(result, 0)
        writeInt(result, MAGIC_BYTES, headerBytes.size)
        headerBytes.copyInto(result, PREFIX_BYTES)
        ciphertext.copyInto(result, PREFIX_BYTES + headerBytes.size)
        return result
    }

    /**
     * Splits an encrypted archive into its header and ciphertext. Returns `null` when [bytes] is not
     * framed as an encrypted archive, is truncated, or carries an unparseable header — the caller
     * then refuses the file instead of guessing.
     */
    fun decode(bytes: ByteArray): Pair<BackupEncryptionHeader, ByteArray>? {
        if (!isEncrypted(bytes) || bytes.size < PREFIX_BYTES) return null
        val headerLength = readInt(bytes, MAGIC_BYTES)
        if (headerLength < 0 || headerLength > bytes.size - PREFIX_BYTES) return null
        val headerJson = bytes.copyOfRange(PREFIX_BYTES, PREFIX_BYTES + headerLength).decodeToString()
        val header = try {
            json.decodeFromString<BackupEncryptionHeader>(headerJson)
        } catch (_: Exception) {
            return null
        }
        return header to bytes.copyOfRange(PREFIX_BYTES + headerLength, bytes.size)
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    private fun readInt(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xFF) shl 24) or
            ((source[offset + 1].toInt() and 0xFF) shl 16) or
            ((source[offset + 2].toInt() and 0xFF) shl 8) or
            (source[offset + 3].toInt() and 0xFF)
}
