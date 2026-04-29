/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

@file:OptIn(kotlin.contracts.ExperimentalContracts::class, kotlin.ExperimentalUnsignedTypes::class)

package com.omnifret.gplayer.test

import com.omnifret.gplayer.api.GPlayer
import com.omnifret.gplayer.api.PlaybackBuilder
import com.omnifret.gplayer.api.PlaybackEventListener
import com.omnifret.gplayer.model.Score
import kotlin.contracts.ExperimentalContracts
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Load `test-data/<path>` from the test bundle and run [GPlayer.parseScore] on it. */
@OptIn(ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
internal fun parseFixture(path: String): Score =
    GPlayer.parseScore(loadTestFixture(path))

/**
 * Lightweight structural assertion. Each parameter is opt-in: pass null
 * to skip checking that property. Using a single helper keeps the per-
 * format shape tests one-liners.
 */
internal fun assertScoreShape(
    score: Score,
    title: String? = null,
    minTracks: Int? = null,
    minMasterBars: Int? = null,
    tempo: Double? = null,
) {
    if (title != null) assertEquals(title, score.title, "score.title")
    if (minTracks != null) {
        assertTrue(
            score.tracks.length.toInt() >= minTracks,
            "expected ≥$minTracks tracks, got ${score.tracks.length.toInt()}",
        )
    }
    if (minMasterBars != null) {
        assertTrue(
            score.masterBars.length.toInt() >= minMasterBars,
            "expected ≥$minMasterBars master bars, got ${score.masterBars.length.toInt()}",
        )
    }
    if (tempo != null) assertEquals(tempo, score.tempo, "score.tempo")
}

/**
 * Run [PlaybackBuilder.generate] on [score] and collect every callback
 * into an in-order list. Replaces the per-test listener boilerplate.
 */
@OptIn(ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
internal fun collectPlaybackEvents(score: Score): List<PlaybackEvent> {
    val events = mutableListOf<PlaybackEvent>()
    val listener = object : PlaybackEventListener {
        override fun onTimeSignature(tick: Double, numerator: Int, denominator: Int) {
            events += PlaybackEvent.TimeSignature(tick, numerator, denominator)
        }
        override fun onTempo(tick: Double, bpm: Double) {
            events += PlaybackEvent.Tempo(tick, bpm)
        }
        override fun onNote(
            track: Int,
            channel: Int,
            startTick: Double,
            lengthTicks: Double,
            midiKey: Int,
            velocity: Int,
        ) {
            events += PlaybackEvent.Note(track, channel, startTick, lengthTicks, midiKey, velocity)
        }
        override fun onProgramChange(track: Int, tick: Double, channel: Int, program: Int) {
            events += PlaybackEvent.ProgramChange(track, tick, channel, program)
        }
        override fun onBend(track: Int, tick: Double, channel: Int, value: Int) {
            events += PlaybackEvent.Bend(track, tick, channel, value)
        }
        override fun onNoteBend(
            track: Int,
            tick: Double,
            channel: Int,
            midiKey: Int,
            value: Int,
        ) {
            events += PlaybackEvent.NoteBend(track, tick, channel, midiKey, value)
        }
        override fun onControlChange(
            track: Int,
            tick: Double,
            channel: Int,
            controller: Int,
            value: Int,
        ) {
            events += PlaybackEvent.ControlChange(track, tick, channel, controller, value)
        }
        override fun onRest(track: Int, tick: Double, channel: Int) {
            events += PlaybackEvent.Rest(track, tick, channel)
        }
        override fun onTrackEnd(track: Int, tick: Double) {
            events += PlaybackEvent.TrackEnd(track, tick)
        }
    }
    PlaybackBuilder(score).generate(listener)
    return events
}

/**
 * Asserts two scores are *structurally* equivalent for round-trip tests:
 * track count, master bar count, and the per-voice note pitch sequence
 * on every bar. Tolerates exporter timing rounding and metadata loss.
 */
@OptIn(ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
internal fun assertScoresEquivalent(a: Score, b: Score) {
    assertEquals(a.tracks.length.toInt(), b.tracks.length.toInt(), "track count")
    assertEquals(a.masterBars.length.toInt(), b.masterBars.length.toInt(), "master bar count")

    val aTrackCount = a.tracks.length.toInt()
    for (t in 0 until aTrackCount) {
        val aTrack = a.tracks[t]
        val bTrack = b.tracks[t]
        val aStaffCount = aTrack.staves.length.toInt()
        assertEquals(aStaffCount, bTrack.staves.length.toInt(), "track[$t].staves.length")
        for (s in 0 until aStaffCount) {
            val aStaff = aTrack.staves[s]
            val bStaff = bTrack.staves[s]
            val aBarCount = aStaff.bars.length.toInt()
            assertEquals(aBarCount, bStaff.bars.length.toInt(), "track[$t].staves[$s].bars.length")
            for (bar in 0 until aBarCount) {
                val aPitches = collectPitches(aStaff.bars[bar])
                val bPitches = collectPitches(bStaff.bars[bar])
                assertEquals(aPitches, bPitches, "track[$t].staves[$s].bars[$bar] pitch sequence")
            }
        }
    }
}

@OptIn(ExperimentalContracts::class, ExperimentalUnsignedTypes::class)
private fun collectPitches(bar: com.omnifret.gplayer.model.Bar): List<Int> {
    val out = mutableListOf<Int>()
    val voiceCount = bar.voices.length.toInt()
    for (v in 0 until voiceCount) {
        val voice = bar.voices[v]
        val beatCount = voice.beats.length.toInt()
        for (i in 0 until beatCount) {
            val beat = voice.beats[i]
            val noteCount = beat.notes.length.toInt()
            for (n in 0 until noteCount) {
                out += beat.notes[n].displayValue.toInt()
            }
        }
    }
    return out
}
