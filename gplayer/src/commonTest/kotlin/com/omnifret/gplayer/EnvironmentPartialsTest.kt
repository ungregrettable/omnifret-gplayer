/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Hand-written KMP-port partials replacing the upstream Android-specific
 * `EnvironmentPartials.kt`. Tests cover the lambdas and exceptions that
 * the renderer/importer depend on.
 */
class EnvironmentPartialsTest {

    @Test
    fun printPlatformInfo_invokes_print_lambda_twice() {
        val collected = mutableListOf<String>()
        EnvironmentPartials._printPlatformInfo { msg -> collected += msg }
        assertEquals(2, collected.size, "expected exactly 2 print calls (Platform + Version)")
        assertTrue(collected[0].startsWith("Platform: "), "first call: ${collected[0]}")
        assertTrue(collected[1].startsWith("Version: "), "second call: ${collected[1]}")
        // Platform name and version are both non-empty.
        assertTrue(collected[0].length > "Platform: ".length)
        assertTrue(collected[1].length > "Version: ".length)
    }

    @Test
    fun quoteJsonString_escapes_quotes_and_backslashes() {
        // Forwards to platform.Json.quoteJsonString. Returns a fully-
        // quoted JSON string literal, including the outer quotes.
        assertEquals("\"\"", EnvironmentPartials.quoteJsonString(""))
        assertEquals("\"hello\"", EnvironmentPartials.quoteJsonString("hello"))
        // Embedded quote → escaped.
        val input = "say \"hi\""
        val out = EnvironmentPartials.quoteJsonString(input)
        assertTrue(out.startsWith("\""), out)
        assertTrue(out.endsWith("\""), out)
        assertTrue(out.contains("\\\""), "expected escaped quote in $out")
        // Embedded backslash → escaped.
        val withBackslash = EnvironmentPartials.quoteJsonString("a\\b")
        assertTrue(withBackslash.contains("\\\\"), "expected escaped backslash in $withBackslash")
    }

    @Test
    fun getGlobalWorkerScope_throws() {
        assertFailsWith<UnsupportedOperationException> {
            EnvironmentPartials.getGlobalWorkerScope<String>()
        }
    }

    @Test
    fun prepareForPostMessage_is_identity() {
        assertEquals(42, EnvironmentPartials.prepareForPostMessage(42))
        assertEquals("hello", EnvironmentPartials.prepareForPostMessage("hello"))
        val o = listOf(1, 2, 3)
        assertEquals(o, EnvironmentPartials.prepareForPostMessage(o))
    }

    @Test
    fun platformInfo_actuals_supply_non_empty_metadata() {
        // PlatformInfo is `expect`/`actual` — Android and iOS both fill
        // it. From commonTest we just assert non-empty values; the per-
        // platform format ("Android" / "iOS API X.Y") is checked in
        // androidUnitTest / iosTest.
        assertTrue(PlatformInfo.name.isNotEmpty(), "PlatformInfo.name empty")
        assertTrue(PlatformInfo.version.isNotEmpty(), "PlatformInfo.version empty")
    }
}
