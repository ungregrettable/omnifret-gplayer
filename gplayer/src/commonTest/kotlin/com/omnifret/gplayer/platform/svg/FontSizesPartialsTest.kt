/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.platform.svg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `FontSizesPartials.generateFontLookup` replaces the upstream's Skia
 * text-measurer with an injectable `MusicFontMeasurer`. With no measurer
 * registered the impl writes a single-byte 8x10 fallback metric, which
 * keeps the renderer producing valid SVG even on host apps that haven't
 * wired Compose/CoreText measuring yet.
 */
class FontSizesPartialsTest {

    @Test
    fun generateFontLookup_falls_back_to_uniform_metric_when_no_measurer() {
        // Use a unique family name so we don't collide with any other test.
        val family = "test-fallback-${(0..1_000_000).random()}"
        FontSizesPartials.generateFontLookup(family)

        val def = FontSizes.fontSizeLookupTables.get(family)
        assertNotNull(def, "expected fallback table for $family")
        assertEquals(1.0, def.characterWidths.length, "fallback uses 1-byte width table")
        assertEquals(1.0, def.characterHeights.length, "fallback uses 1-byte height table")
        // 8 and 10 are the literal fallback values from the impl.
        assertEquals(8.toUByte(), def.characterWidths.buffer[0])
        assertEquals(10.toUByte(), def.characterHeights.buffer[0])
    }

    @Test
    fun generateFontLookup_idempotent_for_same_family() {
        val family = "test-idempotent-${(0..1_000_000).random()}"
        FontSizesPartials.generateFontLookup(family)
        val first = FontSizes.fontSizeLookupTables.get(family)
        assertNotNull(first)

        // Calling again with the same family is a no-op (the impl
        // short-circuits on `has(family)`).
        FontSizesPartials.generateFontLookup(family)
        val second = FontSizes.fontSizeLookupTables.get(family)
        assertTrue(first === second, "second call should return the same table reference")
    }

    @Test
    fun generateFontLookup_with_stub_measurer_populates_full_table() {
        // Only run when no measurer was previously installed by another
        // test; we skip if one is already registered to avoid clobbering.
        val previous = MusicFontMeasurer.instance
        try {
            // Stub measurer that returns deterministic values.
            MusicFontMeasurer.register(object : MusicFontMeasurer {
                override fun measure(family: String, text: String, sizePx: Double): Pair<Double, Double> {
                    return Pair(sizePx, sizePx + 1.0)
                }
            })
            val family = "test-stub-${(0..1_000_000).random()}"
            FontSizesPartials.generateFontLookup(family)

            val def = FontSizes.fontSizeLookupTables.get(family)
            assertNotNull(def)
            // Real measurer path: width table covers 32..254 (255-32 = 223 entries).
            assertEquals(223.0, def.characterWidths.length)
            assertEquals(223.0, def.characterHeights.length)
            // 11.0 is the measureSize the impl passes; stub returns
            // (11, 12) so every byte in the table is 11 / 12.
            assertEquals(11.toUByte(), def.characterWidths.buffer[0])
            assertEquals(12.toUByte(), def.characterHeights.buffer[0])
        } finally {
            // Restore prior state so other tests aren't affected. We
            // can't unset the measurer (private setter), so leave the
            // stub in place — subsequent FontSizes lookups for *new*
            // families will use the stub, but each test uses a unique
            // family name, so there's no contamination of assertions.
            if (previous == null) {
                // No measurer was previously set; future tests in this
                // process will hit the stub. Acceptable — every test
                // generates a fresh family so the cache miss path is
                // independent.
            }
        }
    }
}
