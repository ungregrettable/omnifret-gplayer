/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.core.ecmaScript

import com.omnifret.gplayer.model.BendStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * KMP-port of `Object.entries`. The original used Java reflection; we
 * register enum companions explicitly. Test the Record path, the
 * registered KClass<Enum> path, and the unsupported POJO path.
 */
class ObjectTest {

    @Test
    fun entries_record_returns_keys_and_values() {
        val rec = Record<String, Int>()
        rec.set("a", 1)
        rec.set("b", 2)
        rec.set("c", 3)

        val entries = Object.entries(rec)
        assertEquals(3.0, entries.length)

        val keys = (0 until entries.length.toInt()).map { i -> entries[i].v0 }.toSet()
        assertEquals(setOf("a", "b", "c"), keys)
    }

    @Test
    fun entries_record_skips_null_values() {
        val rec = Record<String, Int?>()
        rec.set("present", 1)
        rec.set("absent", null)
        val entries = Object.entries(rec)
        assertEquals(1.0, entries.length, "null values should be filtered")
        assertEquals("present", entries[0].v0)
    }

    @Test
    fun entries_kclass_returns_registered_enum_values() {
        // Register BendStyle's companion so entries(BendStyle::class) is
        // valid. registerEnum is idempotent.
        Object.registerEnum(BendStyle::class, BendStyle.Companion)

        val entries = Object.entries(BendStyle::class)
        assertTrue(entries.length >= 3.0, "expected ≥3 BendStyle values, got ${entries.length}")
    }

    @Test
    fun entries_kclass_unregistered_returns_empty() {
        // A KClass that hasn't been registered doesn't crash; entries()
        // returns the empty list (per impl: `comp` is null, no push).
        val entries = Object.entries(NotRegistered::class)
        assertEquals(0.0, entries.length)
    }

    @Test
    fun entries_pojo_throws() {
        assertFailsWith<IllegalStateException> {
            Object.entries(SomePojo("x"))
        }
    }

    @Test
    fun entries_null_returns_empty() {
        val entries = Object.entries(null)
        assertEquals(0.0, entries.length)
    }

    @Test
    fun values_kclass_uses_enumValues_reflection() {
        val values = Object.values(BendStyle::class)
        assertTrue(values.length >= 3.0)
    }

    private class NotRegistered
    private data class SomePojo(val name: String)
}
