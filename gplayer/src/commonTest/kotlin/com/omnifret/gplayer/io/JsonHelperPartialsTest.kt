/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.io

import com.omnifret.gplayer.GPlayerError
import com.omnifret.gplayer.collections.Map
import com.omnifret.gplayer.model.BendStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hand-rewritten KMP-port helpers used by serialization. The original
 * relied on `java.lang.reflect`-style enum lookup; we re-implement on
 * top of a typed `Map<String, Any?>` and explicit `enumValues<T>()`.
 * Tests cover both forEach overloads + the four parseEnum overloads.
 */
class JsonHelperPartialsTest {

    @Test
    fun forEach_invokes_callback_per_entry() {
        val m = Map<String, Any?>()
        m.set("a", 1)
        m.set("b", "x")
        m.set("c", true)

        val collected = mutableListOf<Pair<String, Any?>>()
        // Two forEach overloads exist (lambda → Unit, lambda → Boolean,
        // disambiguated only by return type). `collected += ...` returns
        // Boolean (MutableList.add), which trips the resolver — pin the
        // overload by giving the lambda a Unit return type.
        val cb: (Any?, String) -> Unit = { v, k -> collected += k to v }
        JsonHelperPartials.forEach(m, cb)
        assertEquals(3, collected.size)
        assertEquals(setOf("a", "b", "c"), collected.map { it.first }.toSet())
    }

    @Test
    fun forEach_no_op_for_non_map() {
        var called = false
        val cb: (Any?, String) -> Unit = { _, _ -> called = true }
        JsonHelperPartials.forEach(null, cb)
        JsonHelperPartials.forEach("not a map", cb)
        assertTrue(!called, "forEach should be a no-op for null / non-Map inputs")
    }

    @Test
    fun getValue_returns_null_for_unknown_key() {
        val m = Map<String, Any?>()
        m.set("present", 42)
        assertEquals(42, JsonHelperPartials.getValue(m, "present"))
        assertNull(JsonHelperPartials.getValue(m, "absent"))
    }

    @Test
    fun getValue_returns_null_for_non_map() {
        assertNull(JsonHelperPartials.getValue(null, "k"))
        assertNull(JsonHelperPartials.getValue("not a map", "k"))
    }

    // ----- parseEnum (String, Array<T>) -----

    @Test
    fun parseEnum_string_case_insensitive_match() {
        assertEquals(
            BendStyle.Default,
            JsonHelperPartials.parseEnum("default", arrayOf(*BendStyle.values))
        )
        assertEquals(
            BendStyle.Gradual,
            JsonHelperPartials.parseEnum("GRADUAL", arrayOf(*BendStyle.values))
        )
    }

    @Test
    fun parseEnum_string_numeric_value_match() {
        // BendStyle.Default has value 0; parseEnum should match "0" → Default.
        val result = JsonHelperPartials.parseEnum("0", arrayOf(*BendStyle.values))
        assertEquals(BendStyle.Default, result)
    }

    @Test
    fun parseEnum_string_unknown_returns_null() {
        assertNull(
            JsonHelperPartials.parseEnum("Mystery", arrayOf(*BendStyle.values))
        )
    }

    // ----- parseEnum (Int, Array<T>) -----

    @Test
    fun parseEnum_int_returns_value_match() {
        assertEquals(
            BendStyle.Default,
            JsonHelperPartials.parseEnum(0, arrayOf(*BendStyle.values))
        )
        assertEquals(
            BendStyle.Gradual,
            JsonHelperPartials.parseEnum(1, arrayOf(*BendStyle.values))
        )
    }

    @Test
    fun parseEnum_int_unknown_returns_null() {
        assertNull(JsonHelperPartials.parseEnum(999, arrayOf(*BendStyle.values)))
    }

    // ----- parseEnumExact (Int, Array<T>) -----

    @Test
    fun parseEnumExact_int_matches_by_value() {
        assertEquals(
            BendStyle.Default,
            JsonHelperPartials.parseEnumExact(0, arrayOf(*BendStyle.values))
        )
    }

    @Test
    fun parseEnumExact_string_matches_by_name() {
        assertEquals(
            BendStyle.Default,
            JsonHelperPartials.parseEnumExact("default", BendStyle::class)
        )
    }

    @Test
    fun parseEnumExact_string_unknown_returns_null() {
        assertNull(JsonHelperPartials.parseEnumExact("Mystery", BendStyle::class))
    }

    // ----- parseEnum (Any?, KClass<T>) — top-level dispatcher -----

    @Test
    fun parseEnum_any_dispatches_on_runtime_type() {
        // String dispatch
        assertEquals(
            BendStyle.Default,
            JsonHelperPartials.parseEnum("default", BendStyle::class)
        )
        // Int dispatch (value 1 → Gradual)
        assertEquals(
            BendStyle.Gradual,
            JsonHelperPartials.parseEnum(1, BendStyle::class)
        )
        // Double dispatch
        assertEquals(
            BendStyle.Gradual,
            JsonHelperPartials.parseEnum(1.0, BendStyle::class)
        )
        // Already an instance: passthrough.
        assertEquals(
            BendStyle.Fast,
            JsonHelperPartials.parseEnum(BendStyle.Fast, BendStyle::class)
        )
        // Null: null.
        assertNull(JsonHelperPartials.parseEnum(null, BendStyle::class))
    }

    @Test
    fun parseEnum_any_unknown_runtime_type_throws() {
        // List<*> isn't String/Int/Double/T/null — should throw GPlayerError.
        assertFailsWith<GPlayerError> {
            JsonHelperPartials.parseEnum(listOf(1, 2, 3), BendStyle::class)
        }
    }
}
