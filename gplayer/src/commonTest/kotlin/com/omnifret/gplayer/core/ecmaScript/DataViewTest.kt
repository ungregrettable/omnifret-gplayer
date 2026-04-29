/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.core.ecmaScript

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KMP-port of the JVM ByteBuffer-backed DataView. Critical surface for
 * the GP7/GP8 binary parsers, which read floats and signed-16 integers
 * out of bytes via DataView. Test endian-explicit calls on known
 * sequences.
 */
class DataViewTest {

    private fun buf(vararg bytes: Int): ArrayBuffer =
        bytes.map { it.toUByte() }.toUByteArray()

    @Test
    fun getFloat32_LE_one() {
        val view = DataView(buf(0x00, 0x00, 0x80, 0x3F))
        assertEquals(1.0, view.getFloat32(0.0, true))
    }

    @Test
    fun getFloat32_BE_one() {
        val view = DataView(buf(0x3F, 0x80, 0x00, 0x00))
        assertEquals(1.0, view.getFloat32(0.0, false))
    }

    @Test
    fun getFloat32_LE_with_byteOffset() {
        // Skip 2 bytes of padding, then read a LE 1.0.
        val view = DataView(buf(0xFF, 0xFF, 0x00, 0x00, 0x80, 0x3F), 2.0, 4.0)
        assertEquals(1.0, view.getFloat32(0.0, true))
    }

    @Test
    fun getFloat32_LE_index_offset() {
        val view = DataView(buf(0x00, 0x00, 0x00, 0x00, 0x80, 0x3F))
        assertEquals(1.0, view.getFloat32(2.0, true))
    }

    @Test
    fun getInt16_LE_negative_sign_extends() {
        // 0xFF80 LE: bytes 0x80 0xFF → signed 16-bit -128
        val view = DataView(buf(0x80, 0xFF))
        assertEquals(-128.0, view.getInt16(0.0, true))
    }

    @Test
    fun getInt16_BE_negative_sign_extends() {
        // 0xFF80 BE: bytes 0xFF 0x80 → signed 16-bit -128
        val view = DataView(buf(0xFF, 0x80))
        assertEquals(-128.0, view.getInt16(0.0, false))
    }

    @Test
    fun getInt16_positive_max() {
        // 0x7FFF LE = 32767
        assertEquals(32767.0, DataView(buf(0xFF, 0x7F)).getInt16(0.0, true))
        // 0x7FFF BE = 32767
        assertEquals(32767.0, DataView(buf(0x7F, 0xFF)).getInt16(0.0, false))
    }

    @Test
    fun getInt16_negative_one() {
        // 0xFFFF → -1
        assertEquals(-1.0, DataView(buf(0xFF, 0xFF)).getInt16(0.0, true))
    }

    @Test
    fun getInt16_zero() {
        assertEquals(0.0, DataView(buf(0x00, 0x00)).getInt16(0.0, true))
    }
}
