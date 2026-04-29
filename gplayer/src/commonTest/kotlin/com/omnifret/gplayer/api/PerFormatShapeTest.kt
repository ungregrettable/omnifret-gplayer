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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Per-format structural-shape tests. Each parses a curated fixture and
 * asserts properties that are stable enough to be regression signals
 * without being so specific they break on every alphaTab upstream
 * tweak. We don't pin every note count — just the broad invariants
 * (track ≥ 1, bar ≥ 1, tempo > 0, title non-empty).
 *
 * The exact title strings are pinned where alphaTab's upstream fixtures
 * have stable, well-known values; if upstream changes them, update here
 * and treat it as an upstream-tracking signal, not a bug.
 */
class PerFormatShapeTest {

    // ----- Guitar Pro 3 -----

    @Test
    fun gp3_score_info_has_full_metadata() {
        val score = parseFixture("guitarpro3/score-info.gp3")
        assertEquals("Title", score.title)
        assertEquals("Subtitle", score.subTitle)
        assertEquals("Artist", score.artist)
        assertEquals("Album", score.album)
        assertTrue(score.tracks.length >= 1.0)
    }

    @Test
    fun gp3_notes_parses_with_tracks() {
        val score = parseFixture("guitarpro3/notes.gp3")
        assertTrue(score.tracks.length >= 1.0, "expected ≥1 track, got ${score.tracks.length}")
        assertTrue(score.masterBars.length >= 1.0, "expected ≥1 master bar")
    }

    @Test
    fun gp3_bends_parses_with_tracks() {
        val score = parseFixture("guitarpro3/bends.gp3")
        assertTrue(score.tracks.length >= 1.0)
        assertTrue(score.masterBars.length >= 1.0)
    }

    // ----- Guitar Pro 4 -----

    @Test
    fun gp4_score_info_has_metadata() {
        val score = parseFixture("guitarpro4/score-info.gp4")
        assertEquals("Title", score.title)
        assertEquals("Subtitle", score.subTitle)
    }

    @Test
    fun gp4_tuplets_parses_with_tempo() {
        val score = parseFixture("guitarpro4/tuplets.gp4")
        assertTrue(score.tempo > 0.0, "expected positive tempo, got ${score.tempo}")
    }

    // ----- Guitar Pro 5 -----

    @Test
    fun gp5_score_info_has_metadata() {
        val score = parseFixture("guitarpro5/score-info.gp5")
        assertEquals("Title", score.title)
        assertEquals("Subtitle", score.subTitle)
        assertEquals("Artist", score.artist)
        assertEquals("Album", score.album)
        assertEquals("Words", score.words)
        assertEquals("Music", score.music)
        assertEquals("Copyright", score.copyright)
    }

    @Test
    fun gp5_notes_parses() {
        val score = parseFixture("guitarpro5/notes.gp5")
        assertTrue(score.tracks.length >= 1.0)
        assertTrue(score.tempo > 0.0)
    }

    @Test
    fun gp5_chords_parses() {
        val score = parseFixture("guitarpro5/chords.gp5")
        assertTrue(score.tracks.length >= 1.0)
        assertTrue(score.masterBars.length >= 1.0)
    }

    // ----- Guitar Pro 6 (.gpx) -----

    @Test
    fun gp6_score_info_has_metadata() {
        val score = parseFixture("guitarpro6/score-info.gpx")
        assertEquals("Title", score.title)
        assertTrue(score.tracks.length >= 1.0)
    }

    @Test
    fun gp6_chords_parses() {
        val score = parseFixture("guitarpro6/chords.gpx")
        assertTrue(score.tracks.length >= 1.0)
        assertTrue(score.masterBars.length >= 1.0)
    }

    // ----- Guitar Pro 7 (.gp) -----

    @Test
    fun gp7_score_info_has_metadata() {
        val score = parseFixture("guitarpro7/score-info.gp")
        assertEquals("Title", score.title)
        assertTrue(score.tracks.length >= 1.0)
    }

    @Test
    fun gp7_chords_parses_with_tempo() {
        val score = parseFixture("guitarpro7/chords.gp")
        assertTrue(score.tracks.length >= 1.0)
        assertTrue(score.tempo > 0.0)
    }

    // ----- Guitar Pro 8 (.gp) -----

    @Test
    fun gp8_bank_parses_with_tracks() {
        val score = parseFixture("guitarpro8/bank.gp")
        assertTrue(score.tracks.length >= 1.0)
        assertTrue(score.masterBars.length >= 1.0)
    }

    @Test
    fun gp8_directions_parses() {
        val score = parseFixture("guitarpro8/directions.gp")
        assertTrue(score.tracks.length >= 1.0)
    }

    @Test
    fun gp8_transposition_parses() {
        val score = parseFixture("guitarpro8/transposition-tonality-c.gp")
        assertTrue(score.tracks.length >= 1.0)
    }

    // ----- MusicXML -----

    @Test
    fun musicxml3_first_bar_tempo_parses() {
        val score = parseFixture("musicxml3/first-bar-tempo.musicxml")
        assertTrue(score.tracks.length >= 1.0)
        assertTrue(score.tempo > 0.0)
    }

    @Test
    fun musicxml3_full_bar_rest_parses() {
        val score = parseFixture("musicxml3/full-bar-rest.musicxml")
        assertTrue(score.tracks.length >= 1.0)
        assertTrue(score.masterBars.length >= 1.0)
    }

    @Test
    fun musicxml4_partwise_basic_parses() {
        val score = parseFixture("musicxml4/partwise-basic.xml")
        assertTrue(score.tracks.length >= 1.0)
    }

    @Test
    fun musicxml4_anacrusis_parses() {
        val score = parseFixture("musicxml4/partwise-anacrusis.xml")
        assertTrue(score.tracks.length >= 1.0)
    }

    @Test
    fun musicxml4_midi_bank_parses() {
        val score = parseFixture("musicxml4/midi-bank.xml")
        assertTrue(score.tracks.length >= 1.0)
    }
}
