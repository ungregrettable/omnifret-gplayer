/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 omnifret-gplayer contributors.
 */

package com.omnifret.gplayer.test

/**
 * Captured `PlaybackEventListener` callback as a value type. Keeps every
 * field a `PlaybackBuilder` listener exposes so tests can assert exact
 * equality across two runs (determinism check) without writing a custom
 * listener each time.
 */
internal sealed class PlaybackEvent {
    data class TimeSignature(val tick: Double, val numerator: Int, val denominator: Int) : PlaybackEvent()
    data class Tempo(val tick: Double, val bpm: Double) : PlaybackEvent()
    data class Note(
        val track: Int,
        val channel: Int,
        val startTick: Double,
        val lengthTicks: Double,
        val midiKey: Int,
        val velocity: Int,
    ) : PlaybackEvent()
    data class ProgramChange(val track: Int, val tick: Double, val channel: Int, val program: Int) : PlaybackEvent()
    data class Bend(val track: Int, val tick: Double, val channel: Int, val value: Int) : PlaybackEvent()
    data class NoteBend(
        val track: Int,
        val tick: Double,
        val channel: Int,
        val midiKey: Int,
        val value: Int,
    ) : PlaybackEvent()
    data class ControlChange(
        val track: Int,
        val tick: Double,
        val channel: Int,
        val controller: Int,
        val value: Int,
    ) : PlaybackEvent()
    data class Rest(val track: Int, val tick: Double, val channel: Int) : PlaybackEvent()
    data class TrackEnd(val track: Int, val tick: Double) : PlaybackEvent()
}
