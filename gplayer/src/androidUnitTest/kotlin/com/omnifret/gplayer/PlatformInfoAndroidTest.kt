/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

package com.omnifret.gplayer

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Android `actual` for `PlatformInfo`. Asserts the actual reads
 * `Build.MANUFACTURER`/`Build.MODEL`/`Build.VERSION.SDK_INT` correctly.
 *
 * Under JVM unit tests, AGP returns "null" / "unknown" / 0 from these
 * Build fields (Robolectric fills them more realistically). We assert
 * the *format* of the strings rather than specific values.
 */
class PlatformInfoAndroidTest {

    @Test
    fun name_starts_with_Android() {
        assertTrue(
            PlatformInfo.name.startsWith("Android"),
            "expected Android prefix, got '${PlatformInfo.name}'",
        )
    }

    @Test
    fun version_starts_with_API() {
        assertTrue(
            PlatformInfo.version.startsWith("API "),
            "expected API prefix, got '${PlatformInfo.version}'",
        )
    }

    @Test
    fun name_includes_manufacturer_and_model_brackets() {
        // Format: "Android (manufacturer model)" — must contain "(...)"
        assertTrue(
            PlatformInfo.name.contains("(") && PlatformInfo.name.contains(")"),
            "expected '(manufacturer model)' bracket, got '${PlatformInfo.name}'",
        )
    }
}
