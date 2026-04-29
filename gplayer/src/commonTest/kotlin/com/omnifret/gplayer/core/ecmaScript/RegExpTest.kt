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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * KMP-port of alphaTab's java.util.regex.Pattern wrapper, on top of
 * kotlin.text.Regex. Test the JS-flag semantics (g/i/m/s) separately
 * so a flag mapping bug in the constructor is caught at the smallest
 * possible scope.
 */
class RegExpTest {

    @Test
    fun exec_matches_simple_pattern() {
        val r = RegExp("abc")
        assertTrue(r.exec("abc"))
        assertTrue(r.exec("xxabcxx"))
        assertFalse(r.exec("xyz"))
    }

    @Test
    fun i_flag_matches_case_insensitively() {
        assertTrue(RegExp("HELLO", "i").exec("hello world"))
        assertFalse(RegExp("HELLO").exec("hello world"))
    }

    @Test
    fun s_flag_makes_dot_match_newlines() {
        assertTrue(RegExp("a.b", "s").exec("a\nb"))
        assertFalse(RegExp("a.b").exec("a\nb"))
    }

    @Test
    fun m_flag_makes_anchors_match_per_line() {
        assertTrue(RegExp("^bar", "m").exec("foo\nbar"))
        assertFalse(RegExp("^bar").exec("foo\nbar"))
    }

    @Test
    fun replace_string_first_occurrence_only_without_g_flag() {
        val r = RegExp("a")
        assertEquals("Xbcabc", r.replace("abcabc", "X"))
    }

    @Test
    fun replace_string_all_occurrences_with_g_flag() {
        val r = RegExp("a", "g")
        assertEquals("XbcXbc", r.replace("abcabc", "X"))
    }

    @Test
    fun replace_lambda_passes_capture_group() {
        val r = RegExp("a(\\d)", "g")
        val out = r.replace("a1 a2 a3") { _, g1 -> "[$g1]" }
        assertEquals("[1] [2] [3]", out)
    }

    @Test
    fun replace_lambda_match_only_for_no_capture_group() {
        val r = RegExp("\\d+", "g")
        val out = r.replace("a1 b22 c333") { match -> "<$match>" }
        assertEquals("a<1> b<22> c<333>", out)
    }

    @Test
    fun replace_string_escapes_dollar_one_in_replacement() {
        // The Kotlin replace() interprets `$1` as a back-reference.
        // The KMP wrapper escapes the replacement so the literal "$1"
        // appears in the output.
        val r = RegExp("foo")
        val out = r.replace("foo", "\$1")
        assertEquals("\$1", out)
    }

    @Test
    fun non_global_lambda_replace_only_replaces_first() {
        val r = RegExp("\\d")
        val out = r.replace("a1b2c3") { match -> "[$match]" }
        assertEquals("a[1]b2c3", out)
    }

    @Test
    fun non_global_no_match_returns_input_unchanged() {
        val r = RegExp("xyz")
        assertEquals("abcdef", r.replace("abcdef") { _ -> "X" })
    }

    @Test
    fun multiple_flags_combine() {
        val r = RegExp("HELLO\\nWORLD", "is")
        assertTrue(r.exec("hello\nworld"))
    }
}
