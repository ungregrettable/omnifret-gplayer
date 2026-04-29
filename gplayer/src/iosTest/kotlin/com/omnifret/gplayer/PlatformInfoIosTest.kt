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
 * iOS `actual` for `PlatformInfo`. Asserts that `UIDevice.currentDevice
 * .model` and `.systemVersion` are queried correctly. Under the iOS
 * simulator both are populated with realistic values
 * (e.g. "iPhone Simulator" / "17.0").
 */
class PlatformInfoIosTest {

    @Test
    fun name_starts_with_iOS() {
        assertTrue(
            PlatformInfo.name.startsWith("iOS"),
            "expected iOS prefix, got '${PlatformInfo.name}'",
        )
    }

    @Test
    fun name_includes_device_model_brackets() {
        assertTrue(
            PlatformInfo.name.contains("(") && PlatformInfo.name.contains(")"),
            "expected '(model)' bracket, got '${PlatformInfo.name}'",
        )
    }

    @Test
    fun version_is_non_empty() {
        // UIDevice.currentDevice.systemVersion is "17.0" / "16.4" /
        // similar — never empty on a booted simulator.
        assertTrue(
            PlatformInfo.version.isNotEmpty(),
            "expected non-empty systemVersion",
        )
    }
}
