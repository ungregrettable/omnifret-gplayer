/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

package com.omnifret.gplayer.test

import kotlin.contracts.ExperimentalContracts
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * P0 sanity checks: confirm the fixture loader resolves a real binary
 * fixture on whichever platform the test is running, and that the
 * result is byte-identical (parsing-independent).
 *
 * If this passes, every higher-layer test using `parseFixture(...)`
 * has a working substrate.
 */
@OptIn(ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
class InfrastructureTest {

    @Test
    fun loads_a_gp5_fixture_from_test_resources() {
        val bytes = loadTestFixture("guitarpro5/score-info.gp5")
        assertTrue(bytes.isNotEmpty(), "fixture is empty")
        // GP5 files start with a Pascal-style version string. Sanity-
        // check that this is plausibly a GP5 file rather than some other
        // resource the loader stumbled into.
        val header = bytes.copyOfRange(0, minOf(bytes.size, 30)).decodeToString()
        assertTrue(
            header.contains("FICHIER GUITAR PRO"),
            "header doesn't look like a Guitar Pro file: $header",
        )
    }

    @Test
    fun parses_gp5_fixture() {
        val score = parseFixture("guitarpro5/score-info.gp5")
        assertNotNull(score)
        assertTrue(score.tracks.length > 0.0, "expected at least one track")
    }
}
