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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestDummyMdocResponseFactory {

    /** (a) The serialized DeviceResponse is ~352 KB (within a tolerance band). */
    @Test
    fun deviceResponse_size_is_about_352kb() {
        val target = DummyMdocResponseFactory.DEFAULT_TARGET_TOTAL_BYTES
        val result = DummyMdocResponseFactory.build(target)

        // The linear-overhead model should hit the target essentially exactly.
        val tolerance = 2 * 1024
        assertTrue(
            "total=${result.totalSize} target=$target",
            kotlin.math.abs(result.totalSize - target) <= tolerance,
        )
        // The filler dominates the payload.
        assertTrue(result.fillerSize > 300 * 1024)
    }

    /** (b) The filler value (and thus its SHA-256) is deterministic across builds. */
    @Test
    fun filler_hash_is_stable_across_runs() {
        val a = DummyMdocResponseFactory.build()
        val b = DummyMdocResponseFactory.build()

        assertEquals(a.fillerSize, b.fillerSize)
        assertEquals(a.fillerSha256Hex, b.fillerSha256Hex)
        assertEquals(a.deviceResponseSha256Hex, b.deviceResponseSha256Hex)
        // 64 hex chars == 32-byte SHA-256.
        assertEquals(64, a.fillerSha256Hex.length)
    }

    /** Structural sanity: the CBOR carries the expected docType and element identifier. */
    @Test
    fun response_contains_doctype_and_element_identifier() {
        val bytes = DummyMdocResponseFactory.build().deviceResponseBytes
        val asLatin1 = String(bytes, Charsets.ISO_8859_1)

        assertTrue(asLatin1.contains(DummyMdocResponseFactory.DOC_TYPE))
        assertTrue(asLatin1.contains(DummyMdocResponseFactory.ELEMENT_IDENTIFIER))
        // DeviceResponse is a CBOR map of 3 entries -> first byte 0xA3.
        assertEquals(0xA3.toByte(), bytes[0])
    }
}
