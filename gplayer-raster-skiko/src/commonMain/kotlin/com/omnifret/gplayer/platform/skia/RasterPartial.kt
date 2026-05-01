/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

package com.omnifret.gplayer.platform.skia

/**
 * Internal handoff value returned by [ComposeRasterCanvas.onRenderFinished]
 * and consumed by `ScoreRasterRenderer`. Not part of the public API.
 */
internal class RasterPartial(
    val pixels: IntArray,
    val pixelsWidth: Int,
    val pixelsHeight: Int,
)
