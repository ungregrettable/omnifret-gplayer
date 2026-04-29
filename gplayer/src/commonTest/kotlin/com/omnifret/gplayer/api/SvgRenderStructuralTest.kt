/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.api

import com.omnifret.gplayer.test.parseFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Structural assertions on [ScoreSvgRenderer.render]'s output. We don't
 * compare pixel-perfect SVG snapshots — alphaTab maintains a visual-
 * regression suite upstream and our SVG drift is mostly engraving
 * tweaks, not breakage. Instead we assert: every chunk parses as SVG,
 * total dimensions are positive, and every master bar is covered by
 * exactly one chunk's [firstBarIndex..lastBarIndex] range.
 */
class SvgRenderStructuralTest {

    @Test
    fun gp5_notes_renders_at_least_one_chunk() {
        val score = parseFixture("guitarpro5/notes.gp5")
        val result = ScoreSvgRenderer(score).render()
        assertTrue(result.chunks.isNotEmpty(), "no chunks emitted")
    }

    @Test
    fun every_chunk_starts_with_svg_tag() {
        val score = parseFixture("guitarpro5/notes.gp5")
        val result = ScoreSvgRenderer(score).render()
        for ((i, chunk) in result.chunks.withIndex()) {
            val trimmed = chunk.svg.trimStart()
            assertTrue(
                trimmed.startsWith("<svg"),
                "chunk[$i] does not start with <svg> tag; first 80 chars: ${trimmed.take(80)}",
            )
        }
    }

    @Test
    fun total_dimensions_are_positive() {
        val score = parseFixture("guitarpro5/notes.gp5")
        val result = ScoreSvgRenderer(score).render()
        assertTrue(result.totalWidthPx > 0.0, "totalWidthPx ${result.totalWidthPx}")
        assertTrue(result.totalHeightPx > 0.0, "totalHeightPx ${result.totalHeightPx}")
    }

    @Test
    fun every_chunk_has_positive_dimensions() {
        val score = parseFixture("guitarpro5/notes.gp5")
        val result = ScoreSvgRenderer(score).render()
        for ((i, chunk) in result.chunks.withIndex()) {
            assertTrue(chunk.widthPx > 0.0, "chunk[$i] width ${chunk.widthPx}")
            assertTrue(chunk.heightPx > 0.0, "chunk[$i] height ${chunk.heightPx}")
        }
    }

    @Test
    fun chunks_cover_every_master_bar_index() {
        // Each master bar should be covered by exactly one chunk's
        // [firstBarIndex..lastBarIndex] range. Catches gaps or overlaps
        // in the layout engine's bar packing.
        val score = parseFixture("guitarpro5/notes.gp5")
        val result = ScoreSvgRenderer(score).render()
        val barCount = score.masterBars.length.toInt()
        val coverage = IntArray(barCount)
        for (chunk in result.chunks) {
            for (b in chunk.firstBarIndex..chunk.lastBarIndex) {
                if (b in 0 until barCount) coverage[b]++
            }
        }
        for (b in 0 until barCount) {
            assertEquals(
                1,
                coverage[b],
                "bar $b covered ${coverage[b]} times (expected 1) — gap or overlap in chunking",
            )
        }
    }

    @Test
    fun custom_width_changes_total_width() {
        val score = parseFixture("guitarpro5/notes.gp5")
        val narrow = ScoreSvgRenderer(score, widthPx = 500.0).render()
        val wide = ScoreSvgRenderer(score, widthPx = 1500.0).render()
        // The renderer reflows; at minimum, the total widths shouldn't
        // be identical, since reflow uses widthPx as the upper bound.
        // (We don't assert ordering — wider can still produce smaller
        // totalWidth if the score fits within both.)
        assertTrue(
            narrow.totalWidthPx != wide.totalWidthPx ||
                narrow.totalHeightPx != wide.totalHeightPx,
            "rendering at 500px vs 1500px produced identical layout",
        )
    }

    @Test
    fun gp4_renders_to_svg() {
        val score = parseFixture("guitarpro4/notes.gp4")
        val result = ScoreSvgRenderer(score).render()
        assertTrue(result.chunks.isNotEmpty())
        assertTrue(result.totalWidthPx > 0.0)
    }

    @Test
    fun gp7_renders_to_svg() {
        val score = parseFixture("guitarpro7/notes.gp")
        val result = ScoreSvgRenderer(score).render()
        assertTrue(result.chunks.isNotEmpty())
        assertTrue(result.totalWidthPx > 0.0)
    }

    @Test
    fun musicxml_renders_to_svg() {
        val score = parseFixture("musicxml4/partwise-basic.xml")
        val result = ScoreSvgRenderer(score).render()
        assertTrue(result.chunks.isNotEmpty())
    }

    @Test
    fun two_renders_of_same_score_produce_identical_chunk_count() {
        // Determinism: rendering should be repeatable.
        val score = parseFixture("guitarpro5/notes.gp5")
        val first = ScoreSvgRenderer(score).render()
        val second = ScoreSvgRenderer(score).render()
        assertEquals(first.chunks.size, second.chunks.size)
        assertEquals(first.totalWidthPx, second.totalWidthPx)
        assertEquals(first.totalHeightPx, second.totalHeightPx)
    }
}
