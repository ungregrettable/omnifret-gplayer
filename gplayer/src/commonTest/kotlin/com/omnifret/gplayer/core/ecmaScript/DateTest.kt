/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

package com.omnifret.gplayer.core.ecmaScript

import kotlin.test.Test
import kotlin.test.assertTrue

class DateTest {

    @Test
    fun now_is_monotonic_across_consecutive_calls() {
        val t0 = Date.now()
        // Spin briefly so the second call has elapsed time even on fast
        // CPUs where the time source resolution is microseconds.
        var burn = 0
        repeat(10_000) { burn += it }
        val t1 = Date.now()
        assertTrue(t1 >= t0, "Date.now() not monotonic: t0=$t0 t1=$t1 (burn=$burn)")
    }

    @Test
    fun now_returns_milliseconds_since_origin() {
        // The contract is "milliseconds elapsed since origin", which for a
        // freshly-loaded test class means a small positive number.
        val t = Date.now()
        assertTrue(t >= 0.0, "Date.now() should be non-negative, got $t")
    }
}
