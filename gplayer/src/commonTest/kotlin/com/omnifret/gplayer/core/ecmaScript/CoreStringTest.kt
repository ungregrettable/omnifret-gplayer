/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

package com.omnifret.gplayer.core.ecmaScript

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The KMP port hand-rolls surrogate-pair encoding because Kotlin/Native
 * lacks the JVM `String(IntArray)` constructor. Test against known
 * code-point boundaries to catch off-by-one shifts or surrogate-range
 * bugs.
 */
class CoreStringTest {

    @Test
    fun fromCharCode_basic_latin() {
        assertEquals("A", CoreString.fromCharCode(0x41.toDouble()))
    }

    @Test
    fun fromCodePoint_bmp_value_yields_single_char() {
        assertEquals("é", CoreString.fromCodePoint(0x00E9.toDouble())) // é
        assertEquals(1, CoreString.fromCodePoint(0x00E9.toDouble()).length)
    }

    @Test
    fun fromCodePoint_supplementary_plane_yields_surrogate_pair() {
        // 𐐷 (DESERET SMALL LETTER YEE, U+10437) decomposes to high=D801 low=DC37.
        val s = CoreString.fromCodePoint(0x10437.toDouble())
        assertEquals(2, s.length, "supplementary code point should be 2 UTF-16 chars")
        assertEquals(0xD801.toChar(), s[0], "high surrogate")
        assertEquals(0xDC37.toChar(), s[1], "low surrogate")
    }

    @Test
    fun fromCodePoint_at_supplementary_boundary() {
        // U+10000 → high=0xD800 low=0xDC00 (the lowest supplementary code point)
        val s = CoreString.fromCodePoint(0x10000.toDouble())
        assertEquals(0xD800.toChar(), s[0])
        assertEquals(0xDC00.toChar(), s[1])
    }

    @Test
    fun fromCodePoint_varargs_concatenates() {
        // BMP "A" + supplementary U+10437 + BMP "B"
        val s = CoreString.fromCodePoint(0x41.toDouble(), 0x10437.toDouble(), 0x42.toDouble())
        assertEquals(4, s.length, "1 + 2 + 1 UTF-16 chars")
        assertEquals('A', s[0])
        assertEquals(0xD801.toChar(), s[1])
        assertEquals(0xDC37.toChar(), s[2])
        assertEquals('B', s[3])
    }
}
