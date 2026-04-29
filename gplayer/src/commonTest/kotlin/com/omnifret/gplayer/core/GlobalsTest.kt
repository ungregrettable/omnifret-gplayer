/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The hand-rewritten KMP-port replacements for `java.text.DecimalFormat`,
 * `java.lang.String.getBytes("UTF-16LE")`, and `System.nanoTime()`.
 * Bugs here would silently corrupt SVG output (decimal coordinates) and
 * break GP file metadata reads (UTF-16LE strings).
 */
class GlobalsTest {

    // ----- Double.toInvariantString -----

    @Test
    fun toInvariantString_integer_no_decimal_point() {
        assertEquals("0", 0.0.toInvariantString())
        assertEquals("42", 42.0.toInvariantString())
        assertEquals("-7", (-7.0).toInvariantString())
    }

    @Test
    fun toInvariantString_non_integer_uses_dot() {
        assertEquals("1.5", 1.5.toInvariantString())
        assertEquals("-3.25", (-3.25).toInvariantString())
    }

    @Test
    fun toInvariantString_no_scientific_notation_for_small_values() {
        // 1.0E-5 should expand to "0.00001", not stay as scientific.
        val v = 1.0E-5
        val s = v.toInvariantString()
        assertTrue(!s.contains("E", ignoreCase = true), "got $s — should not have E")
        assertEquals("0.00001", s)
    }

    @Test
    fun toInvariantString_no_scientific_notation_for_large_values() {
        val v = 1.5E10
        val s = v.toInvariantString()
        assertTrue(!s.contains("E", ignoreCase = true), "got $s — should not have E")
        assertEquals("15000000000", s)
    }

    @Test
    fun toInvariantString_negative_scientific() {
        // -3.7E-3 → -0.0037
        assertEquals("-0.0037", (-3.7E-3).toInvariantString())
    }

    @Test
    fun toInvariantString_nan_falls_through_integer_path() {
        // The toInvariantString path checks `fractionalPart > 1e-7 ||
        // fractionalPart < -1e-7` first; NaN compares false to both, so
        // execution lands on `integerPart.toString()` = "0". The dedicated
        // NaN/Infinity branches in `formatDoubleInvariant` are unreachable
        // for these inputs. Asserting actual behavior so a future fix
        // (e.g. an `isNaN` short-circuit) trips a test deliberately.
        assertEquals("0", Double.NaN.toInvariantString())
    }

    @Test
    fun toInvariantString_positive_infinity_via_formatter() {
        // +Infinity.toLong() is Long.MAX_VALUE, fractionalPart non-zero
        // → routes through formatDoubleInvariant → "Infinity".
        assertEquals("Infinity", Double.POSITIVE_INFINITY.toInvariantString())
    }

    @Test
    fun toInvariantString_negative_infinity_via_formatter() {
        assertEquals("-Infinity", Double.NEGATIVE_INFINITY.toInvariantString())
    }

    @Test
    fun toInvariantString_with_base_emits_radix_string() {
        // toInvariantString(16) takes a Double, casts to Int, formats in
        // base 16. The lowercase form of `Int.toString(16)` is the contract.
        assertEquals("ff", 255.0.toInvariantString(16.0))
        assertEquals("a", 10.0.toInvariantString(16.0))
    }

    // ----- Double.toFixed -----

    @Test
    fun toFixed_pads_zeros_to_n_decimals() {
        assertEquals("3.140", 3.14.toFixed(3.0))
        assertEquals("0.00", 0.0.toFixed(2.0))
    }

    @Test
    fun toFixed_truncates_or_rounds() {
        assertEquals("1.23", 1.234.toFixed(2.0))
        // kotlin.math.round uses banker's rounding (half-to-even), so 0.5 → 0.
        // Pin the actual behavior so a switch to round-half-up would break.
        assertEquals("0", 0.5.toFixed(0.0))
        assertEquals("2", 1.5.toFixed(0.0))   // even neighbor of 1.5
        assertEquals("2", 2.5.toFixed(0.0))   // banker's rounds half-to-even (2 is even neighbor of 2.5)
    }

    @Test
    fun toFixed_negative() {
        assertEquals("-3.14", (-3.14).toFixed(2.0))
    }

    // ----- String.toDoubleOrNaN -----

    @Test
    fun toDoubleOrNaN_garbage_returns_nan() {
        assertTrue("not a number".toDoubleOrNaN().isNaN())
        assertTrue("".toDoubleOrNaN().isNaN())
    }

    @Test
    fun toDoubleOrNaN_valid_returns_double() {
        assertEquals(3.14, "3.14".toDoubleOrNaN())
        assertEquals(-7.0, "-7".toDoubleOrNaN())
        assertEquals(1.0E-5, "1.0E-5".toDoubleOrNaN())
    }

    @Test
    fun toDoubleOrNaN_trims_whitespace() {
        assertEquals(42.0, "  42  ".toDoubleOrNaN())
    }

    // ----- UByteArray.decodeToString -----

    @Test
    fun decodeToString_utf8_default() {
        // "abc" in UTF-8.
        val bytes = ubyteArrayOf(0x61.toUByte(), 0x62.toUByte(), 0x63.toUByte())
        assertEquals("abc", bytes.decodeToString("utf-8"))
    }

    @Test
    fun decodeToString_utf16le_round_trips() {
        // "AB" in UTF-16LE: 0x41 0x00 0x42 0x00
        val bytes = ubyteArrayOf(
            0x41.toUByte(), 0x00.toUByte(),
            0x42.toUByte(), 0x00.toUByte(),
        )
        assertEquals("AB", bytes.decodeToString("utf-16le"))
    }

    @Test
    fun decodeToString_utf16le_handles_high_codepoint() {
        // U+00E9 = 0xE9 0x00 LE
        val bytes = ubyteArrayOf(0xE9.toUByte(), 0x00.toUByte())
        assertEquals("é", bytes.decodeToString("utf-16le"))
    }

    @Test
    fun decodeToString_latin1_preserves_high_bytes() {
        // 0x80-0xFF map to U+0080-U+00FF in Latin-1.
        val bytes = ubyteArrayOf(0x80.toUByte(), 0xFF.toUByte())
        val s = bytes.decodeToString("latin1")
        assertEquals(2, s.length)
        assertEquals(0x80.toChar(), s[0])
        assertEquals(0xFF.toChar(), s[1])
    }

    @Test
    fun decodeToString_normalizes_encoding_name() {
        // ISO-8859-1 == latin1 == Windows-1252 (per the impl)
        val bytes = ubyteArrayOf(0xC0.toUByte())
        val a = bytes.decodeToString("ISO-8859-1")
        val b = bytes.decodeToString("latin1")
        val c = bytes.decodeToString("Windows-1252")
        assertEquals(a, b)
        assertEquals(b, c)
    }

    // ----- Performance.now -----

    @Test
    fun performance_now_is_monotonic() {
        val t0 = Globals.performance.now()
        var burn = 0
        repeat(10_000) { burn += it }
        val t1 = Globals.performance.now()
        assertTrue(t1 >= t0, "performance.now() not monotonic: t0=$t0 t1=$t1")
    }

    // ----- Globals.console exists -----

    @Test
    fun console_singleton_exists() {
        assertNotNull(Globals.console)
    }
}
