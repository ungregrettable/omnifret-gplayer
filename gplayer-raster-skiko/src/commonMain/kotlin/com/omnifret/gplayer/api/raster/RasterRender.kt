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
 * - [cursorTopPx] / [cursorHeightPx]: chunk-relative y bounds in
 *   **logical CSS pixels** for the native gplayer cursor, when available.
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
    /**
     * Every engraved beat's `(absoluteTick, x)` pair inside this chunk,
     * sorted ascending by tick. `x` shares the [barXOffsets]
     * coordinate frame (scaled CSS px). Use for tick-driven playhead
     * placement: bracket the audio tick in this list and interpolate
     * piecewise-linearly between neighbours. Gplayer's beat positions
     * are log-spring-distributed inside each bar, so a linear sweep
     * across `barWidth` drifts from engraved noteheads on sparse
     * rhythms; using actual beat positions across the whole chunk
     * removes that drift *and* eliminates the bar-boundary "teleport"
     * a per-bar model produces (the first beat of a bar engraves a
     * few px right of the bar line). Empty when no beats are present.
     */
    val beatXOffsets: List<Pair<Double, Float>> = emptyList(),
    val cursorTopPx: Float? = null,
    val cursorHeightPx: Float? = null,
)

/** Result of a full-score raster render. */
public data class ScoreRasterResult(
    val totalWidthPx: Int,
    val totalHeightPx: Int,
    val chunks: List<ScoreRasterChunk>,
)

/**
 * Layout-only metadata for one chunk in a [ScoreRasterLazyHandle]. Same
 * coordinate fields as [ScoreRasterChunk], minus the pixel buffer —
 * pixels are produced on demand by [ScoreRasterLazyHandle.renderChunk].
 *
 * The [resultId] is the GUID gplayer assigns each layout partial; the
 * lazy handle uses it to drive on-demand rasterisation. It's `internal`
 * because consumers should pass the chunk's [index] (or the chunk
 * itself) — the GUID is implementation detail of the gplayer event
 * pipeline.
 */
public data class ScoreRasterChunkLayout(
    val index: Int,
    val firstBarIndex: Int,
    val lastBarIndex: Int,
    val widthPx: Int,
    val heightPx: Int,
    val pixelsWidth: Int,
    val pixelsHeight: Int,
    /** Chunk's x position within the full strip, in logical CSS px. */
    val xLogicalPx: Int,
    /** Chunk's y position within the full strip, in logical CSS px. */
    val yLogicalPx: Int,
    val barXOffsets: Map<Int, Float>,
    /** Every beat's `(absoluteTick, x)` pair inside this chunk, sorted
     *  by tick. See [ScoreRasterChunk.beatXOffsets]. */
    val beatXOffsets: List<Pair<Double, Float>> = emptyList(),
    val cursorTopPx: Float? = null,
    val cursorHeightPx: Float? = null,
    internal val resultId: String,
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
 * of [render] / [openLazy] / [ScoreRasterLazyHandle.renderChunk]. Wrap
 * concurrent calls with external synchronization if needed.
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

    private val baseSettings: Settings = settings ?: Settings()

    public fun render(): ScoreRasterResult {
        // Force eager rendering so partialRenderFinished fires with
        // renderResult populated (lazy mode stores partials and waits
        // for renderLazyPartial calls).
        val resolvedSettings: Settings = baseSettings.also {
            it.core.enableLazyLoading = false
        }
        // Register a unique engine name for this render() call so per-
        // instance state (font bytes + pixelScale) can be passed via the
        // factory closure. Environment.renderEngines is a global map;
        // see threading note in the class kdoc.
        val engineName = "raster-skiko-${nextEngineId()}"
        val factory = RenderEngineFactory(supportsWorkers = false) {
            ComposeRasterCanvas(musicFontBytes, pixelScale)
        }
        Environment.renderEngines.set(engineName, factory)
        var renderer: ScoreRenderer? = null
        try {
            resolvedSettings.core.engine = engineName

            renderer = ScoreRenderer(resolvedSettings)
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
                val beatOffsets: List<Pair<Double, Float>> = args.beatXOffsets
                    ?.map { it[0] to it[1].toFloat() }
                    ?: emptyList()
                chunks += ScoreRasterChunk(
                    pixels = partial.pixels,
                    pixelsWidth = partial.pixelsWidth,
                    pixelsHeight = partial.pixelsHeight,
                    widthPx = args.width.toInt(),
                    heightPx = args.height.toInt(),
                    firstBarIndex = args.firstMasterBarIndex.toInt(),
                    lastBarIndex = args.lastMasterBarIndex.toInt(),
                    barXOffsets = barOffsets,
                    beatXOffsets = beatOffsets,
                    cursorTopPx = args.cursorTopPx?.toFloat(),
                    cursorHeightPx = args.cursorHeightPx?.toFloat(),
                )
            }
            renderer.renderFinished.on { args: RenderFinishedEventArgs ->
                totalW = args.totalWidth
                totalH = args.totalHeight
            }
            renderer.error.on { e -> caughtError = e }

            renderer.renderScore(score, resolvedTrackIndexes(), null)
            caughtError?.let { throw it }

            return ScoreRasterResult(
                totalWidthPx = (totalW * pixelScale).toInt(),
                totalHeightPx = (totalH * pixelScale).toInt(),
                chunks = chunks.toList(),
            )
        } finally {
            renderer?.destroy()
            // Best-effort cleanup. If the consumer caught an exception
            // and re-renders, leftover unique-named entries don't cause
            // correctness issues but they do leak memory in the global
            // map — so always remove.
            Environment.renderEngines.delete(engineName)
        }
    }

    /**
     * Open a lazy render handle. Performs the layout pass eagerly (so
     * chunk count + per-chunk geometry are known up front) but defers
     * pixel rasterisation until [ScoreRasterLazyHandle.renderChunk] is
     * called. Pair with `use {}` or call [ScoreRasterLazyHandle.close]
     * when done — leaking a handle pins the gplayer renderer instance
     * (~1–2 MB of layout state) and the engine factory entry in
     * [Environment.renderEngines].
     *
     * Memory tradeoff vs [render]:
     * - `render()` returns all chunks' `pixels: IntArray` materialised
     *   eagerly — peak memory is `O(chunkCount × pixelScale²)`.
     * - `openLazy()` returns metadata only; consumers materialise
     *   pixels on demand and are expected to evict bitmaps when chunks
     *   leave the viewport — peak memory is `O(viewportChunks ×
     *   pixelScale²)`, independent of total score length.
     *
     * Layout determinism is identical to [render]: same score / tracks /
     * widthPx / settings yield the same chunk count, bar ranges, and
     * coordinates. Pixel output is bit-equivalent to [render]'s.
     */
    public fun openLazy(): ScoreRasterLazyHandle {
        // Lazy mode — the layout pass populates ScoreLayout._lazyPartials
        // (keyed by GUID) and triggers partialLayoutFinished without
        // running the canvas paint callbacks. We pick up id + dimensions
        // from those events; pixels come later via renderChunk().
        val resolvedSettings: Settings = baseSettings.also {
            it.core.enableLazyLoading = true
        }
        val engineName = "raster-skiko-${nextEngineId()}"
        val factory = RenderEngineFactory(supportsWorkers = false) {
            ComposeRasterCanvas(musicFontBytes, pixelScale)
        }
        Environment.renderEngines.set(engineName, factory)

        var ownsEngine = true
        var renderer: ScoreRenderer? = null
        try {
            resolvedSettings.core.engine = engineName
            val activeRenderer = ScoreRenderer(resolvedSettings)
            renderer = activeRenderer
            activeRenderer.width = widthPx

            val layouts = mutableListOf<ScoreRasterChunkLayout>()
            var totalW = 0.0
            var totalH = 0.0
            var caughtError: Throwable? = null

            activeRenderer.partialLayoutFinished.on { args: RenderFinishedEventArgs ->
                val barOffsets: Map<Int, Float> = args.barXOffsets
                    ?.mapValues { it.value.toFloat() }
                    ?: emptyMap()
                val beatOffsets: List<Pair<Double, Float>> = args.beatXOffsets
                    ?.map { it[0] to it[1].toFloat() }
                    ?: emptyList()
                layouts += ScoreRasterChunkLayout(
                    index = layouts.size,
                    firstBarIndex = args.firstMasterBarIndex.toInt(),
                    lastBarIndex = args.lastMasterBarIndex.toInt(),
                    widthPx = args.width.toInt(),
                    heightPx = args.height.toInt(),
                    pixelsWidth = (args.width * pixelScale).toInt(),
                    pixelsHeight = (args.height * pixelScale).toInt(),
                    xLogicalPx = args.x.toInt(),
                    yLogicalPx = args.y.toInt(),
                    barXOffsets = barOffsets,
                    beatXOffsets = beatOffsets,
                    cursorTopPx = args.cursorTopPx?.toFloat(),
                    cursorHeightPx = args.cursorHeightPx?.toFloat(),
                    resultId = args.id,
                )
            }
            activeRenderer.renderFinished.on { args: RenderFinishedEventArgs ->
                totalW = args.totalWidth
                totalH = args.totalHeight
            }
            activeRenderer.error.on { e -> caughtError = e }

            activeRenderer.renderScore(score, resolvedTrackIndexes(), null)
            caughtError?.let { throw it }

            val handle = ScoreRasterLazyHandle(
                renderer = activeRenderer,
                engineName = engineName,
                pixelScale = pixelScale,
                totalWidthPx = (totalW * pixelScale).toInt(),
                totalHeightPx = (totalH * pixelScale).toInt(),
                chunks = layouts.toList(),
            )
            // Handle now owns the engine entry — don't delete in finally.
            ownsEngine = false
            return handle
        } finally {
            if (ownsEngine) {
                renderer?.destroy()
                Environment.renderEngines.delete(engineName)
            }
        }
    }

    private fun resolvedTrackIndexes(): DoubleList {
        if (tracks == null) return DoubleList()
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
        return list
    }

    private companion object {
        // Single-threaded counter for unique engine names. Doesn't need
        // to be atomic given the documented threading constraint.
        private var engineIdCounter: Long = 0L
        private fun nextEngineId(): Long = ++engineIdCounter
    }
}

/**
 * On-demand raster handle returned by [ScoreRasterRenderer.openLazy].
 * Holds the gplayer renderer + its per-chunk paint closures so
 * [renderChunk] can rasterise individual chunks without re-running
 * layout. Call [close] when done — leaking the handle pins the engine
 * entry in [Environment.renderEngines] and the renderer's layout state.
 */
@OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)
public class ScoreRasterLazyHandle internal constructor(
    private var renderer: ScoreRenderer?,
    private val engineName: String,
    private val pixelScale: Double,
    public val totalWidthPx: Int,
    public val totalHeightPx: Int,
    public val chunks: List<ScoreRasterChunkLayout>,
) {

    private var closed: Boolean = false

    /**
     * Rasterise the chunk at [index]. Cost: ~20–50 ms per chunk on a
     * mid-tier phone (Skia draw + IntArray allocation). Idempotent —
     * each call paints fresh and returns a new [ScoreRasterChunk].
     * The caller is expected to cache the resulting bitmap and call
     * again only on cache miss.
     *
     * Not safe to call concurrently with itself or with another lazy
     * handle's [renderChunk] — the underlying gplayer engine state is
     * single-threaded.
     */
    public fun renderChunk(index: Int): ScoreRasterChunk {
        val activeRenderer = renderer
        check(!closed && activeRenderer != null) { "ScoreRasterLazyHandle has been closed" }
        require(index in chunks.indices) {
            "chunk index $index out of range ${chunks.indices}"
        }
        val layout = chunks[index]
        var captured: ScoreRasterChunk? = null
        var caughtError: Throwable? = null
        // Filter the broadcast to this chunk's resultId. The callback
        // only fires once (one renderResult call → one event for that
        // id), so listener identity doesn't matter for unsubscription.
        val listener: (RenderFinishedEventArgs) -> Unit = { args ->
            if (args.id == layout.resultId) {
                val partial = args.renderResult as? RasterPartial
                if (partial != null) {
                    captured = ScoreRasterChunk(
                        pixels = partial.pixels,
                        pixelsWidth = partial.pixelsWidth,
                        pixelsHeight = partial.pixelsHeight,
                        widthPx = layout.widthPx,
                        heightPx = layout.heightPx,
                        firstBarIndex = layout.firstBarIndex,
                        lastBarIndex = layout.lastBarIndex,
                        barXOffsets = layout.barXOffsets,
                        beatXOffsets = layout.beatXOffsets,
                        cursorTopPx = layout.cursorTopPx,
                        cursorHeightPx = layout.cursorHeightPx,
                    )
                }
            }
        }
        val errorListener: (Throwable) -> Unit = { caughtError = it }
        activeRenderer.partialRenderFinished.on(listener)
        activeRenderer.error.on(errorListener)
        try {
            activeRenderer.renderResult(layout.resultId)
            caughtError?.let { throw it }
            return captured
                ?: error("No partialRenderFinished event for resultId=${layout.resultId}")
        } finally {
            activeRenderer.partialRenderFinished.off(listener)
            activeRenderer.error.off(errorListener)
        }
    }

    public fun close() {
        if (closed) return
        closed = true
        Environment.renderEngines.delete(engineName)
        renderer?.destroy()
        renderer = null
    }
}
