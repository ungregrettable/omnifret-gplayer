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
import com.omnifret.gplayer.model.Color as GplayerColor
import com.omnifret.gplayer.model.Font as GplayerFont
import com.omnifret.gplayer.platform.TextAlign
import com.omnifret.gplayer.platform.TextBaseline

/**
 * Text rendering for [ComposeRasterCanvas]. Compose UI's high-level text
 * APIs (TextMeasurer/Paragraph) require a [androidx.compose.ui.text.font.FontFamily.Resolver]
 * which is awkward to construct outside @Composable scope. Bypass that
 * entirely by dropping to each platform's native canvas (the typealiased
 * `nativeCanvas` accessor) and drawing text with the platform's typeface
 * primitive — `android.graphics.Paint` + `Typeface` on Android,
 * `org.jetbrains.skia.Font` + `Paint` on iOS.
 *
 * Constructed once per [ComposeRasterCanvas] from caller-supplied music
 * font bytes (Bravura OTF). Reused across every paint call in that
 * canvas's lifetime; a fresh canvas per render() means the typeface is
 * also fresh per render — no leak from stale font registrations.
 */
internal expect class PlatformTextSupport(musicFontBytes: ByteArray) {

    /**
     * Draws [text] at ([x], [y]) in unscaled painter coordinates. The
     * caller's transform on [canvas] (display.scale × pixelScale) maps
     * to physical pixels.
     *
     * [isMusicFont] selects between the loaded music typeface and the
     * platform's default sans-serif; [font.size] is in unscaled CSS px.
     */
    fun drawText(
        canvas: Canvas,
        text: String,
        x: Double,
        y: Double,
        font: GplayerFont,
        color: GplayerColor,
        textAlign: TextAlign,
        textBaseline: TextBaseline,
        isMusicFont: Boolean,
    )
}
