/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.raster.test

import com.omnifret.gplayer.api.GPlayer
import com.omnifret.gplayer.model.Score

/** Parse a score fixture from `test-data/<path>`. */
internal fun parseRasterFixture(path: String): Score =
    GPlayer.parseScore(loadRasterTestResource("test-data/$path"))

/** Lazily-loaded Bravura OTF bytes used by every raster test. */
internal val bravuraBytes: ByteArray by lazy {
    loadRasterTestResource("fonts/Bravura.otf")
}
