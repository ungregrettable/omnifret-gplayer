/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.core

import com.omnifret.gplayer.GPlayerError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private enum class Color { Red, Green, Blue }

class TypeHelperTest {

    // ----- parseEnum -----

    @Test
    fun parseEnum_case_insensitive_match() {
        assertEquals(Color.Red, TypeHelper.parseEnum("Red", Color::class))
        assertEquals(Color.Green, TypeHelper.parseEnum("green", Color::class))
        assertEquals(Color.Blue, TypeHelper.parseEnum("BLUE", Color::class))
    }

    @Test
    fun parseEnum_throws_on_unknown_value() {
        assertFailsWith<GPlayerError> {
            TypeHelper.parseEnum("Purple", Color::class)
        }
    }

    @Test
    fun parseEnum_array_overload() {
        assertEquals(Color.Red, TypeHelper.parseEnum("red", arrayOf(Color.Red, Color.Green, Color.Blue)))
    }

    // ----- isTruthy -----

    @Test
    fun isTruthy_string() {
        assertFalse(TypeHelper.isTruthy(null as String?))
        assertFalse(TypeHelper.isTruthy(""))
        assertTrue(TypeHelper.isTruthy("hello"))
        assertTrue(TypeHelper.isTruthy(" "))
    }

    @Test
    fun isTruthy_boolean() {
        assertFalse(TypeHelper.isTruthy(null as Boolean?))
        assertFalse(TypeHelper.isTruthy(false))
        assertTrue(TypeHelper.isTruthy(true))
    }

    @Test
    fun isTruthy_double() {
        assertFalse(TypeHelper.isTruthy(0.0))
        assertFalse(TypeHelper.isTruthy(Double.NaN))
        assertTrue(TypeHelper.isTruthy(1.0))
        assertTrue(TypeHelper.isTruthy(-1.0))
        assertTrue(TypeHelper.isTruthy(0.0001))
    }

    @Test
    fun isTruthy_any_null_only() {
        assertFalse(TypeHelper.isTruthy(null as Any?))
        assertTrue(TypeHelper.isTruthy("hello" as Any?))
        assertTrue(TypeHelper.isTruthy(0 as Any?))    // raw 0 is non-null → truthy via Any overload
        assertTrue(TypeHelper.isTruthy(false as Any?)) // raw false is non-null → truthy via Any overload
    }

    // ----- typeOf -----

    @Test
    fun typeOf_returns_js_style_strings() {
        assertEquals("string", TypeHelper.typeOf("hello"))
        assertEquals("boolean", TypeHelper.typeOf(true))
        assertEquals("number", TypeHelper.typeOf(42))
        assertEquals("number", TypeHelper.typeOf(42.5))
        assertEquals("number", TypeHelper.typeOf(42L))
        assertEquals("number", TypeHelper.typeOf(42.toShort()))
        assertEquals("undefined", TypeHelper.typeOf(null))
        assertEquals("object", TypeHelper.typeOf(listOf(1, 2, 3)))
    }

    @Test
    fun typeOf_iGPlayerEnum_is_number() {
        val e = object : IGPlayerEnum {
            override val value: Int = 7
        }
        assertEquals("number", TypeHelper.typeOf(e))
    }

    // ----- unknownToNumber -----

    @Test
    fun unknownToNumber_int() {
        assertEquals(42.0, TypeHelper.unknownToNumber(42))
    }

    @Test
    fun unknownToNumber_double_passthrough() {
        assertEquals(3.14, TypeHelper.unknownToNumber(3.14))
    }

    @Test
    fun unknownToNumber_iGPlayerEnum() {
        val e = object : IGPlayerEnum {
            override val value: Int = 7
        }
        assertEquals(7.0, TypeHelper.unknownToNumber(e))
    }

    @Test
    fun unknownToNumber_throws_on_null() {
        assertFailsWith<ClassCastException> {
            TypeHelper.unknownToNumber(null)
        }
    }

    @Test
    fun unknownToNumber_throws_on_unsupported_type() {
        assertFailsWith<ClassCastException> {
            TypeHelper.unknownToNumber("not a number")
        }
    }

    // ----- createRegex -----

    @Test
    fun createRegex_returns_working_regexp() {
        val r = TypeHelper.createRegex("\\d+", "g")
        assertEquals("X X X", r.replace("1 22 333", "X"))
    }
}
