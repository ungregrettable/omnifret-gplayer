# omnifret-gplayer

A Kotlin Multiplatform library for parsing Guitar Pro / MusicXML / AlphaTex /
Capella score files, generating MIDI playback events, and rendering notation
to SVG. Targets Android (AAR) and iOS (XCFramework) and is consumed by the
proprietary OmniFret app.

This repository is a derivative work of [alphaTab](https://github.com/CoderLine/alphaTab)
(MPL-2.0). It exists as a public, MPL-2.0-licensed source distribution so
OmniFret can ship the binary while satisfying alphaTab's license terms.

## Status

| Component | Status |
|---|---|
| Score parsing (GP3-5, GP7-8, GPX, MusicXML, Capella, AlphaTex) | ✅ Working |
| Score domain model | ✅ Working |
| Playback event generation (note-on/off, tempo, control changes) | ✅ Working |
| SVG rendering (CssFontSvg engine) | ✅ Working |
| Compose Canvas / Core Graphics native rendering | ⏳ Future work |
| Android AAR | ✅ `gplayer/build/outputs/aar/notation-release.aar` |
| iOS XCFramework | ⚠️ Compiles to klib; binary linking requires full Xcode |

## Public API

All consumer-facing types live under `com.omnifret.gplayer.api.*`:

```kotlin
import com.omnifret.gplayer.api.Notation
import com.omnifret.gplayer.api.PlaybackBuilder
import com.omnifret.gplayer.api.PlaybackEventListener
import com.omnifret.gplayer.api.ScoreSvgRenderer

// Parse — auto-detects GP3-5/GP7-8/GPX/MusicXML/Capella/AlphaTex
val score = Notation.parseScore(bytes)            // any binary or text format
val score = Notation.parseAlphaTex(texSource)     // text format

// Render to SVG (chunked for streaming display)
val rendered = ScoreSvgRenderer(score, widthPx = 970.0).render()
rendered.chunks.forEach { chunk -> displaySvg(chunk.svg) }

// Playback — generate MIDI events, drive your own audio engine
PlaybackBuilder(score).generate(object : PlaybackEventListener {
    override fun onNote(track, channel, startTick, lengthTicks, midiKey, velocity) {
        myAudioEngine.playNote(midiKey, durationFromTicks(lengthTicks))
    }
    override fun onTempo(tick, bpm) { /* update tempo map */ }
    override fun onTimeSignature(tick, numerator, denominator) { /* … */ }
})
```

## Integration into OmniFret

### Android (`:app` module)

After publishing the AAR locally:

```kotlin
// gplayer/build.gradle.kts publishes to mavenLocal automatically
// once we wire up `mavenPublishing`. For now, depend on the AAR file:
dependencies {
    implementation(files("/path/to/omnifret-gplayer/notation/build/outputs/aar/notation-release.aar"))
    implementation(libs.kotlinx.coroutines.core)
}
```

Wire `PlaybackEventListener.onNote` to OmniFret's existing `AudioEngine.playNote(midi, durationSec)` (see `~/dev/OmniFret/app/src/main/java/com/omnifret/music/Audio.kt`). The Karplus-Strong synth in `commonMain/.../AudioDsp.kt` is the underlying voice.

### iOS (`iosApp`)

```bash
# Build the iOS framework (requires full Xcode, not just Command Line Tools).
./gradlew :gplayer:assembleOmniFretGplayerReleaseXCFramework
# Output: gplayer/build/XCFrameworks/release/OmniFretGplayer.xcframework
```

Drop the XCFramework into Xcode (or wire as a CocoaPod). Implement `PlaybackEventListener` in Swift by calling into your existing `IOSBackingTrackEngine`.

### SVG display

The SVG output is plain markup — render it on each platform with whatever SVG library you already have:

- **Android**: [AndroidSvg](https://github.com/BigBadaboom/androidsvg) renders `String → Drawable`.
- **iOS**: [SVGKit](https://github.com/SVGKit/SVGKit) or `WKWebView` with a small HTML wrapper.

For native canvas rendering (better perf, avoids the SVG round-trip), implement `com.omnifret.gplayer.platform.ICanvas` and register it via `Environment.renderEngines.set("custom", RenderEngineFactory(...))`. This is the path Phase 3 of the original plan envisioned but left as future work.

## Development

```bash
./gradlew :gplayer:assembleRelease           # Android AAR
./gradlew :gplayer:testDebugUnitTest         # Smoke tests on JVM
./gradlew :gplayer:compileKotlinIosArm64     # iOS klib (no Xcode needed)
./gradlew :gplayer:compileKotlinIosSimulatorArm64
```

### Re-snapshotting from upstream

The `commonMain` source under `com/omnifret/gplayer/` (excluding `api/`, `EnvironmentPartials.kt`, `generated/VersionInfo.kt`, and the rewritten files in `core/ecmaScript/`, `core/Globals.kt`, `core/TypeHelper.kt`, `io/TypeConversions.kt`, `io/JsonHelperPartials.kt`, `importer/alphaTex/AlphaTex1EnumMappingsPartials.kt`, `platform/svg/FontSizesPartials.kt`, `collections/List.kt`, `Environment.kt`'s skia patch) is a snapshot of alphaTab's transpiler output.

To update:
1. `cd ~/dev/alphaTab && npm install && npm run transpile -w @coderline/alphatab-kotlin`
2. Diff `~/dev/alphaTab/packages/kotlin/src/android/src/main-generated/java` against `gplayer/src/commonMain/kotlin/com/omnifret/notation/`.
3. Apply non-conflicting upstream changes; re-resolve any local rewrites.
4. Run the rename script (TODO: bake the perl one-liners from the initial port into a script under `tools/resnapshot.sh`).

### What was modified vs. the upstream snapshot

- `Environment.kt`: removed AlphaSkia render-engine registration (we don't ship the AlphaSkia JNI dependency).
- `core/Globals.kt`: replaced `java.nio.ByteBuffer`, `java.text.DecimalFormat`, `java.util.regex` with KMP-portable equivalents.
- `core/TypeHelper.kt`: replaced `v.javaClass` with KClass-based type info.
- `core/ecmaScript/RegExp.kt`: rebuilt on `kotlin.text.Regex` (was `java.util.regex.Pattern`).
- `core/ecmaScript/DataView.kt`, `Float32Array.kt`, `Object.kt`, `CoreString.kt`, `Date.kt`: removed JVM-only APIs.
- `io/TypeConversions.kt`: replaced `ByteBuffer` byte-order reads with hand-rolled little/big-endian.
- `collections/List.kt`: `ArrayListWithRemoveRange` uses `MutableList` interface delegation (Kotlin/Native disallows extending `kotlin.collections.ArrayList`).
- `importer/alphaTex/AlphaTex1EnumMappingsPartials.kt`: replaced `java.lang.reflect`-based enum lookup with explicit registration.
- `platform/svg/FontSizesPartials.kt`: replaced AlphaSkia text-measurement with a `MusicFontMeasurer` injection point.
- `platform/javascript/`, `platform/worker/`, `platform/skia/`: deleted entirely (Web/JNI-only, not consumed by Android or iOS).
- `EnvironmentPartials.kt`: written from scratch for KMP (the original was Android-specific).

## License

This project is distributed under the [Mozilla Public License 2.0](LICENSE).

The bulk of the source code is derived from [alphaTab](https://github.com/CoderLine/alphaTab)
(© Daniel Kuschny / CoderLine), which is also under MPL-2.0. The KMP-port-specific
code in `com/omnifret/notation/api/`, `EnvironmentPartials.kt`, the rewritten
`core/ecmaScript/*` files, and the Android/iOS `PlatformInfo` actuals are © 2026
omnifret-gplayer contributors and are also MPL-2.0.

If you redistribute this in compiled form, you must keep the source available
under MPL-2.0 (which is satisfied by linking back to this repository).
