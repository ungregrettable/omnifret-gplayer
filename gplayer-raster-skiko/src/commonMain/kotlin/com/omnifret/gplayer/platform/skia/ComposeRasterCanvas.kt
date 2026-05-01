/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.platform.skia

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color as ComposeColor
import com.omnifret.gplayer.Settings
import com.omnifret.gplayer.collections.List as GpList
import com.omnifret.gplayer.model.Color as GplayerColor
import com.omnifret.gplayer.model.Font as GplayerFont
import com.omnifret.gplayer.model.FontStyle
import com.omnifret.gplayer.model.MusicFontSymbol
import com.omnifret.gplayer.platform.ICanvas
import com.omnifret.gplayer.platform.MeasuredText
import com.omnifret.gplayer.platform.TextAlign
import com.omnifret.gplayer.platform.TextBaseline
import com.omnifret.gplayer.platform.svg.measureTextWithFontSizes

/**
 * Compose-MP-backed [ICanvas]. Uses `androidx.compose.ui.graphics` types
 * for paths, shapes, and transforms (works identically on Android and
 * iOS via Compose Multiplatform). Text rendering bypasses Compose's
 * higher-level TextMeasurer (which needs a FontFamily.Resolver) and
 * drops to each platform's native canvas via [PlatformTextSupport].
 *
 * Coordinate convention: the renderer paints in **unscaled** CSS px
 * (1 unit = 1 logical pixel). [beginRender] sets up a `scale(s, s)`
 * transform on the underlying [Canvas] where `s = display.scale × pixelScale`,
 * mapping painter coords to physical pixels in the backing [ImageBitmap].
 * [measureText] delegates to gplayer's existing [FontSizes.measureString]
 * so layout-time metrics stay identical to the SVG path.
 */
internal class ComposeRasterCanvas(
    musicFontBytes: ByteArray,
    private val pixelScale: Double,
) : ICanvas {

    private val textSupport: PlatformTextSupport = PlatformTextSupport(musicFontBytes)
    private val path: Path = Path()
    private val paint: Paint = Paint()
    private var image: ImageBitmap? = null
    private var canvas: Canvas? = null
    private var totalScale: Float = 1f

    override lateinit var settings: Settings

    override var color: GplayerColor = GplayerColor(0.0, 0.0, 0.0, 255.0)
        set(value) {
            field = value
            paint.color = value.toComposeColor()
        }

    override var lineWidth: Double = 1.0

    override var font: GplayerFont = GplayerFont("Arial", 10.0, FontStyle.Plain)

    override var textAlign: TextAlign = TextAlign.Left
    override var textBaseline: TextBaseline = TextBaseline.Top

    override fun beginRender(width: Double, height: Double) {
        val displayScale = settings.display.scale
        totalScale = (displayScale * pixelScale).toFloat()
        // [width] / [height] arrive scaled by display.scale already (see
        // ScoreLayout.registerPartial). Multiply again by pixelScale for
        // physical pixels in the backing bitmap.
        val pxW = (width * pixelScale).toInt().coerceAtLeast(1)
        val pxH = (height * pixelScale).toInt().coerceAtLeast(1)
        val bmp = ImageBitmap(pxW, pxH, ImageBitmapConfig.Argb8888, hasAlpha = true)
        image = bmp
        canvas = Canvas(bmp).also {
            // Map unscaled painter coords → physical pixels.
            it.scale(totalScale, totalScale)
        }
        textBaseline = TextBaseline.Top
    }

    override fun endRender(): Any? {
        // Mirrors SvgCanvas.endRender — this returns the partial render
        // result that ScoreLayout assigns to args.renderResult before
        // firing partialRenderFinished. onRenderFinished() is a separate
        // post-frame lifecycle hook that returns null for raster.
        val bmp = image ?: return null
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.readPixels(pixels)
        return RasterPartial(pixels, bmp.width, bmp.height)
    }

    override fun onRenderFinished(): Any? = null

    override fun destroy() {
        image = null
        canvas = null
    }

    // ---- Groups: no-op for raster output (these only matter for SVG
    // <g> grouping which the consumer parses; raster consumers read bar
    // positions from RenderFinishedEventArgs.barXOffsets instead).
    override fun beginGroup(identifier: String) {}
    override fun endGroup() {}

    // ---- Paths
    override fun beginPath() {
        path.reset()
    }

    override fun closePath() {
        path.close()
    }

    override fun moveTo(x: Double, y: Double) {
        path.moveTo(x.toFloat(), y.toFloat())
    }

    override fun lineTo(x: Double, y: Double) {
        path.lineTo(x.toFloat(), y.toFloat())
    }

    override fun bezierCurveTo(
        cp1X: Double, cp1Y: Double,
        cp2X: Double, cp2Y: Double,
        x: Double, y: Double,
    ) {
        path.cubicTo(
            cp1X.toFloat(), cp1Y.toFloat(),
            cp2X.toFloat(), cp2Y.toFloat(),
            x.toFloat(), y.toFloat(),
        )
    }

    override fun quadraticCurveTo(cpx: Double, cpy: Double, x: Double, y: Double) {
        path.quadraticTo(cpx.toFloat(), cpy.toFloat(), x.toFloat(), y.toFloat())
    }

    override fun fill() {
        val c = canvas ?: return
        paint.style = PaintingStyle.Fill
        c.drawPath(path, paint)
    }

    override fun stroke() {
        val c = canvas ?: return
        paint.style = PaintingStyle.Stroke
        paint.strokeWidth = lineWidth.toFloat()
        paint.strokeCap = StrokeCap.Butt
        paint.strokeJoin = StrokeJoin.Miter
        c.drawPath(path, paint)
    }

    // ---- Primitives
    override fun fillRect(x: Double, y: Double, w: Double, h: Double) {
        if (w <= 0.0 || h <= 0.0) return
        val c = canvas ?: return
        paint.style = PaintingStyle.Fill
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
    }

    override fun strokeRect(x: Double, y: Double, w: Double, h: Double) {
        val c = canvas ?: return
        paint.style = PaintingStyle.Stroke
        paint.strokeWidth = lineWidth.toFloat()
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), paint)
    }

    override fun fillCircle(x: Double, y: Double, radius: Double) {
        val c = canvas ?: return
        paint.style = PaintingStyle.Fill
        // Compose has no drawCircle on Canvas directly; use a path.
        val circlePath = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    left = (x - radius).toFloat(),
                    top = (y - radius).toFloat(),
                    right = (x + radius).toFloat(),
                    bottom = (y + radius).toFloat(),
                ),
            )
        }
        c.drawPath(circlePath, paint)
    }

    override fun strokeCircle(x: Double, y: Double, radius: Double) {
        val c = canvas ?: return
        paint.style = PaintingStyle.Stroke
        paint.strokeWidth = lineWidth.toFloat()
        val circlePath = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    left = (x - radius).toFloat(),
                    top = (y - radius).toFloat(),
                    right = (x + radius).toFloat(),
                    bottom = (y + radius).toFloat(),
                ),
            )
        }
        c.drawPath(circlePath, paint)
    }

    // ---- Text
    override fun fillText(text: String, x: Double, y: Double) {
        val c = canvas ?: return
        textSupport.drawText(c, text, x, y, font, color, textAlign, textBaseline, isMusicFont = false)
    }

    override fun measureText(text: String): MeasuredText {
        // Same source the SVG path uses. Layout is computed against this
        // table; if we used Compose's text measurement here, layout would
        // drift from the painted glyph positions.
        return measureTextWithFontSizes(text, font.families, font.size, font.style, font.weight)
    }

    override fun fillMusicFontSymbol(
        x: Double, y: Double, relativeScale: Double,
        symbol: MusicFontSymbol, centerAtPosition: Boolean?,
    ) {
        if (symbol == MusicFontSymbol.None) return
        val c = canvas ?: return
        // SMuFL glyphs are addressed by their codepoint values. The
        // renderer passes the codepoint as the enum's int value.
        val text = codePointToString(symbol.value)
        val effectiveSize = font.size * relativeScale
        val glyphFont = GplayerFont(font.family, effectiveSize, font.style)
        val align = if (centerAtPosition == true) TextAlign.Center else textAlign
        textSupport.drawText(c, text, x, y, glyphFont, color, align, textBaseline, isMusicFont = true)
    }

    override fun fillMusicFontSymbols(
        x: Double, y: Double, relativeScale: Double,
        symbols: GpList<MusicFontSymbol>, centerAtPosition: Boolean?,
    ) {
        val c = canvas ?: return
        val sb = StringBuilder()
        for (s in symbols) {
            if (s != MusicFontSymbol.None) {
                sb.append(codePointToString(s.value))
            }
        }
        if (sb.isEmpty()) return
        val effectiveSize = font.size * relativeScale
        val glyphFont = GplayerFont(font.family, effectiveSize, font.style)
        val align = if (centerAtPosition == true) TextAlign.Center else textAlign
        textSupport.drawText(c, sb.toString(), x, y, glyphFont, color, align, textBaseline, isMusicFont = true)
    }

    // ---- Transforms (only beginRotate/endRotate; no arbitrary matrices)
    override fun beginRotate(centerX: Double, centerY: Double, angle: Double) {
        val c = canvas ?: return
        c.save()
        c.translate(centerX.toFloat(), centerY.toFloat())
        c.rotate(angle.toFloat())
        c.translate(-centerX.toFloat(), -centerY.toFloat())
    }

    override fun endRotate() {
        canvas?.restore()
    }
}

/** Convert gplayer's [GplayerColor] (RGBA in 0..255 doubles) to a [ComposeColor]. */
private fun GplayerColor.toComposeColor(): ComposeColor =
    ComposeColor(
        red = (r / 255.0).toFloat(),
        green = (g / 255.0).toFloat(),
        blue = (b / 255.0).toFloat(),
        alpha = (a / 255.0).toFloat(),
    )

/**
 * KMP-portable codepoint→String. SMuFL glyphs sit mostly in the BMP PUA
 * (U+E000–U+F8FF) but a few are in the supplementary Musical Symbols
 * block (U+1D100+) which needs a surrogate pair.
 */
private fun codePointToString(cp: Int): String =
    if (cp <= 0xFFFF) {
        Char(cp).toString()
    } else {
        val offset = cp - 0x10000
        val hi = Char((0xD800 + (offset shr 10)) and 0xFFFF)
        val lo = Char((0xDC00 + (offset and 0x3FF)) and 0xFFFF)
        charArrayOf(hi, lo).concatToString()
    }
