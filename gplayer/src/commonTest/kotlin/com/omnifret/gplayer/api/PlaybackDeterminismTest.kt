/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.api

import com.omnifret.gplayer.test.PlaybackEvent
import com.omnifret.gplayer.test.collectPlaybackEvents
import com.omnifret.gplayer.test.parseFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The contract on [PlaybackBuilder.generate] (per Playback.kt:97-103) is
 * "synchronous and deterministic". A consumer that wires its audio
 * engine to onNote and reuses the same Score must get the same event
 * stream every time. Any non-determinism — coroutine scheduling, hash
 * iteration order, time-based ID generation — would silently break
 * playback parity across Android/iOS or across runs.
 *
 * Also asserts the event stream is non-empty in the right places: at
 * least one Tempo and one TimeSignature event must be emitted from a
 * GP fixture that defines them.
 */
class PlaybackDeterminismTest {

    @Test
    fun two_runs_on_same_score_produce_identical_events() {
        val score = parseFixture("guitarpro5/notes.gp5")
        val first = collectPlaybackEvents(score)
        val second = collectPlaybackEvents(score)
        assertEquals(first.size, second.size, "event count differs across runs")
        assertEquals(first, second, "event sequences differ across runs")
    }

    @Test
    fun two_runs_on_separately_parsed_scores_produce_identical_events() {
        // Guards against hidden score-instance state (e.g. a tick lookup
        // that retains running-total state from a previous render).
        val a = parseFixture("guitarpro5/notes.gp5")
        val b = parseFixture("guitarpro5/notes.gp5")
        assertEquals(collectPlaybackEvents(a), collectPlaybackEvents(b))
    }

    @Test
    fun gp5_score_emits_tempo_and_timesig_events() {
        val score = parseFixture("guitarpro5/notes.gp5")
        val events = collectPlaybackEvents(score)
        val tempos = events.filterIsInstance<PlaybackEvent.Tempo>()
        val timeSigs = events.filterIsInstance<PlaybackEvent.TimeSignature>()
        assertTrue(tempos.isNotEmpty(), "expected ≥1 Tempo event")
        assertTrue(timeSigs.isNotEmpty(), "expected ≥1 TimeSignature event")
    }

    @Test
    fun gp5_score_emits_note_events() {
        val score = parseFixture("guitarpro5/notes.gp5")
        val events = collectPlaybackEvents(score)
        val notes = events.filterIsInstance<PlaybackEvent.Note>()
        assertTrue(notes.isNotEmpty(), "expected ≥1 Note event from notes.gp5")
        // Sanity-check the values fall in MIDI range (0-127).
        for (n in notes) {
            assertTrue(n.midiKey in 0..127, "midiKey out of range: ${n.midiKey}")
            assertTrue(n.velocity in 0..127, "velocity out of range: ${n.velocity}")
        }
    }

    @Test
    fun gp5_chords_emits_concurrent_notes() {
        val score = parseFixture("guitarpro5/chords.gp5")
        val events = collectPlaybackEvents(score)
        val notes = events.filterIsInstance<PlaybackEvent.Note>()
        // A chord fixture should have multiple notes starting at the
        // same tick (i.e. groups where startTick repeats).
        val byStartTick = notes.groupBy { it.startTick }
        val concurrentGroups = byStartTick.values.filter { it.size >= 2 }
        assertTrue(
            concurrentGroups.isNotEmpty(),
            "expected ≥1 group of concurrent notes in chords.gp5; got ${notes.size} notes spread across ${byStartTick.size} ticks",
        )
    }

    @Test
    fun playback_emits_track_end_for_every_track() {
        val score = parseFixture("guitarpro5/notes.gp5")
        val events = collectPlaybackEvents(score)
        val ends = events.filterIsInstance<PlaybackEvent.TrackEnd>().map { it.track }.toSet()
        val trackCount = score.tracks.length.toInt()
        // The handler emits onTrackEnd once per track that produced events;
        // tracks with no notes also still get one. Just assert the count
        // matches.
        assertEquals(
            (0 until trackCount).toSet(),
            ends,
            "expected TrackEnd for each of $trackCount tracks",
        )
    }

    @Test
    fun playback_event_count_stable_across_runs_for_each_format() {
        // Run multiple formats and assert determinism for each.
        for (fixture in listOf(
            "guitarpro3/notes.gp3",
            "guitarpro5/notes.gp5",
            "guitarpro7/notes.gp",
        )) {
            val score = parseFixture(fixture)
            val first = collectPlaybackEvents(score)
            val second = collectPlaybackEvents(score)
            assertEquals(first.size, second.size, "non-deterministic for $fixture")
            assertEquals(first, second, "events differ for $fixture")
        }
    }
}
