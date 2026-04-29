/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

package com.omnifret.gplayer.importer.gplayertex

import com.omnifret.gplayer.model.BendStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * KMP-port replacement for the upstream `java.lang.reflect`-based enum
 * lookup. The KMP impl requires explicit registration; tests verify
 * registration + lookup on a real enum and the failure path when an
 * enum is queried before registration.
 */
class GPlayerTex1EnumMappingsPartialsTest {

    @Test
    fun register_then_toEnum_returns_correct_value() {
        GPlayerTex1EnumMappingsPartials.register(BendStyle::class, BendStyle.Companion)
        val v: BendStyle = GPlayerTex1EnumMappingsPartials._toEnum(
            BendStyle::class,
            BendStyle.Default.value.toDouble()
        )
        assertEquals(BendStyle.Default, v)

        val v2: BendStyle = GPlayerTex1EnumMappingsPartials._toEnum(
            BendStyle::class,
            BendStyle.Fast.value.toDouble()
        )
        assertEquals(BendStyle.Fast, v2)
    }

    @Test
    fun toEnum_throws_for_unregistered_type() {
        assertFailsWith<IllegalArgumentException> {
            GPlayerTex1EnumMappingsPartials._toEnum<BendStyle>(NotRegistered::class, 0.0)
        }
    }

    @Test
    fun register_replaces_existing_factory() {
        GPlayerTex1EnumMappingsPartials.register(BendStyle::class, BendStyle.Companion)
        // Re-register; should not throw, lookup still works.
        GPlayerTex1EnumMappingsPartials.register(BendStyle::class, BendStyle.Companion)
        val v: BendStyle = GPlayerTex1EnumMappingsPartials._toEnum(
            BendStyle::class,
            0.0
        )
        assertEquals(BendStyle.Default, v)
    }

    private class NotRegistered
}
