/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.backup

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JVMBackupCipherTest {

    private val cipher = JVMBackupCipher()

    @Test
    fun encryptThenDecryptRoundTripsBytesExactly() {
        val plaintext = "the quick brown fox".encodeToByteArray()

        val envelope = assertNotNull(cipher.encrypt(plaintext, "correct horse"))
        val recovered = assertNotNull(cipher.decrypt(envelope, "correct horse"))

        assertContentEquals(plaintext, recovered)
    }

    @Test
    fun encryptedOutputIsFramedAsEnvelope() {
        val envelope = assertNotNull(cipher.encrypt(byteArrayOf(1, 2, 3), "pw"))
        assertTrue(BackupEnvelope.isEncrypted(envelope))
        assertNotNull(BackupEnvelope.decode(envelope))
    }

    @Test
    fun decryptWithWrongPassphraseReturnsNull() {
        val envelope = assertNotNull(cipher.encrypt("secret data".encodeToByteArray(), "right"))
        assertNull(cipher.decrypt(envelope, "wrong"))
    }

    @Test
    fun decryptWithEmptyPassphraseReturnsNull() {
        val envelope = assertNotNull(cipher.encrypt("secret data".encodeToByteArray(), "right"))
        assertNull(cipher.decrypt(envelope, ""))
    }

    @Test
    fun encryptWithEmptyPassphraseReturnsNull() {
        assertNull(cipher.encrypt("data".encodeToByteArray(), ""))
    }

    @Test
    fun tamperedCiphertextFailsAuthentication() {
        val envelope = assertNotNull(cipher.encrypt("payload".encodeToByteArray(), "pw"))
        envelope[envelope.size - 1] = (envelope[envelope.size - 1].toInt() xor 0xFF).toByte()
        assertNull(cipher.decrypt(envelope, "pw"))
    }

    @Test
    fun eachEncryptionUsesAFreshNonce() {
        val first = assertNotNull(cipher.encrypt("same".encodeToByteArray(), "pw"))
        val second = assertNotNull(cipher.encrypt("same".encodeToByteArray(), "pw"))
        assertFalse(first.contentEquals(second), "nonce reuse would produce identical archives")
    }

    @Test
    fun headerRecordsTheStandardPrimitives() {
        val envelope = assertNotNull(cipher.encrypt("data".encodeToByteArray(), "pw"))
        val header = assertNotNull(BackupEnvelope.decode(envelope)).first
        assertTrue(header.iterations > 0)
        assertTrue(header.saltBase64.isNotEmpty())
        assertTrue(header.nonceBase64.isNotEmpty())
    }

    @Test
    fun decryptRejectsNonEnvelopeInput() {
        assertNull(cipher.decrypt(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), "pw"))
        assertNull(cipher.decrypt(byteArrayOf(), "pw"))
    }
}
