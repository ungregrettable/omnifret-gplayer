/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.api.raster

import com.omnifret.gplayer.Environment
import com.omnifret.gplayer.RenderEngineFactory
import com.omnifret.gplayer.Settings
import com.omnifret.gplayer.collections.DoubleList
import com.omnifret.gplayer.model.Score
import com.omnifret.gplayer.model.Track
import com.omnifret.gplayer.platform.skia.ComposeRasterCanvas
import com.omnifret.gplayer.platform.skia.RasterPartial
import com.omnifret.gplayer.rendering.RenderFinishedEventArgs
import com.omnifret.gplayer.rendering.ScoreRenderer

/**
 * One rendered chunk of a score, in raster form. Mirror of [com.omnifret.gplayer.api.ScoreRenderChunk]
 * for raster output. Multiple chunks are emitted for long scores; stitch
 * vertically for the full layout.
 *
 * **Coordinate semantics:**
 * - [pixelsWidth] / [pixelsHeight] / [pixels.size]: physical pixels in
 *   the backing bitmap (= [widthPx] × `pixelScale` and similar for height).
 * - [widthPx] / [heightPx]: logical CSS pixels — what to use when sizing
 *   a Compose `Image` via density-aware `dp`.
 * - [barXOffsets]: chunk-relative x positions in **logical CSS pixels**,
 *   matching [widthPx]. All bars in `[firstBarIndex..lastBarIndex]`
 *   appear as keys when populated, but the map can be empty if the
 *   layout doesn't supply per-bar positions (e.g. score-info / chord-
 *   diagram chunks). Consumers can fall back to geometric interpolation
 *   in that case.
 *
 * [pixels] format: ARGB_8888 packed ints, row-major, **not** premultiplied.
 * Row stride equals [pixelsWidth] (no padding). Same convention as
 * `OmniFret/shared/src/commonMain/kotlin/com/omnifret/files/PdfDocument.kt`'s
 * `PdfPageBitmap`, so the same blit code path works for PDF and tab.
 */
public data class ScoreRasterChunk(
    val pixels: IntArray,
    val pixelsWidth: Int,
    val pixelsHeight: Int,
    val widthPx: Int,
    val heightPx: Int,
    val firstBarIndex: Int,
    val lastBarIndex: Int,
    val barXOffsets: Map<Int, Float>,
)

/** Result of a full-score raster render. */
public data class ScoreRasterResult(
    val totalWidthPx: Int,
    val totalHeightPx: Int,
    val chunks: List<ScoreRasterChunk>,
)

/**
 * Render a [Score] to ARGB_8888 raster chunks via Compose Multiplatform's
 * `Canvas` / `ImageBitmap` types. Synchronous; for large scores call from
 * a background dispatcher.
 *
 * The raster path uses Compose UI's graphics primitives for paths/shapes
 * and drops to each platform's native canvas (`android.graphics.Canvas`
 * on Android, `org.jetbrains.skia.Canvas` on iOS) for text rendering —
 * see `ComposeRasterCanvas` for the rationale.
 *
 * @param musicFontBytes raw bytes of a SMuFL-compliant OTF (typically
 * Bravura). Required — gplayer ships no font data; the consumer manages
 * the font version.
 * @param widthPx target render width in **logical CSS pixels**; the
 * layout reflows to fit. Match the display surface width.
 * @param pixelScale device-pixel-ratio multiplier for the backing
 * bitmap dimensions. Set to your display's density (e.g., 2.0 or 3.0
 * for Retina) for sharp output. Logical coordinates ([widthPx],
 * [ScoreRasterChunk.widthPx], [ScoreRasterChunk.barXOffsets]) are
 * unaffected.
 *
 * **Threading**: not safe to call concurrently. The implementation
 * mutates [Environment.renderEngines] (a global map) for the duration
 * of [render]. Wrap concurrent renders with external synchronization
 * if needed.
 */
@OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)
public class ScoreRasterRenderer(
    private val score: Score,
    private val musicFontBytes: ByteArray,
    private val tracks: List<Track>? = null,
    private val widthPx: Double = 970.0,
    private val pixelScale: Double = 1.0,
    settings: Settings? = null,
) {

    private val resolvedSettings: Settings = (settings ?: Settings()).also {
        // Force eager rendering so partialRenderFinished fires with
        // renderResult populated (lazy mode stores partials and waits
        // for renderLazyPartial calls). Same as ScoreSvgRenderer.
        it.core.enableLazyLoading = false
    }

    public fun render(): ScoreRasterResult {
        // Register a unique engine name for this render() call so per-
        // instance state (font bytes + pixelScale) can be passed via the
        // factory closure. Environment.renderEngines is a global map;
        // see threading note in the class kdoc.
        val engineName = "raster-skiko-${nextEngineId()}"
        val factory = RenderEngineFactory(supportsWorkers = false) {
            ComposeRasterCanvas(musicFontBytes, pixelScale)
        }
        Environment.renderEngines.set(engineName, factory)
        try {
            resolvedSettings.core.engine = engineName

            val renderer = ScoreRenderer(resolvedSettings)
            renderer.width = widthPx

            val chunks = mutableListOf<ScoreRasterChunk>()
            var totalW = 0.0
            var totalH = 0.0
            var caughtError: Throwable? = null

            renderer.partialRenderFinished.on { args: RenderFinishedEventArgs ->
                val partial = args.renderResult as? RasterPartial ?: return@on
                val barOffsets: Map<Int, Float> = args.barXOffsets
                    ?.mapValues { it.value.toFloat() }
                    ?: emptyMap()
                chunks += ScoreRasterChunk(
                    pixels = partial.pixels,
                    pixelsWidth = partial.pixelsWidth,
                    pixelsHeight = partial.pixelsHeight,
                    widthPx = args.width.toInt(),
                    heightPx = args.height.toInt(),
                    firstBarIndex = args.firstMasterBarIndex.toInt(),
                    lastBarIndex = args.lastMasterBarIndex.toInt(),
                    barXOffsets = barOffsets,
                )
            }
            renderer.renderFinished.on { args: RenderFinishedEventArgs ->
                totalW = args.totalWidth
                totalH = args.totalHeight
            }
            renderer.error.on { e -> caughtError = e }

            val indexes: DoubleList = if (tracks == null) {
                DoubleList()
            } else {
                val all = score.tracks
                val list = DoubleList()
                for (t in tracks) {
                    for (i in 0 until all.length.toInt()) {
                        if (all[i] === t) {
                            list.push(i.toDouble())
                            break
                        }
                    }
                }
                list
            }
            renderer.renderScore(score, indexes, null)
            caughtError?.let { throw it }

            return ScoreRasterResult(
                totalWidthPx = (totalW * pixelScale).toInt(),
                totalHeightPx = (totalH * pixelScale).toInt(),
                chunks = chunks.toList(),
            )
        } finally {
            // Best-effort cleanup. If the consumer caught an exception
            // and re-renders, leftover unique-named entries don't cause
            // correctness issues but they do leak memory in the global
            // map — so always remove.
            Environment.renderEngines.delete(engineName)
        }
    }

    private companion object {
        // Single-threaded counter for unique engine names. Doesn't need
        // to be atomic given the documented threading constraint.
        private var engineIdCounter: Long = 0L
        private fun nextEngineId(): Long = ++engineIdCounter
    }
}
