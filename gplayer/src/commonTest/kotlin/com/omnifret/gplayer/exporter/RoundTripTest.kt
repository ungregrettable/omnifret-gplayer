/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.exporter

import com.omnifret.gplayer.api.GPlayer
import com.omnifret.gplayer.test.assertScoresEquivalent
import com.omnifret.gplayer.test.parseFixture
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Round-trip tests: parse → export → parse → compare.
 *
 * Per the user-confirmed plan, these enforce *structural* equivalence
 * (track / bar / pitch sequence) rather than exact equality. Exporters
 * round timing values; bit-exact equality would fail on legitimate
 * normalisation.
 */
class RoundTripTest {

    // ----- Gp7Exporter round trips -----

    @Test
    fun gp7_exporter_round_trips_gp7_notes_fixture() {
        val original = parseFixture("guitarpro7/notes.gp")
        val bytes = Gp7Exporter().export(original).buffer.asByteArray()
        assertTrue(bytes.isNotEmpty(), "Gp7Exporter produced empty bytes")

        val reparsed = GPlayer.parseScore(bytes)
        assertScoresEquivalent(original, reparsed)
    }

    @Test
    fun gp7_exporter_round_trips_gp5_chords() {
        // GP5 → export to GP7 → reparse. The exporter should normalize
        // GP5-specific data into the GP7 model; structural shape stays.
        val original = parseFixture("guitarpro5/chords.gp5")
        val bytes = Gp7Exporter().export(original).buffer.asByteArray()
        val reparsed = GPlayer.parseScore(bytes)
        assertScoresEquivalent(original, reparsed)
    }

    @Test
    fun gp7_exporter_round_trips_gp4_notes() {
        val original = parseFixture("guitarpro4/notes.gp4")
        val bytes = Gp7Exporter().export(original).buffer.asByteArray()
        val reparsed = GPlayer.parseScore(bytes)
        assertScoresEquivalent(original, reparsed)
    }

    @Test
    fun gp7_exporter_preserves_title_metadata() {
        val original = parseFixture("guitarpro5/score-info.gp5")
        val bytes = Gp7Exporter().export(original).buffer.asByteArray()
        val reparsed = GPlayer.parseScore(bytes)
        // Title, artist, etc. are core metadata — must survive a round
        // trip even though the exporter is allowed to round timing.
        assertTrue(reparsed.title.isNotEmpty(), "title was lost on export")
        // Use a relaxed comparison: original title might be normalized.
        assertTrue(
            reparsed.title.equals(original.title, ignoreCase = true),
            "title changed: '${original.title}' → '${reparsed.title}'",
        )
    }

    // ----- GPlayerTexExporter round trips -----

    @Test
    fun gplayertex_exporter_round_trips_gp5_notes() {
        val original = parseFixture("guitarpro5/notes.gp5")
        val tex = GPlayerTexExporter().exportToString(original)
        assertTrue(tex.isNotEmpty(), "exporter produced empty string")

        val reparsed = GPlayer.parseGPlayerTex(tex)
        assertScoresEquivalent(original, reparsed)
    }

    @Test
    fun gplayertex_exporter_handles_gp7_chords_without_crashing() {
        // GPlayerTex's text grammar is lossier than the GP binary format —
        // some chord/voicing details don't round-trip cleanly. We assert
        // export-then-reparse completes successfully and produces at least
        // one master bar; full equivalence is left to the GP7 round-trip.
        val original = parseFixture("guitarpro7/chords.gp")
        val tex = GPlayerTexExporter().exportToString(original)
        assertTrue(tex.isNotEmpty(), "tex export was empty")
        val reparsed = GPlayer.parseGPlayerTex(tex)
        assertTrue(
            reparsed.tracks.length > 0.0,
            "reparsed score has no tracks",
        )
        assertTrue(
            reparsed.masterBars.length > 0.0,
            "reparsed score has no master bars",
        )
    }

    // ----- Sanity: exporters don't return empty/garbage -----

    @Test
    fun gp7_export_starts_with_zip_signature() {
        // GP7 files are ZIP archives. The first byte should be 'P' (0x50)
        // and the second 'K' (0x4B) — the universal ZIP magic header.
        val bytes = Gp7Exporter().export(
            parseFixture("guitarpro5/notes.gp5")
        ).buffer.asByteArray()
        assertTrue(bytes.size >= 4, "exporter produced ≤4 bytes")
        assertNotNull(bytes)
        // The ZIP magic is "PK\x03\x04" (regular file) or "PK\x05\x06"
        // (empty archive); either way starts with PK.
        assertTrue(
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte(),
            "expected ZIP magic 'PK', got 0x${bytes[0].toUByte().toString(16)}${bytes[1].toUByte().toString(16)}",
        )
    }

    @Test
    fun gplayertex_export_contains_metadata_directives() {
        val tex = GPlayerTexExporter().exportToString(
            parseFixture("guitarpro5/score-info.gp5")
        )
        // GPlayerTex's metadata uses backslash-prefixed directives.
        // \title at minimum should appear for a fixture with a title.
        assertTrue(tex.contains("\\title"), "\\title directive missing")
    }
}
