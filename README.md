# HALCYON

A box-breathing companion for Android that does exactly one thing, beautifully.

Tap the screen to begin. The whole screen is a field of fireflies, and the
field breathes with you — brightening and turning a vivid green as you inhale,
settling into a muted sage as you exhale. Tap again to stop. That's the whole
app.

**Box breathing** (a.k.a. square breathing) is a 4–4–4–4 pattern used by
clinicians, athletes, and first responders to steady the nervous system:

```
Breathe in  (4s)  →  Hold  (4s)  →  Breathe out  (4s)  →  Hold  (4s)  →  repeat
```

## No permissions. Nothing asked of you but a tap.

HALCYON requests **zero** Android permissions — no internet, no Bluetooth, no
sensors, no storage, no notifications. There is no account, no server, no
analytics, and no telemetry. It runs entirely offline and keeps no data. The
only interaction it ever needs is a tap to start and a tap to stop. The screen
stays awake while a session runs (no wake-lock permission required).

## The animation

Everything is drawn on a single Compose `Canvas` from one frame clock — a
full-screen field of ~420 fireflies:

- **depth-layered**: distant lights are tiny, dim, and slow; near ones are
  larger, brighter, and carry a soft halo
- each firefly **rises on warm air** (70 seconds to 4 minutes per screen,
  wrapping seamlessly) and **wanders** on two-octave sine paths, so nothing
  ever moves in a straight line
- each **blinks** on its own slow, dreamy clock, and every few minutes swells
  through a long soft **flare** — somewhere on screen one is always blooming
- the **breath is the whole field**: on the inhale everything brightens and
  gently swells; on the exhale it settles and dims — Catppuccin Mocha greens,
  from muted sage (empty) to vivid green cresting into mint (full)
- a **vignette** sinks the edges into the night, and an edgeless central bloom
  glows faintly with the lungs

The timing model (`breath/BreathCycle.kt`) is pure, Android-free, and
unit-tested, so the cadence stays exact.

## Build

No Android SDK is needed locally — CI is the compiler. Every push builds a debug
APK; pushing a `v*` tag publishes it to a GitHub Release. The committed
`debug.keystore` (password `android`, the well-known debug key) means CI and
local builds sign identically, so updates install in place.

```
./gradlew :app:assembleDebug     # debug APK at app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest # breath-model unit tests
```

- Kotlin 2.0.21 · AGP 8.7.3 · Compose · `compileSdk` 35 · `minSdk` 26
- Package `org.soulstone.halcyon`

## License

TBD.
