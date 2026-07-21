/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.corelogic.debug

import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * DEBUG-ONLY, headless (pure-Kotlin, no Android dependencies) builder for a synthetic
 * ISO 18013-5 mdoc `DeviceResponse` used to transport-test a proximity reader.
 *
 * It emits a structurally valid `DeviceResponse` CBOR containing a single document of
 * docType `com.dc26.test` whose namespace `com.dc26.test` holds one element, `filler`,
 * a deterministic byte string padded so the *serialized DeviceResponse* is ~352 KB.
 *
 * NOTE ON CRYPTO: the `issuerAuth` / `deviceAuth` COSE structures are placeholders
 * (dummy signatures). This is a transport test, not a credential test — no real PID,
 * no valid MSO, no ZK proof. A verifying reader that validates issuer/device signatures
 * will mark them invalid; a transport-integrity reader that checks size + hash will not
 * care. Whether a given reader's CBOR parser accepts placeholder auth is reader-specific
 * and can only be confirmed on hardware.
 *
 * Because the byte-string headers are all in the 4-byte-length range at ~352 KB, the
 * serialized size is a linear function of the filler length, so the target size is hit
 * exactly with a single overhead probe.
 */
object DummyMdocResponseFactory {

    const val DOC_TYPE: String = "com.dc26.test"
    const val NAMESPACE: String = "com.dc26.test"
    const val ELEMENT_IDENTIFIER: String = "filler"

    /** ~352 KB, matching the reader's expected payload size. */
    const val DEFAULT_TARGET_TOTAL_BYTES: Int = 352 * 1024

    data class Result(
        /** The full serialized mdoc DeviceResponse (what is sent over BLE). */
        val deviceResponseBytes: ByteArray,
        /** SHA-256 hex of the `filler` element value only. */
        val fillerSha256Hex: String,
        /** Length of the `filler` element value in bytes. */
        val fillerSize: Int,
        /** SHA-256 hex of the entire serialized DeviceResponse (what a raw-bytes reader hashes). */
        val deviceResponseSha256Hex: String,
        /** Total serialized DeviceResponse size in bytes. */
        val totalSize: Int,
    )

    /**
     * Builds the dummy response so that the serialized DeviceResponse is [targetTotalBytes].
     */
    fun build(targetTotalBytes: Int = DEFAULT_TARGET_TOTAL_BYTES): Result {
        // 1) Probe the fixed overhead with a filler length in the 4-byte-header range.
        val probeFillerLen = 300_000
        val overhead = buildResponse(makeFiller(probeFillerLen)).size - probeFillerLen

        // 2) Solve for the filler length that hits the target exactly.
        var fillerLen = targetTotalBytes - overhead
        // Keep the filler in the 4-byte length-header range so the linear model holds.
        if (fillerLen < 65_536) fillerLen = 65_536

        val filler = makeFiller(fillerLen)
        val response = buildResponse(filler)

        return Result(
            deviceResponseBytes = response,
            fillerSha256Hex = sha256Hex(filler),
            fillerSize = filler.size,
            deviceResponseSha256Hex = sha256Hex(response),
            totalSize = response.size,
        )
    }

    /** Deterministic, non-trivial filler so the hash is stable across runs/builds. */
    private fun makeFiller(size: Int): ByteArray =
        ByteArray(size) { ((it * 31 + 7) % 251).toByte() }

    /**
     * Assembles the DeviceResponse CBOR around the given filler value.
     *
     * DeviceResponse = { "version":"1.0", "documents":[ Document ], "status":0 }
     */
    private fun buildResponse(filler: ByteArray): ByteArray {
        val cbor = CborWriter()

        // IssuerSignedItem = { digestID, random, elementIdentifier, elementValue(filler) }
        val issuerSignedItem = CborWriter().apply {
            beginMap(4)
            textString("digestID"); uInt(0L)
            textString("random"); byteString(ByteArray(16) { (it + 1).toByte() })
            textString("elementIdentifier"); textString(ELEMENT_IDENTIFIER)
            textString("elementValue"); byteString(filler)
        }.toByteArray()

        // IssuerSignedItemBytes = #6.24(bstr .cbor IssuerSignedItem)
        val issuerSignedItemBytes = CborWriter().apply {
            tag(24L); byteString(issuerSignedItem)
        }.toByteArray()

        cbor.beginMap(3)

        cbor.textString("version"); cbor.textString("1.0")

        cbor.textString("documents")
        cbor.beginArray(1)
        run {
            cbor.beginMap(3)

            cbor.textString("docType"); cbor.textString(DOC_TYPE)

            // issuerSigned = { nameSpaces: { NAMESPACE: [ issuerSignedItemBytes ] }, issuerAuth }
            cbor.textString("issuerSigned")
            cbor.beginMap(2)
            cbor.textString("nameSpaces")
            cbor.beginMap(1)
            cbor.textString(NAMESPACE)
            cbor.beginArray(1)
            cbor.rawBytes(issuerSignedItemBytes)
            cbor.textString("issuerAuth")
            cbor.rawBytes(dummyCoseSign1())

            // deviceSigned = { nameSpaces: #6.24(bstr .cbor {}), deviceAuth: { deviceSignature } }
            cbor.textString("deviceSigned")
            cbor.beginMap(2)
            cbor.textString("nameSpaces")
            cbor.tag(24L); cbor.byteString(CborWriter().apply { beginMap(0) }.toByteArray())
            cbor.textString("deviceAuth")
            cbor.beginMap(1)
            cbor.textString("deviceSignature")
            cbor.rawBytes(dummyCoseSign1())
        }

        cbor.textString("status"); cbor.uInt(0L)

        return cbor.toByteArray()
    }

    /** COSE_Sign1 = [ protected: bstr, unprotected: {}, payload: nil, signature: bstr ] (placeholder). */
    private fun dummyCoseSign1(): ByteArray = CborWriter().apply {
        beginArray(4)
        byteString(ByteArray(0)) // protected header (empty)
        beginMap(0)              // unprotected header
        nullValue()              // detached payload
        byteString(ByteArray(64)) // dummy signature
    }.toByteArray()

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    /** Minimal CBOR encoder supporting only the major types this factory needs. */
    private class CborWriter {
        private val out = ByteArrayOutputStream()

        fun toByteArray(): ByteArray = out.toByteArray()

        fun rawBytes(bytes: ByteArray) {
            out.write(bytes)
        }

        fun uInt(value: Long) = writeHead(0, value)

        fun byteString(bytes: ByteArray) {
            writeHead(2, bytes.size.toLong())
            out.write(bytes)
        }

        fun textString(text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            writeHead(3, bytes.size.toLong())
            out.write(bytes)
        }

        fun beginArray(size: Int) = writeHead(4, size.toLong())

        fun beginMap(size: Int) = writeHead(5, size.toLong())

        fun tag(value: Long) = writeHead(6, value)

        fun nullValue() {
            out.write(0xF6) // simple value: null
        }

        private fun writeHead(majorType: Int, value: Long) {
            val mt = majorType shl 5
            when {
                value < 24 -> out.write(mt or value.toInt())
                value < 0x100 -> {
                    out.write(mt or 24); out.write(value.toInt())
                }
                value < 0x10000 -> {
                    out.write(mt or 25)
                    out.write((value ushr 8).toInt() and 0xFF)
                    out.write(value.toInt() and 0xFF)
                }
                value < 0x100000000L -> {
                    out.write(mt or 26)
                    out.write((value ushr 24).toInt() and 0xFF)
                    out.write((value ushr 16).toInt() and 0xFF)
                    out.write((value ushr 8).toInt() and 0xFF)
                    out.write(value.toInt() and 0xFF)
                }
                else -> {
                    out.write(mt or 27)
                    for (shift in 56 downTo 0 step 8) {
                        out.write((value ushr shift).toInt() and 0xFF)
                    }
                }
            }
        }
    }
}
