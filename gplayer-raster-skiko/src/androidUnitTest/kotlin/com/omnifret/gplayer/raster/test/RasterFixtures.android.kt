/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

package com.omnifret.gplayer.raster.test

internal actual fun loadRasterTestResource(path: String): ByteArray {
    val stream = RasterFixtures::class.java.classLoader?.getResourceAsStream(path)
        ?: error("raster test resource not found on classpath: $path")
    return stream.use { it.readBytes() }
}

private object RasterFixtures
