/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.io

import com.omnifret.gplayer.core.ecmaScript.Uint8Array
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hand-rewritten KMP byte-conversion helpers. The JVM original used
 * java.nio.ByteBuffer; we hand-roll the endian decode/encode so a sign-
 * extension or shift bug here would silently corrupt every binary import
 * (.gp3-.gp8). Test on known IEEE-754 byte sequences.
 */
class TypeConversionsTest {

    private fun u8(vararg bytes: Int): Uint8Array =
        Uint8Array(bytes.map { it.toUByte() }.toUByteArray())

    @Test
    fun bytesToFloat32LE_zero() {
        assertEquals(0.0, TypeConversions.bytesToFloat32LE(u8(0x00, 0x00, 0x00, 0x00)))
    }

    @Test
    fun bytesToFloat32LE_one() {
        // 1.0f → bits 0x3F800000 → LE bytes 00 00 80 3F
        assertEquals(1.0, TypeConversions.bytesToFloat32LE(u8(0x00, 0x00, 0x80, 0x3F)))
    }

    @Test
    fun bytesToFloat32LE_negative_one() {
        // -1.0f → bits 0xBF800000 → LE bytes 00 00 80 BF
        assertEquals(-1.0, TypeConversions.bytesToFloat32LE(u8(0x00, 0x00, 0x80, 0xBF)))
    }

    @Test
    fun bytesToFloat32LE_nan() {
        // Any quiet-NaN bit pattern; check NaN-ness, not exact value.
        val v = TypeConversions.bytesToFloat32LE(u8(0x00, 0x00, 0xC0, 0x7F))
        assertTrue(v.isNaN(), "expected NaN, got $v")
    }

    @Test
    fun bytesToFloat32LE_positive_infinity() {
        // +Inf → bits 0x7F800000 → LE 00 00 80 7F
        val v = TypeConversions.bytesToFloat32LE(u8(0x00, 0x00, 0x80, 0x7F))
        assertEquals(Float.POSITIVE_INFINITY.toDouble(), v)
    }

    @Test
    fun bytesToFloat64LE_one() {
        // 1.0 (double) → bits 0x3FF0000000000000 → LE 00 00 00 00 00 00 F0 3F
        val v = TypeConversions.bytesToFloat64LE(
            u8(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0, 0x3F)
        )
        assertEquals(1.0, v)
    }

    @Test
    fun bytesToFloat64LE_negative_two() {
        // -2.0 → bits 0xC000000000000000 → LE 00 00 00 00 00 00 00 C0
        val v = TypeConversions.bytesToFloat64LE(
            u8(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xC0)
        )
        assertEquals(-2.0, v)
    }

    @Test
    fun bytesToInt64LE_reads_only_4_bytes() {
        // The implementation deliberately reads a 32-bit int (legacy JVM
        // behavior we preserved) — assert the upper 4 bytes are ignored.
        val v = TypeConversions.bytesToInt64LE(u8(0x01, 0x02, 0x03, 0x04, 0xFF, 0xFF, 0xFF, 0xFF))
        // 0x04030201 = 67305985
        assertEquals(67305985.0, v)
    }

    @Test
    fun bytesToInt64LE_negative_via_high_bit() {
        // Bytes 00 00 00 80 → bit pattern 0x80000000, which as a *signed*
        // int is Int.MIN_VALUE = -2147483648.
        val v = TypeConversions.bytesToInt64LE(u8(0x00, 0x00, 0x00, 0x80))
        assertEquals(Int.MIN_VALUE.toDouble(), v)
    }

    @Test
    fun float32BEToBytes_one_round_trip() {
        // 1.0f → bits 0x3F800000 → BE bytes 3F 80 00 00
        val out = TypeConversions.float32BEToBytes(1.0).buffer.asByteArray()
        assertEquals(0x3F.toByte(), out[0])
        assertEquals(0x80.toByte(), out[1])
        assertEquals(0x00.toByte(), out[2])
        assertEquals(0x00.toByte(), out[3])
    }

    @Test
    fun float32BEToBytes_round_trips_with_LE_after_reverse() {
        val original = 3.14f.toDouble()
        val be = TypeConversions.float32BEToBytes(original).buffer.asByteArray()
        // Reverse to LE and decode.
        val le = u8(
            be[3].toInt() and 0xFF,
            be[2].toInt() and 0xFF,
            be[1].toInt() and 0xFF,
            be[0].toInt() and 0xFF,
        )
        assertEquals(original.toFloat().toDouble(), TypeConversions.bytesToFloat32LE(le))
    }

    @Test
    fun int32ToInt16_sign_extends_high_bit() {
        // 0xFFFF → narrowed to 16-bit signed → -1
        assertEquals(-1.0, TypeConversions.int32ToInt16(0xFFFF.toDouble()))
        // 0x8000 → narrowed → -32768
        assertEquals(-32768.0, TypeConversions.int32ToInt16(0x8000.toDouble()))
        // 0x7FFF → narrowed → 32767
        assertEquals(32767.0, TypeConversions.int32ToInt16(0x7FFF.toDouble()))
    }

    @Test
    fun int16ToUint32_sign_extends_through_int_intermediate() {
        // The chain `Double → Int → Short → UInt` sign-extends at the
        // Short→UInt step (Short → Int → UInt), so Short(-1) becomes
        // 0xFFFFFFFF = 4294967295, not 0x0000FFFF = 65535. Asserting the
        // actual behavior so future refactors that "fix" this chain
        // (e.g. masking with 0xFFFF) trip a test.
        assertEquals(4294967295.0, TypeConversions.int16ToUint32((-1).toDouble()))
        // Positive values remain themselves (no sign extension to worry about).
        assertEquals(0x7FFF.toDouble(), TypeConversions.int16ToUint32(0x7FFF.toDouble()))
    }

    @Test
    fun uint16ToInt16_round_trip() {
        // 0xFFFF unsigned → -1 signed
        assertEquals(-1.0, TypeConversions.uint16ToInt16(0xFFFF.toDouble()))
    }

    @Test
    fun int32ToUint16_truncates() {
        assertEquals(0xFFFF.toDouble(), TypeConversions.int32ToUint16(0xFFFFFFFF.toDouble()))
    }

    @Test
    fun int32ToUint32_round_trip() {
        // -1 as Int → 0xFFFFFFFF as UInt → 4294967295
        assertEquals(4294967295.0, TypeConversions.int32ToUint32((-1).toDouble()))
    }
}
