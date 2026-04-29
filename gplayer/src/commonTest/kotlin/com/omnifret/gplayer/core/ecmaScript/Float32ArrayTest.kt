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
import kotlin.test.assertTrue

/**
 * Float32Array is the audio sample buffer the synth/render code passes
 * around. The hand-rewritten `buffer` getter / `ArrayBuffer` ctor /
 * `subarray` operations are the most error-prone surface (manual
 * IEEE-754 little-endian encode by hand). Tests focus on round-tripping
 * data through buffer ↔ array conversions.
 */
class Float32ArrayTest {

    @Test
    fun buffer_round_trips_via_arraybuffer_constructor() {
        val original = Float32Array(floatArrayOf(0.0f, 1.0f, -1.0f, 3.14f))
        val buffer = original.buffer
        val rebuilt = Float32Array(buffer)
        assertEquals(original.length, rebuilt.length)
        for (i in 0 until original.data.size) {
            assertEquals(original.data[i], rebuilt.data[i], "index $i")
        }
    }

    @Test
    fun buffer_size_is_4x_element_count() {
        val arr = Float32Array(floatArrayOf(1.0f, 2.0f, 3.0f))
        assertEquals(12, arr.buffer.size, "3 floats × 4 bytes")
    }

    @Test
    fun subarray_returns_correct_slice() {
        val arr = Float32Array(floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f))
        val sub = arr.subarray(1.0, 4.0)
        assertEquals(3.0, sub.length)
        assertEquals(2.0, sub[0])
        assertEquals(3.0, sub[1])
        assertEquals(4.0, sub[2])
    }

    @Test
    fun set_overwrites_in_place() {
        val arr = Float32Array(floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f))
        val src = Float32Array(floatArrayOf(7.0f, 8.0f))
        arr.set(src, 2.0)
        assertEquals(0.0, arr[0])
        assertEquals(0.0, arr[1])
        assertEquals(7.0, arr[2])
        assertEquals(8.0, arr[3])
        assertEquals(0.0, arr[4])
    }

    @Test
    fun fill_writes_value_in_range() {
        val arr = Float32Array(floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f))
        arr.fill(9.5, 1.0, 3.0)
        assertEquals(0.0, arr[0])
        assertEquals(9.5, arr[1])
        assertEquals(9.5, arr[2])
        assertEquals(0.0, arr[3])
    }

    @Test
    fun iterator_yields_all_values_in_order() {
        val arr = Float32Array(floatArrayOf(1.0f, 2.0f, 3.0f))
        val collected = mutableListOf<Float>()
        for (v in arr) collected += v
        assertEquals(listOf(1.0f, 2.0f, 3.0f), collected)
    }

    @Test
    fun length_uses_size_constructor() {
        val arr = Float32Array(7.0)
        assertEquals(7.0, arr.length)
        // Default-initialized to zero.
        for (i in 0 until 7) assertEquals(0.0, arr[i])
    }

    @Test
    fun set_via_index_operator() {
        val arr = Float32Array(3.0)
        arr[0] = 1.0
        arr[1] = -2.5
        arr[2] = 100.0
        assertEquals(1.0, arr[0])
        assertEquals(-2.5, arr[1])
        assertEquals(100.0, arr[2])
    }

    @Test
    fun buffer_encodes_known_float_LE() {
        // 1.0f → IEEE-754 0x3F800000 → LE bytes 00 00 80 3F
        val arr = Float32Array(floatArrayOf(1.0f))
        val bytes = arr.buffer.asByteArray()
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0x00.toByte(), bytes[1])
        assertEquals(0x80.toByte(), bytes[2])
        assertEquals(0x3F.toByte(), bytes[3])
    }

    @Test
    fun byteLength_matches_size_in_bytes() {
        val arr = Float32Array(floatArrayOf(1.0f, 2.0f))
        assertEquals(8.0, arr.byteLength)
    }

    @Test
    fun byteOffset_is_zero_for_array_constructed() {
        val arr = Float32Array(floatArrayOf(1.0f))
        assertEquals(0.0, arr.byteOffset)
    }

    @Test
    fun supports_negative_values_via_round_trip() {
        val original = floatArrayOf(-3.14f, Float.NEGATIVE_INFINITY, Float.MIN_VALUE)
        val arr = Float32Array(original)
        val rebuilt = Float32Array(arr.buffer)
        for (i in original.indices) {
            assertEquals(original[i], rebuilt.data[i], "index $i")
        }
    }

    @Test
    fun nan_round_trips() {
        val arr = Float32Array(floatArrayOf(Float.NaN))
        val rebuilt = Float32Array(arr.buffer)
        assertTrue(rebuilt.data[0].isNaN())
    }
}
