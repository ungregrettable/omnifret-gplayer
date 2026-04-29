/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.api

import com.omnifret.gplayer.test.parseFixture
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * One smoke test per supported binary/text format. Each asserts that
 * `parseScore` returns a non-null Score with at least one track —
 * detecting flat-out parser breakage on a real fixture from upstream.
 *
 * Runs on commonTest, so every assertion fires on both Android JVM and
 * Kotlin/Native iOS sim.
 */
class PerFormatSmokeTest {

    @Test fun gp3() = smoke("guitarpro3/score-info.gp3")
    @Test fun gp4() = smoke("guitarpro4/score-info.gp4")
    @Test fun gp5() = smoke("guitarpro5/score-info.gp5")
    @Test fun gp6() = smoke("guitarpro6/score-info.gpx")
    @Test fun gp7() = smoke("guitarpro7/score-info.gp")
    @Test fun gp8_bank() = smoke("guitarpro8/bank.gp")
    @Test fun musicxml3() = smoke("musicxml3/first-bar-tempo.musicxml")
    @Test fun musicxml4() = smoke("musicxml4/partwise-basic.xml")

    private fun smoke(path: String) {
        val score = parseFixture(path)
        assertNotNull(score, "parseFixture returned null for $path")
        assertTrue(score.tracks.length > 0.0, "no tracks parsed from $path")
    }
}
