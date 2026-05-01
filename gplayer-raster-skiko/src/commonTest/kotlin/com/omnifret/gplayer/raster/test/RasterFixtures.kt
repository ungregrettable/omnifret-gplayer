/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

package com.omnifret.gplayer.raster.test

/**
 * Load a binary test resource by relative path under
 * `gplayer-raster-skiko/src/commonTest/resources/`. Same shape as
 * `:gplayer`'s `loadTestFixture` but scoped to this module's resources.
 */
internal expect fun loadRasterTestResource(path: String): ByteArray
