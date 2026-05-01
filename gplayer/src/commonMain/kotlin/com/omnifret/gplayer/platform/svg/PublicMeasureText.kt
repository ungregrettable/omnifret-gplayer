/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.platform.svg

import com.omnifret.gplayer.collections.List as GpList
import com.omnifret.gplayer.model.FontStyle
import com.omnifret.gplayer.model.FontWeight
import com.omnifret.gplayer.platform.MeasuredText

// KMP-PORT addition: the auto-generated FontSizes class stays `internal`
// so regenerating it doesn't churn its visibility. This file exposes
// just the measureString entry point as a public top-level function so
// sibling modules (:gplayer-raster-skiko) can share the layout-time
// font-metric lookup table — keeping painted glyph positions consistent
// with what the layout engine assumed.
public fun measureTextWithFontSizes(
    text: String,
    families: GpList<String>,
    size: Double,
    style: FontStyle,
    weight: FontWeight,
): MeasuredText = FontSizes.measureString(text, families, size, style, weight)
