# `core:ble` KMP Strategy Analysis

> Date: 2026-03-10
>
> Context: Nordic responded to [our inquiry](https://github.com/NordicSemiconductor/Kotlin-BLE-Library/issues/183#issuecomment-4030710057) confirming KMP is on their roadmap but not yet available, and recommended KABLE for projects needing KMP now.

## Current State — Already Well-Architected

Our `core:ble` is **already one of the best-structured modules in the repo** for KMP:

| Layer | What exists | KMP-ready? |
|---|---|---|
| `commonMain` interfaces | `BleConnection`, `BleScanner`, `BleDevice`, `BleConnectionFactory`, `BluetoothRepository`, `BleConnectionState`, `BleService`, `BleRetry`, `MeshtasticBleConstants` | ✅ Pure Kotlin — zero platform imports |
| `androidMain` implementations | `AndroidBleConnection`, `AndroidBleScanner`, `AndroidBleDevice`, `AndroidBleConnectionFactory`, `AndroidBluetoothRepository`, `AndroidBleService` | ✅ Properly isolated |
| DI | `CoreBleModule` (commonMain), `CoreBleAndroidModule` (androidMain) | ✅ Clean split |

**The abstraction boundary is already drawn exactly where it needs to be.** No Nordic types leak into `commonMain`.

## The JVM Target Question

Adding `jvm()` to `core:ble` is **easy right now** — the `commonMain` has zero platform dependencies. The only blocker would be providing `jvmMain` implementations of the BLE interfaces, but for JVM (headless/desktop) we have two options:

### Option A: No-op / Stub JVM Implementation (Minimal, Unblocks CI Now)

Add `jvm()` and provide no-op or stub implementations in `jvmMain` (or don't — `commonMain` is just interfaces, it'll compile fine with no `jvmMain` source at all). Consumers on JVM would get `BleScanner`/`BleConnection` etc. from DI; a headless JVM app would simply not wire BLE into the graph.

**Effort: ~10 minutes. Unblocks JVM smoke compile immediately.**

### Option B: KABLE-backed JVM Implementation (Real Desktop BLE)

Replace or supplement the Nordic `androidMain` implementation with KABLE in `commonMain` or platform-specific source sets.

## Library Comparison

### Nordic Kotlin-BLE-Library (current: `2.0.0-alpha16`)

| Aspect | Status |
|---|---|
| Module structure | `core` and `client-core` are **pure JVM** (no Android dependencies). `client-android`, `environment-android` etc. are Android-only. |
| KMP status | **Not KMP yet.** `core` & `client-core` are JVM-only modules (not KMP multiplatform). No `iosMain`, no `commonMain` with `expect`/`actual`. |
| Roadmap | Nordic says: _"The library is intended to eventually be multiplatform on its own"_ but _"I don't have much KMP experience yet, we just started experimenting."_ |
| Our coupling | 5 Nordic imports across 6 `androidMain` files. All wrapped behind our `commonMain` interfaces. |
| Mocking | ✅ Has `client-android-mock`, `core-mock` modules — we use these in tests |
| Stability | Alpha (`2.0.0-alpha16`) — API still changing (recent breaking change in alpha16: `services()` emission) |

### KABLE (JuulLabs, current: `0.42.0`)

| Aspect | Status |
|---|---|
| KMP targets | ✅ Android, iOS, macOS, JVM, JavaScript, Wasm |
| API style | Coroutines/Flow-first. `Scanner`, `Peripheral`, `connect()`, `observe()`, `read()`, `write()` |
| JVM support | ✅ Uses Bluetooth on macOS/Linux/Windows via native bindings |
| Mocking | ❌ No mock module (Nordic's advantage) |
| Maturity | More mature than Nordic's KMP story, actively maintained |
| License | Apache 2.0 |
| Our coupling cost | Would need to rewrite 6 `androidMain` files (~400 lines total) |

## Recommended Strategy

### Phase 1: Add `jvm()` Target Now (No Library Change) ✅ COMPLETED

Since `commonMain` is already pure Kotlin interfaces, `jvm()` has been added to `core:ble/build.gradle.kts`. No JVM BLE implementation is needed — the interfaces compile fine and a headless JVM app simply wouldn't inject BLE bindings.

This unblocked `core:ble` in the JVM smoke compile. CI now validates `core:ble:compileKotlinJvm` on every PR.

### Phase 2: Evaluate Whether to Migrate to KABLE (Strategic Decision)

There are three paths, and the right one depends on project goals:

#### Path A: Stay on Nordic, Wait for Their KMP Support
- **Pro:** Zero migration work, we're already well-abstracted
- **Pro:** Nordic's mock modules are valuable for testing
- **Con:** Nordic says KMP is "intended" but has no timeline and "just started experimenting"
- **Con:** Nordic library is still alpha (API instability risk)
- **Risk:** Could be waiting 1+ years

#### Path B: Migrate to KABLE for `commonMain`, Keep Nordic as Optional Android Backend
- **Pro:** Real KMP BLE across all targets immediately
- **Pro:** KABLE is production-ready and actively maintained
- **Con:** ~400 lines of adapter code to rewrite
- **Con:** No built-in mock support (would need our own test doubles)
- **Con:** Two BLE library dependencies during transition

#### Path C: Dual-Backend Architecture (Best of Both Worlds)
Keep `commonMain` interfaces as-is. Add a `kableMain` or use KABLE in `commonMain` only for platforms that need it (JVM/iOS), keep Nordic on Android.

This is **overkill for now** but the architecture already supports it — our `BleConnection`/`BleScanner` interfaces would have multiple implementations selected via DI.

### Recommendation

**Phase 1 completed** (`jvm()` added, CI validates it).

For Phase 2: **Path A (stay on Nordic, wait)** is the pragmatic choice for now because:

1. Our abstraction layer is already clean — switching BLE backends later is a bounded, mechanical task
2. Nordic is actively developing (alpha16 released March 4, 2026 — 6 days ago)
3. We don't currently need real BLE on JVM/iOS
4. The mock modules are genuinely useful for testing

If Nordic hasn't shipped KMP by the time we're ready for iOS, revisit KABLE. The migration cost is predictable: ~6 files, ~400 lines, all in `androidMain` → `commonMain`.

## Potential Contribution to Nordic

Nordic is open to help. High-impact contributions we could make:

1. **File an issue or PR** showing how `core` and `client-core` could become `kotlin("multiplatform")` modules with `commonMain` + `jvmMain` source sets (they're pure JVM already — it's a build config change)
2. **Propose the `expect`/`actual` pattern** for `CentralManager` / `Peripheral` interfaces, showing how our wrapper demonstrates the abstraction boundary
3. **Share our `commonMain` interface design** as a reference for what a KMP-ready API surface looks like

This would accelerate their timeline and reduce our eventual migration friction.

