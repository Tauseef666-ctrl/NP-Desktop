/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.backup

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupEnvelopeTest {

    private fun header(iterations: Int = 120_000) = BackupEncryptionHeader(
        iterations = iterations,
        saltBase64 = "c2FsdA==",
        nonceBase64 = "bm9uY2U="
    )

    @Test
    fun encodeThenDecodeRoundTripsHeaderAndCiphertext() {
        val ciphertext = byteArrayOf(10, 20, 30, 40, 50)

        val framed = BackupEnvelope.encode(header(), ciphertext)
        val decoded = assertNotNull(BackupEnvelope.decode(framed))

        assertEquals(header(), decoded.first)
        assertContentEquals(ciphertext, decoded.second)
    }

    @Test
    fun encodedArchiveCarriesTheMagicPrefix() {
        val framed = BackupEnvelope.encode(header(), byteArrayOf(1))
        assertTrue(BackupEnvelope.isEncrypted(framed))
        assertContentEquals(BackupEnvelope.MAGIC.encodeToByteArray(), framed.copyOf(7))
    }

    @Test
    fun defaultsStampStandardAlgorithmAndKdf() {
        val decoded = assertNotNull(
            BackupEnvelope.decode(BackupEnvelope.encode(header(), byteArrayOf()))
        )
        assertEquals(BackupOps.APP_NAME, decoded.first.appName)
        assertEquals(BackupOps.ENCRYPTED_SCHEMA_VERSION, decoded.first.schemaVersion)
        assertEquals(BackupEncryptionHeader.ALGORITHM_AES_GCM, decoded.first.algorithm)
        assertEquals(BackupEncryptionHeader.KDF_PBKDF2_HMAC_SHA256, decoded.first.kdf)
    }

    @Test
    fun plainZipBytesAreNotDetectedAsEncrypted() {
        val plain = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 5, 6, 7, 8)
        assertFalse(BackupEnvelope.isEncrypted(plain))
        assertNull(BackupEnvelope.decode(plain))
    }

    @Test
    fun shortOrEmptyInputIsNotEncrypted() {
        assertFalse(BackupEnvelope.isEncrypted(byteArrayOf()))
        assertFalse(BackupEnvelope.isEncrypted(BackupEnvelope.MAGIC.encodeToByteArray().copyOf(3)))
    }

    @Test
    fun truncatedAfterMagicFailsToDecode() {
        val magic = BackupEnvelope.MAGIC.encodeToByteArray()
        assertNull(BackupEnvelope.decode(magic))
        assertNull(BackupEnvelope.decode(magic.copyOf(9)))
    }

    @Test
    fun headerLengthBeyondBufferFailsToDecode() {
        val magic = BackupEnvelope.MAGIC.encodeToByteArray()
        val framed = ByteArray(11)
        magic.copyInto(framed, 0)
        framed[7] = 0x7F
        framed[8] = 0xFF.toByte()
        framed[9] = 0xFF.toByte()
        framed[10] = 0xFF.toByte()
        assertNull(BackupEnvelope.decode(framed))
    }

    @Test
    fun unparseableHeaderJsonFailsToDecode() {
        val magic = BackupEnvelope.MAGIC.encodeToByteArray()
        val body = "not-json".encodeToByteArray()
        val framed = ByteArray(11 + body.size)
        magic.copyInto(framed, 0)
        framed[7] = 0
        framed[8] = 0
        framed[9] = 0
        framed[10] = body.size.toByte()
        body.copyInto(framed, 11)
        assertNull(BackupEnvelope.decode(framed))
    }

    @Test
    fun emptyCiphertextRoundTripsToEmptyArray() {
        val decoded = assertNotNull(
            BackupEnvelope.decode(BackupEnvelope.encode(header(), byteArrayOf()))
        )
        assertContentEquals(byteArrayOf(), decoded.second)
    }
}
