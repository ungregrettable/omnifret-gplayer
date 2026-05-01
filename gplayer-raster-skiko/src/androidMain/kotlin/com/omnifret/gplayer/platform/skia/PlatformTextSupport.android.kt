/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.platform.skia

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import com.omnifret.gplayer.model.Color as GplayerColor
import com.omnifret.gplayer.model.Font as GplayerFont
import com.omnifret.gplayer.platform.TextAlign
import com.omnifret.gplayer.platform.TextBaseline
import java.io.File

internal actual class PlatformTextSupport actual constructor(musicFontBytes: ByteArray) {

    private val musicTypeface: Typeface = run {
        // Typeface.Builder needs a File; MemoryFile / FileDescriptor variants
        // require either reflection or hidden APIs. Writing to java.io.tmpdir
        // works because Android's runtime sets that to the app's cache dir
        // on startup (ActivityThread.setTempDir).
        val tmp = File.createTempFile("bravura-${musicFontBytes.contentHashCode()}", ".otf")
        tmp.deleteOnExit()
        tmp.writeBytes(musicFontBytes)
        Typeface.Builder(tmp).build()
            ?: error("Typeface.Builder returned null for bundled music font bytes")
    }

    private val fallbackTypeface: Typeface = Typeface.SANS_SERIF

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
        val paint = AndroidPaint().apply {
            isAntiAlias = true
            this.color = color.toAndroidArgbInt()
            textSize = font.size.toFloat()
            typeface = if (isMusicFont) musicTypeface else fallbackTypeface
            this.textAlign = when (textAlign) {
                TextAlign.Center -> AndroidPaint.Align.CENTER
                TextAlign.Right -> AndroidPaint.Align.RIGHT
                TextAlign.Left -> AndroidPaint.Align.LEFT
            }
        }
        // Android drawText takes y as the baseline. Translate per-baseline
        // semantics. fontMetrics.ascent is negative.
        val m = paint.fontMetrics
        val baselineY: Double = when (textBaseline) {
            TextBaseline.Top -> y - m.ascent
            TextBaseline.Middle -> y + (m.descent - m.ascent) / 2.0 - m.descent
            TextBaseline.Bottom -> y - m.descent
            TextBaseline.Alphabetic -> y
        }
        nativeCanvas.drawText(text, x.toFloat(), baselineY.toFloat(), paint)
    }
}

private fun GplayerColor.toAndroidArgbInt(): Int =
    ((a.toInt() and 0xFF) shl 24) or
        ((r.toInt() and 0xFF) shl 16) or
        ((g.toInt() and 0xFF) shl 8) or
        (b.toInt() and 0xFF)
