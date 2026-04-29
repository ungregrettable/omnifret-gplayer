/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

package com.omnifret.gplayer.test

/**
 * Load a binary test fixture by relative path under `test-data/`.
 *
 * Each platform resolves resources differently: the JVM can read from the
 * test classpath, Kotlin/Native must walk an `NSBundle`. The actuals hide
 * that asymmetry.
 */
internal expect fun loadTestFixture(path: String): ByteArray
