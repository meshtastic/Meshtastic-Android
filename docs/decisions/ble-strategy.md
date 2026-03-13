# Decision: BLE KMP Strategy

> Date: 2026-03-10 | Status: **Decided — Phase 1 complete**

## Context

`core:ble` needed to support non-Android targets. Nordic's KMM-BLE-Library is Android/iOS only (no Desktop/Web). KABLE supports all KMP targets but lacks mock modules.

## Decision

**Interface-Driven "Nordic Hybrid" Abstraction:**

- `commonMain`: Pure Kotlin interfaces (`BleConnection`, `BleScanner`, `BleDevice`, `BleConnectionFactory`, etc.) — zero platform imports
- `androidMain`: Nordic KMM-BLE-Library implementations behind those interfaces
- `jvm()` target added — interfaces compile fine; no JVM BLE implementation needed yet
- Future: KABLE or alternative can implement the same interfaces for Desktop/iOS without touching core logic

**BLE library decision: Stay on Nordic, wait.** Our abstraction layer is clean — switching backends later is a bounded, mechanical task (~6 files, ~400 lines). Nordic is actively developing. We don't currently need real BLE on JVM/iOS. If Nordic hasn't shipped KMP by the time we need iOS, revisit KABLE.

## Consequences

- `core:ble` compiles on JVM and is included in CI smoke compile
- No Nordic types leak into `commonMain`
- Desktop simply doesn't inject BLE bindings
- Migration cost to KABLE is predictable and bounded

## Archive

Full analysis: [`archive/ble-kmp-strategy.md`](../archive/ble-kmp-strategy.md)

