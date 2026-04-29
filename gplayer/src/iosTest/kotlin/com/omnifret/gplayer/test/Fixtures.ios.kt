/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

package com.omnifret.gplayer.test

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSProcessInfo
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/**
 * Resolve test fixtures via the `OMNIFRET_TEST_RESOURCES` env var that
 * `syncOmnifretTestResources` (in build.gradle.kts) populates. Kotlin/Native
 * iOS test executables don't inherit `commonTest/resources` automatically,
 * so we explicitly stage them and pass the directory path through the
 * test task's environment.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun loadTestFixture(path: String): ByteArray {
    val base = NSProcessInfo.processInfo.environment["OMNIFRET_TEST_RESOURCES"] as? String
        ?: error(
            "OMNIFRET_TEST_RESOURCES env var not set; check that " +
                "syncOmnifretTestResources runs before iOS tests"
        )
    val full = "$base/test-data/$path"
    val data = NSData.dataWithContentsOfFile(full)
        ?: error("fixture not found: $full")
    val length = data.length.toInt()
    val bytes = ByteArray(length)
    if (length > 0) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, length.toULong())
        }
    }
    return bytes
}
