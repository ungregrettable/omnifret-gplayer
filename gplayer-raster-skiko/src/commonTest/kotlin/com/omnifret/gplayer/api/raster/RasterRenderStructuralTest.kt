/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.api.raster

import com.omnifret.gplayer.raster.test.bravuraBytes
import com.omnifret.gplayer.raster.test.parseRasterFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Structural assertions on [ScoreRasterRenderer.render]'s output.
 * Mirrors `SvgRenderStructuralTest` from `:gplayer`. We don't compare
 * pixel-perfect snapshots here — that's a separate test ([RasterRenderSnapshotTest])
 * to keep golden churn isolated from structural regressions.
 */
class RasterRenderStructuralTest {

    @Test
    fun gp5_notes_renders_at_least_one_chunk() {
        val score = parseRasterFixture("guitarpro5/notes.gp5")
        val result = ScoreRasterRenderer(score, bravuraBytes).render()
        assertTrue(result.chunks.isNotEmpty(), "no chunks emitted")
    }

    @Test
    fun every_chunk_has_pixels_matching_dimensions() {
        val score = parseRasterFixture("guitarpro5/notes.gp5")
        val result = ScoreRasterRenderer(score, bravuraBytes).render()
        for ((i, chunk) in result.chunks.withIndex()) {
            assertEquals(
                chunk.pixelsWidth * chunk.pixelsHeight,
                chunk.pixels.size,
                "chunk[$i] pixel buffer size mismatch",
            )
            assertTrue(chunk.pixelsWidth > 0, "chunk[$i] pixelsWidth ${chunk.pixelsWidth}")
            assertTrue(chunk.pixelsHeight > 0, "chunk[$i] pixelsHeight ${chunk.pixelsHeight}")
            assertTrue(chunk.widthPx > 0, "chunk[$i] widthPx ${chunk.widthPx}")
            assertTrue(chunk.heightPx > 0, "chunk[$i] heightPx ${chunk.heightPx}")
        }
    }

    @Test
    fun total_dimensions_are_positive() {
        val score = parseRasterFixture("guitarpro5/notes.gp5")
        val result = ScoreRasterRenderer(score, bravuraBytes).render()
        assertTrue(result.totalWidthPx > 0, "totalWidthPx ${result.totalWidthPx}")
        assertTrue(result.totalHeightPx > 0, "totalHeightPx ${result.totalHeightPx}")
    }

    @Test
    fun chunks_cover_every_master_bar_index() {
        val score = parseRasterFixture("guitarpro5/notes.gp5")
        val result = ScoreRasterRenderer(score, bravuraBytes).render()
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
    fun bar_x_offsets_populated_for_score_chunks() {
        // Chunks that contain bars (i.e. firstBarIndex >= 0) should have
        // every bar in their range as a key in barXOffsets, with values
        // monotonically non-decreasing and within the chunk width.
        val score = parseRasterFixture("guitarpro5/notes.gp5")
        val result = ScoreRasterRenderer(score, bravuraBytes).render()
        for ((i, chunk) in result.chunks.withIndex()) {
            if (chunk.firstBarIndex < 0) continue
            for (b in chunk.firstBarIndex..chunk.lastBarIndex) {
                assertTrue(
                    chunk.barXOffsets.containsKey(b),
                    "chunk[$i] missing barXOffsets[$b]; keys=${chunk.barXOffsets.keys.sorted()}",
                )
            }
            // Monotonic within a chunk (bars laid out left-to-right).
            var prev = Float.NEGATIVE_INFINITY
            for (b in chunk.firstBarIndex..chunk.lastBarIndex) {
                val x = chunk.barXOffsets.getValue(b)
                assertTrue(
                    x >= prev,
                    "chunk[$i] barXOffsets[$b]=$x < prev=$prev — bars are out of order",
                )
                assertTrue(
                    x in 0f..chunk.widthPx.toFloat(),
                    "chunk[$i] barXOffsets[$b]=$x out of [0, ${chunk.widthPx}]",
                )
                prev = x
            }
        }
    }

    @Test
    fun cursor_vertical_bounds_populated_for_score_chunks() {
        val score = parseRasterFixture("guitarpro5/notes.gp5")
        val result = ScoreRasterRenderer(score, bravuraBytes).render()
        for ((i, chunk) in result.chunks.withIndex()) {
            if (chunk.firstBarIndex < 0) continue
            val top = assertNotNull(chunk.cursorTopPx, "chunk[$i] missing cursorTopPx")
            val height = assertNotNull(chunk.cursorHeightPx, "chunk[$i] missing cursorHeightPx")
            assertTrue(top >= 0f, "chunk[$i] cursorTopPx=$top < 0")
            assertTrue(height > 0f, "chunk[$i] cursorHeightPx=$height <= 0")
            assertTrue(
                top + height <= chunk.heightPx.toFloat(),
                "chunk[$i] cursor bounds ${top}..${top + height} exceed height ${chunk.heightPx}",
            )
        }
    }

    @Test
    fun pixel_scale_multiplies_buffer_dimensions() {
        // pixelScale=2 should produce roughly 2x more pixels per axis.
        val score = parseRasterFixture("guitarpro5/notes.gp5")
        val r1 = ScoreRasterRenderer(score, bravuraBytes, pixelScale = 1.0).render()
        val r2 = ScoreRasterRenderer(score, bravuraBytes, pixelScale = 2.0).render()
        assertEquals(r1.chunks.size, r2.chunks.size, "chunk count should not depend on pixelScale")
        for ((i, c1) in r1.chunks.withIndex()) {
            val c2 = r2.chunks[i]
            // Logical dims should match across pixelScale variants.
            assertEquals(c1.widthPx, c2.widthPx, "chunk[$i] widthPx changed with pixelScale")
            assertEquals(c1.heightPx, c2.heightPx, "chunk[$i] heightPx changed with pixelScale")
            // Pixel dims should approximately 2× (allowing ±1 for rounding).
            assertTrue(
                c2.pixelsWidth in (c1.pixelsWidth * 2 - 2)..(c1.pixelsWidth * 2 + 2),
                "chunk[$i] pixelsWidth: ${c1.pixelsWidth} vs ${c2.pixelsWidth} at pixelScale=2",
            )
        }
    }

    @Test
    fun gp4_renders_to_raster() {
        val score = parseRasterFixture("guitarpro4/notes.gp4")
        val result = ScoreRasterRenderer(score, bravuraBytes).render()
        assertTrue(result.chunks.isNotEmpty())
        assertTrue(result.totalWidthPx > 0)
    }

    @Test
    fun gp7_renders_to_raster() {
        val score = parseRasterFixture("guitarpro7/notes.gp")
        val result = ScoreRasterRenderer(score, bravuraBytes).render()
        assertTrue(result.chunks.isNotEmpty())
        assertTrue(result.totalWidthPx > 0)
    }

    @Test
    fun musicxml_renders_to_raster() {
        val score = parseRasterFixture("musicxml4/partwise-basic.xml")
        val result = ScoreRasterRenderer(score, bravuraBytes).render()
        assertTrue(result.chunks.isNotEmpty())
    }

    @Test
    fun two_renders_of_same_score_produce_identical_chunk_count_and_dims() {
        val score = parseRasterFixture("guitarpro5/notes.gp5")
        val first = ScoreRasterRenderer(score, bravuraBytes).render()
        val second = ScoreRasterRenderer(score, bravuraBytes).render()
        assertEquals(first.chunks.size, second.chunks.size)
        assertEquals(first.totalWidthPx, second.totalWidthPx)
        assertEquals(first.totalHeightPx, second.totalHeightPx)
        for ((i, c1) in first.chunks.withIndex()) {
            val c2 = second.chunks[i]
            assertEquals(c1.pixelsWidth, c2.pixelsWidth, "chunk[$i] pixelsWidth")
            assertEquals(c1.pixelsHeight, c2.pixelsHeight, "chunk[$i] pixelsHeight")
            assertEquals(c1.firstBarIndex, c2.firstBarIndex, "chunk[$i] firstBarIndex")
            assertEquals(c1.lastBarIndex, c2.lastBarIndex, "chunk[$i] lastBarIndex")
        }
    }

    @Test
    fun rendered_pixels_contain_non_background_content() {
        // End-to-end smoke for font fidelity: at least some pixels in the
        // rendered output should differ from pure transparent — confirms
        // that drawing actually happened. If the music font silently
        // failed to load, glyphs render as empty boxes (still strokes/
        // staff lines exist) but at minimum we should see SOMETHING.
        val score = parseRasterFixture("guitarpro5/notes.gp5")
        val result = ScoreRasterRenderer(score, bravuraBytes).render()
        val first = result.chunks.first()
        val nonBlankCount = first.pixels.count { it != 0 }
        assertTrue(
            nonBlankCount > 0,
            "first chunk has no non-zero pixels — rendering produced an empty bitmap",
        )
        // Stronger check: at least 0.5% of pixels should be non-blank
        // (staff lines + noteheads on a 970-wide score chunk).
        val totalPixels = first.pixels.size
        val ratio = nonBlankCount.toDouble() / totalPixels
        assertTrue(
            ratio > 0.005,
            "first chunk has too few non-zero pixels: $nonBlankCount/$totalPixels (ratio=$ratio)",
        )
    }

    @Test
    fun pixel_format_is_argb8888_packed_int_not_premultiplied() {
        // Locks the format contract documented at RasterRender.kt:41 —
        // ARGB_8888 packed ints, NOT premultiplied, row stride = pixelsWidth.
        // The OmniFret iOS Swift consumer relies on this exact layout to
        // wrap chunk pixels into a CGImage; any drift here silently breaks
        // that bridge.
        val score = parseRasterFixture("guitarpro5/notes.gp5")
        val result = ScoreRasterRenderer(score, bravuraBytes).render()
        val first = result.chunks.first()

        // Untouched canvas pixels are exactly 0. Compose's ImageBitmap
        // (ARGB_8888) initializes to all-zero; rendering paints on top.
        // If init or readPixels behavior ever drifts, the empty-canvas
        // sentinel value moves with it.
        val zeroCount = first.pixels.count { it == 0 }
        assertTrue(
            zeroCount > 0,
            "expected some untouched (zero) pixels in chunk; got none — empty-canvas init may have changed",
        )

        // At least one fully-opaque pixel exists. Staff lines and stems
        // are drawn fully opaque; if alpha pre-multiplication accidentally
        // crept in, the high byte would be < 0xFF wherever the painter
        // blends with the empty canvas.
        val opaqueCount = first.pixels.count { ((it ushr 24) and 0xFF) == 0xFF }
        assertTrue(
            opaqueCount > 0,
            "no fully-opaque pixels (alpha=0xFF) — alpha may be pre-multiplied or stripped",
        )

        // Every non-zero pixel has non-zero alpha. Catches a hypothetical
        // drift where RGB channels are written without alpha (would render
        // invisibly through CGImage but pass the basic `it != 0` smoke).
        for (px in first.pixels) {
            if (px == 0) continue
            val a = (px ushr 24) and 0xFF
            assertTrue(
                a > 0,
                "pixel 0x${(px.toLong() and 0xFFFFFFFFL).toString(16)} has non-zero RGB but alpha=0",
            )
        }

        // At least one opaque-black pixel exists. Default ink is black;
        // the centers of staff lines and note stems should produce
        // 0xFF000000 (signed -16777216) packed Ints. This serves as a
        // smoke for the channel order: if Skiko ever started returning
        // ABGR-packed Ints instead of ARGB, rendered staff lines would
        // come out as pure-black with alpha=0, failing the alpha check
        // above; this assertion locks the positive case.
        val opaqueBlack = 0xFF000000.toInt()
        val opaqueBlackCount = first.pixels.count { it == opaqueBlack }
        assertTrue(
            opaqueBlackCount > 0,
            "no opaque-black pixels (0xFF000000) — staff lines / stems should produce some",
        )
    }

    @Test
    fun music_glyphs_render_at_engraving_size_not_default_text_size() {
        // Regression: prior to fixing ComposeRasterCanvas, music-font
        // glyphs were sized off `canvas.font.size` (Arial 10 px default)
        // instead of EngravingSettings.musicFontSize (36 px on Bravura
        // defaults). Note heads, clef, time signature, and key-signature
        // accidentals rendered ~3.6× too small; strokes (stems, beams,
        // staff lines) and tab numbers were unaffected. Total non-blank
        // pixel count alone won't catch it — the existing 0.5% smoke
        // floor is satisfied even with tiny glyphs. Assert a tighter
        // floor that fails when glyphs collapse to text-default size.
        val score = parseRasterFixture("guitarpro5/notes.gp5")
        val result = ScoreRasterRenderer(score, bravuraBytes).render()
        val first = result.chunks.first()
        val nonBlankCount = first.pixels.count { it != 0 }
        val totalPixels = first.pixels.size
        val ratio = nonBlankCount.toDouble() / totalPixels
        assertTrue(
            ratio > 0.02,
            "first chunk ratio=$ratio ($nonBlankCount/$totalPixels) is below " +
                "2% — music glyphs are likely rendering at default text size " +
                "instead of EngravingSettings.musicFontSize",
        )
    }
}
