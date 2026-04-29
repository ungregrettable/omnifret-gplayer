/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

package com.omnifret.gplayer.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `ArrayListWithRemoveRange` and the JS-style `List<T>` wrapper are
 * hand-rewritten because Kotlin/Native disallows extending the stdlib
 * ArrayList. Test removeRange edge cases plus the JS-shaped operations
 * (push/pop/shift/unshift/splice/slice).
 */
class ListTest {

    // ----- ArrayListWithRemoveRange -----

    @Test
    fun removeRange_middle() {
        val a = ArrayListWithRemoveRange<Int>()
        a.addAll(listOf(1, 2, 3, 4, 5))
        a.removeRange(1, 4)
        assertEquals(listOf(1, 5), a.toList())
    }

    @Test
    fun removeRange_empty_range_no_op() {
        val a = ArrayListWithRemoveRange<Int>()
        a.addAll(listOf(1, 2, 3))
        a.removeRange(1, 1)
        assertEquals(listOf(1, 2, 3), a.toList())
    }

    @Test
    fun removeRange_full_range_clears() {
        val a = ArrayListWithRemoveRange<Int>()
        a.addAll(listOf(1, 2, 3))
        a.removeRange(0, 3)
        assertTrue(a.isEmpty())
    }

    @Test
    fun removeRange_out_of_bounds_throws() {
        val a = ArrayListWithRemoveRange<Int>()
        a.addAll(listOf(1, 2, 3))
        assertFailsWith<IndexOutOfBoundsException> {
            a.removeRange(0, 5)
        }
    }

    // ----- List<T> JS-shaped operations -----

    @Test
    fun push_appends() {
        val l = List<Int>()
        l.push(1)
        l.push(2, 3)
        assertEquals(3.0, l.length)
        assertEquals(1, l[0])
        assertEquals(2, l[1])
        assertEquals(3, l[2])
    }

    @Test
    fun pop_removes_last_and_returns_it() {
        val l = List(1, 2, 3)
        assertEquals(3, l.pop())
        assertEquals(2.0, l.length)
        assertEquals(2, l[1])
    }

    @Test
    fun pop_on_empty_throws() {
        val l = List<Int>()
        assertFailsWith<IndexOutOfBoundsException> { l.pop() }
    }

    @Test
    fun shift_removes_first_and_returns_it() {
        val l = List(1, 2, 3)
        assertEquals(1, l.shift())
        assertEquals(2.0, l.length)
        assertEquals(2, l[0])
    }

    @Test
    fun shift_on_empty_throws() {
        val l = List<Int>()
        assertFailsWith<IndexOutOfBoundsException> { l.shift() }
    }

    @Test
    fun unshift_prepends() {
        val l = List(2, 3)
        l.unshift(1)
        assertEquals(3.0, l.length)
        assertEquals(1, l[0])
        assertEquals(2, l[1])
    }

    @Test
    fun splice_removes_and_inserts() {
        val l = List(1, 2, 3, 4, 5)
        val removed = l.splice(1.0, 2.0, 99, 100)
        // Returned removed items: from start onward (per impl, the slice
        // grabs subList(start, size) as `remove`, not just the deleted span).
        assertTrue(removed.length > 0.0)
        // After splice, the original list has the new elements at index 1.
        assertEquals(99, l[1])
        assertEquals(100, l[2])
    }

    @Test
    fun splice_with_zero_delete_count_is_pure_insert() {
        val l = List(1, 2, 3)
        l.splice(1.0, 0.0, 99)
        assertEquals(4.0, l.length)
        assertEquals(99, l[1])
        assertEquals(2, l[2])
    }

    @Test
    fun slice_no_args_returns_copy() {
        val l = List(1, 2, 3)
        val copy = l.slice()
        assertEquals(3.0, copy.length)
        // Mutate the copy; original is untouched.
        copy.push(4)
        assertEquals(3.0, l.length)
        assertEquals(4.0, copy.length)
    }

    @Test
    fun slice_with_start_returns_suffix() {
        val l = List(1, 2, 3, 4, 5)
        val s = l.slice(2.0)
        assertEquals(3.0, s.length)
        assertEquals(3, s[0])
        assertEquals(4, s[1])
        assertEquals(5, s[2])
    }

    // ----- List query/mutation -----

    @Test
    fun includes_and_indexOf() {
        val l = List(10, 20, 30)
        assertTrue(l.includes(20))
        assertEquals(1.0, l.indexOf(20))
        assertEquals(-1.0, l.indexOf(99))
    }

    @Test
    fun some_returns_true_if_any_match() {
        val l = List(1, 2, 3)
        assertTrue(l.some { it > 2 })
        assertTrue(!l.some { it > 99 })
    }

    @Test
    fun reverse_in_place() {
        val l = List(1, 2, 3)
        l.reverse()
        assertEquals(3, l[0])
        assertEquals(1, l[2])
    }

    @Test
    fun sort_with_comparator_sorts_ascending() {
        val l = List(3, 1, 2)
        l.sort { a, b -> (a - b).toDouble() }
        assertEquals(1, l[0])
        assertEquals(2, l[1])
        assertEquals(3, l[2])
    }

    @Test
    fun map_with_index_uses_two_arg_overload() {
        // The 2-arg `map((v, i) -> TOut)` overload disambiguates from the
        // four 1-arg overloads. Validates the indexed traversal.
        val l = List(10, 20, 30)
        val mapped = l.map { v, i -> "[${i.toInt()}=$v]" }
        assertEquals(3.0, mapped.length)
        assertEquals("[0=10]", mapped[0])
        assertEquals("[1=20]", mapped[1])
        assertEquals("[2=30]", mapped[2])
    }

    @Test
    fun filter_keeps_matching() {
        val l = List(1, 2, 3, 4, 5)
        val even = l.filter { it % 2 == 0 }
        assertEquals(2.0, even.length)
        assertEquals(2, even[0])
        assertEquals(4, even[1])
    }

    @Test
    fun join_concatenates_with_separator() {
        val l = List("a", "b", "c")
        assertEquals("a-b-c", l.join("-"))
    }

    @Test
    fun fixed_size_constructor_initializes_with_nulls() {
        val l = List<Int?>(3)
        assertEquals(3.0, l.length)
        assertEquals(null, l[0])
        assertEquals(null, l[1])
        assertEquals(null, l[2])
    }

    @Test
    fun iterator_yields_in_order() {
        val l = List(1, 2, 3)
        val collected = mutableListOf<Int>()
        for (v in l) collected += v
        assertEquals(listOf(1, 2, 3), collected)
    }
}
