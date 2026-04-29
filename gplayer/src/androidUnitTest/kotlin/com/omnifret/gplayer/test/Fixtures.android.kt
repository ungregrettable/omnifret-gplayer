/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

package com.omnifret.gplayer.test

internal actual fun loadTestFixture(path: String): ByteArray {
    val resource = "test-data/$path"
    val stream = Fixtures::class.java.classLoader?.getResourceAsStream(resource)
        ?: error("fixture not found on classpath: $resource")
    return stream.use { it.readBytes() }
}

private object Fixtures
