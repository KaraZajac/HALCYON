# HALCYON

A box-breathing companion for Android that does exactly one thing, beautifully.

Tap the screen to begin. A luminous orb breathes with you — swelling on the
inhale, holding full, settling on the exhale, holding empty — while a glowing
comet traces the four sides of a square, one side per phase. Tap again to stop.
That's the whole app.

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

Everything is drawn on a single Compose `Canvas` from one frame clock:

- a **breathing orb** — a layered radial-gradient sphere with core, rim, and a
  lit-from-above highlight — scaled by eased inhale/exhale
- the **box** — a faint rounded square the comet traces at constant speed, one
  lap per 16-second cycle
- a **comet** with a fading tail marking your progress around the box
- **ripples** cast off at each phase boundary
- an **ambient mote field** that drifts, twinkles, and gently inhales with you
- a **breath glow** that blooms behind the orb
- an accent hue that **glides** through the Catppuccin Mocha palette across the
  breath (blue → lavender → teal → sapphire)

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
