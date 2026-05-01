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
import androidx.compose.ui.graphics.nativeCanvas
import com.omnifret.gplayer.model.Color as GplayerColor
import com.omnifret.gplayer.model.Font as GplayerFont
import com.omnifret.gplayer.platform.TextAlign
import com.omnifret.gplayer.platform.TextBaseline
import org.jetbrains.skia.Data
import org.jetbrains.skia.Font as SkiaFont
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle as SkiaFontStyle
import org.jetbrains.skia.Paint as SkiaPaint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Typeface

internal actual class PlatformTextSupport actual constructor(musicFontBytes: ByteArray) {

    // skiko 0.9.x exposes makeFromData on FontMgr, not Typeface (mirrors
    // SkFontMgr::makeFromData in the C++ Skia API).
    private val musicTypeface: Typeface =
        FontMgr.default.makeFromData(Data.makeFromBytes(musicFontBytes))
            ?: error("FontMgr.default.makeFromData returned null for the supplied music font bytes")

    private val fallbackTypeface: Typeface =
        FontMgr.default.matchFamilyStyle(null, SkiaFontStyle.NORMAL) ?: musicTypeface

    actual fun drawText(
        canvas: Canvas,
        text: String,
        x: Double,
        y: Double,
        font: GplayerFont,
        color: GplayerColor,
        textAlign: TextAlign,
        textBaseline: TextBaseline,
        isMusicFont: Boolean,
    ) {
        if (text.isEmpty()) return
        val nativeCanvas = canvas.nativeCanvas
        val typeface = if (isMusicFont) musicTypeface else fallbackTypeface
        val skiaFont = SkiaFont(typeface, font.size.toFloat())
        val skiaPaint = SkiaPaint().apply {
            this.color = color.toSkiaArgbInt()
            mode = PaintMode.FILL
            isAntiAlias = true
        }
        val textWidth = skiaFont.measureTextWidth(text)
        val anchorX: Double = when (textAlign) {
            TextAlign.Center -> x - textWidth / 2.0
            TextAlign.Right -> x - textWidth.toDouble()
            TextAlign.Left -> x
        }
        val m = skiaFont.metrics
        // Skia metrics: ascent is negative (above baseline), descent is positive.
        val baselineY: Double = when (textBaseline) {
            TextBaseline.Top -> y - m.ascent
            TextBaseline.Middle -> y + (m.descent - m.ascent) / 2.0 - m.descent
            TextBaseline.Bottom -> y - m.descent
            TextBaseline.Alphabetic -> y
        }
        nativeCanvas.drawString(text, anchorX.toFloat(), baselineY.toFloat(), skiaFont, skiaPaint)
    }
}

private fun GplayerColor.toSkiaArgbInt(): Int =
    ((a.toInt() and 0xFF) shl 24) or
        ((r.toInt() and 0xFF) shl 16) or
        ((g.toInt() and 0xFF) shl 8) or
        (b.toInt() and 0xFF)
