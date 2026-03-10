# KMP Progress Review — Evidence Appendix

This appendix records the concrete repo evidence behind [`docs/kmp-progress-review-2026.md`](./kmp-progress-review-2026.md).

## Module inventory

### Core modules

| Module | Build plugin state | Current reality | Key evidence |
|---|---|---|---|
| `core:api` | Android library | **Android-only** | [`core/api/build.gradle.kts`](../core/api/build.gradle.kts) |
| `core:barcode` | Android library + compose + flavors | **Android-only** | [`core/barcode/build.gradle.kts`](../core/barcode/build.gradle.kts) |
| `core:ble` | KMP library | **KMP, Android target only** | [`core/ble/build.gradle.kts`](../core/ble/build.gradle.kts) |
| `core:common` | KMP library | **KMP, Android target only** | [`core/common/build.gradle.kts`](../core/common/build.gradle.kts) |
| `core:data` | KMP library | **KMP, Android target only** | [`core/data/build.gradle.kts`](../core/data/build.gradle.kts) |
| `core:database` | KMP library | **KMP, Android target only** | [`core/database/build.gradle.kts`](../core/database/build.gradle.kts) |
| `core:datastore` | KMP library | **KMP, Android target only** | [`core/datastore/build.gradle.kts`](../core/datastore/build.gradle.kts) |
| `core:di` | KMP library | **KMP, Android target only** | [`core/di/build.gradle.kts`](../core/di/build.gradle.kts) |
| `core:domain` | KMP library | **KMP, Android target only** | [`core/domain/build.gradle.kts`](../core/domain/build.gradle.kts) |
| `core:model` | KMP library | **KMP, Android target only, published** | [`core/model/build.gradle.kts`](../core/model/build.gradle.kts) |
| `core:navigation` | KMP library | **KMP, Android target only** | [`core/navigation/build.gradle.kts`](../core/navigation/build.gradle.kts) |
| `core:network` | KMP library | **KMP, Android target only** | [`core/network/build.gradle.kts`](../core/network/build.gradle.kts) |
| `core:nfc` | Android library + compose | **Android-only** | [`core/nfc/build.gradle.kts`](../core/nfc/build.gradle.kts) |
| `core:prefs` | KMP library | **KMP, Android target only** | [`core/prefs/build.gradle.kts`](../core/prefs/build.gradle.kts) |
| `core:proto` | KMP library | **KMP with explicit `jvm()`** | [`core/proto/build.gradle.kts`](../core/proto/build.gradle.kts) |
| `core:repository` | KMP library | **KMP, Android target only** | [`core/repository/build.gradle.kts`](../core/repository/build.gradle.kts) |
| `core:resources` | KMP library + compose | **KMP, Android target only** | [`core/resources/build.gradle.kts`](../core/resources/build.gradle.kts) |
| `core:service` | KMP library | **KMP, Android target only** | [`core/service/build.gradle.kts`](../core/service/build.gradle.kts) |
| `core:ui` | KMP library + compose | **KMP, Android target only** | [`core/ui/build.gradle.kts`](../core/ui/build.gradle.kts) |

### Feature modules

| Module | Build plugin state | Current reality | Key evidence |
|---|---|---|---|
| `feature:intro` | KMP library + compose | **KMP, Android target only** | [`feature/intro/build.gradle.kts`](../feature/intro/build.gradle.kts) |
| `feature:messaging` | KMP library + compose | **KMP, Android target only** | [`feature/messaging/build.gradle.kts`](../feature/messaging/build.gradle.kts) |
| `feature:map` | KMP library + compose | **KMP, Android target only** | [`feature/map/build.gradle.kts`](../feature/map/build.gradle.kts) |
| `feature:node` | KMP library + compose | **KMP, Android target only** | [`feature/node/build.gradle.kts`](../feature/node/build.gradle.kts) |
| `feature:settings` | KMP library + compose | **KMP, Android target only** | [`feature/settings/build.gradle.kts`](../feature/settings/build.gradle.kts) |
| `feature:firmware` | KMP library + compose | **KMP, Android target only** | [`feature/firmware/build.gradle.kts`](../feature/firmware/build.gradle.kts) |

### Inventory totals

- Core modules: **19**
- Feature modules: **6**
- KMP modules across core + feature: **22 / 25**
- Android-only modules across core + feature: **3 / 25**
- Modules with explicit non-Android target declarations: **1 / 25** (`core:proto`)

---

## Build-logic evidence

### KMP convention setup

- [`KmpLibraryConventionPlugin.kt`](../build-logic/convention/src/main/kotlin/KmpLibraryConventionPlugin.kt) applies:
  - `org.jetbrains.kotlin.multiplatform`
  - `com.android.kotlin.multiplatform.library`
- [`KmpLibraryComposeConventionPlugin.kt`](../build-logic/convention/src/main/kotlin/KmpLibraryComposeConventionPlugin.kt) adds Compose Multiplatform runtime/resources to `commonMain`
- [`KotlinAndroid.kt`](../build-logic/convention/src/main/kotlin/org/meshtastic/buildlogic/KotlinAndroid.kt) configures the Android KMP target and general Kotlin compiler options

### Important implication

The repo has standardized on the **Android KMP library path** for shared modules, but does **not** yet automatically add a second target like `jvm()` or `ios*()`.

---

## Historical documentation gaps this review corrects

| Topic | Historical narrative gap | Current code reality | Evidence |
|---|---|---|---|
| `core:api` | earlier migration wording grouped `core:service` and `core:api` together as KMP | `core:service` is KMP, `core:api` is still Android-only | [`docs/kmp-migration.md`](./kmp-migration.md), [`core/api/build.gradle.kts`](../core/api/build.gradle.kts), [`core/service/build.gradle.kts`](../core/service/build.gradle.kts) |
| DI centralization | original plan kept DI-dependent components in `app` | several `commonMain` modules contain Koin `@Module`, `@ComponentScan`, and `@KoinViewModel` | [`feature/map/src/commonMain/kotlin/org/meshtastic/feature/map/SharedMapViewModel.kt`](../feature/map/src/commonMain/kotlin/org/meshtastic/feature/map/SharedMapViewModel.kt), [`core/domain/src/commonMain/kotlin/org/meshtastic/core/domain/di/CoreDomainModule.kt`](../core/domain/src/commonMain/kotlin/org/meshtastic/core/domain/di/CoreDomainModule.kt) |
| Cross-platform readiness impression | early migration narrative emphasized Desktop/iOS end goals more than active target verification | only `core:proto` explicitly declares a second target today | [`core/proto/build.gradle.kts`](../core/proto/build.gradle.kts), broad scan of module `build.gradle.kts` files |

---

## Git history milestones used for the timeline

These were extracted from local git history on 2026-03-10.

| Date | Commit | Theme | Milestone | Why it mattered |
|---|---|---|---|---|
| 2022-06-11 | `54f611290` | storage | create LocalConfig DataStore | Early shift away from raw app-only preference handling |
| 2024-02-06 | `c8f93db00` | repositories | implement repository pattern for `NodeDB` | Began decoupling data access from service/UI consumers |
| 2024-08-25 | `0b7718f8d` | storage | write to proto DataStore using dynamic field updates | Normalized protobuf-backed state management |
| 2024-09-13 | `39a18e641` | database | replace service local node db with Room NodeDB | Precursor to later Room KMP adoption |
| 2024-11-21 | `80f8f2a59` | api/service | implement repository pattern replacement for AIDL methods | Reduced direct platform/service coupling at the API edge |
| 2024-11-30 | `716a3f535` | navigation | decouple `NavGraph` from ViewModel and NodeEntity | Important cleanup before shared navigation state |
| 2025-04-24 | `5cd3a0229` | repositories | `DeviceHardwareRepository` to local + network data sources | Clearer data-source boundaries |
| 2025-05-22 | `02bb3f02e` | modularization | introduce network module | Early module extraction toward sharable layers |
| 2025-08-16 | `acc3e3f63` | service decoupling | decouple mesh service bind from `MainActivity` | Removed a high-value Android lifecycle coupling |
| 2025-08-18 | `a46065865` | prefs/repositories | add prefs repos and DI providers | Started the broader prefs-to-repository sweep |
| 2025-08-19 | `c913bb047` | prefs/repositories | migrate remaining prefs usages to repo | Consolidated state access behind repository abstractions |
| 2025-09-05 | `4ab588cda` | navigation | Migrate App Intro to Navigation 3 | First major Navigation 3 adoption waypoint |
| 2025-09-15 | `22a5521b9` | build logic | modularize `build-logic` | Strengthened convention-based architecture for later KMP rollout |
| 2025-09-17 | `7afab1601` | modularization | move nav routes to new `:navigation` project module | Formalized navigation as sharable architecture state |
| 2025-09-19 | `0d2c1f151` | modularization | new core modules for `:model`, `:navigation`, `:network`, `:prefs` | One of the clearest runway commits toward KMP |
| 2025-09-25 | `c5360086b` | modularization | add `:core:ui` | Created a natural shared UI landing zone |
| 2025-09-30 | `db2ef75e0` | modularization | add `:core:service` | Separated service logic from app shell concerns |
| 2025-10-01 | `d553cdfee` | modularization | add `:feature:node` | Started feature-level module extraction |
| 2025-10-06 | `95ec4877d` | modularization | modularize settings code | Continued decomposition of app screens into sharable feature modules |
| 2025-10-12 | `886e9cfed` | modularization | modularize messaging code | Another major feature extraction step |
| 2025-11-10 | `28590bfcd` | resources | make `:core:strings` a Compose Multiplatform library | Introduced shared Compose resource infrastructure |
| 2025-11-11 | `57ef889ca` | resources | Kmp strings cleanup | Follow-through cleanup to make shared resources practical |
| 2025-11-15 | `0f8e47538` | BLE | migrate to Nordic BLE Library for scanning and bonding | Modernized BLE stack before abstracting it for KMP |
| 2025-11-19 | `295753d97` | navigation | update `navigation3-runtime` to `1.0.0` | Stabilized the shared-navigation direction |
| 2025-11-20 | `a2285a87a` | storage | update androidx datastore to `1.2.0` | Kept a key KMP-friendly persistence layer current |
| 2025-11-24 | `4b93065c7` | firmware | add firmware update module | Created a distinct module later migrated to KMP |
| 2025-12-17 | `61bc9bfdd` | explicit KMP | `core/common` migrated to KMP | First strong shared-foundation KMP conversion milestone |
| 2025-12-28 | `0776e029f` | logging | replace Timber with Kermit | Removed a non-KMP logging dependency |
| 2026-01-29 | `15760da07` | modularization/public api | create `core:api` module and publishing | Clarified Android API surface vs shared core artifacts |
| 2026-02-20 | `ff3f44318` | DI + explicit KMP | Hilt → Koin and `core:model` KMP pivot | Unblocked broad KMP expansion across modules |
| 2026-02-21 | `8a3d82ca7` | explicit KMP | `core:network` + `core:prefs` to KMP | Shared transport and preference abstractions moved into KMP |
| 2026-02-21 | `8a3c83ebf` | explicit KMP | `core:database` Room KMP structure | Shared persistence layer became materially multiplatform-ready |
| 2026-02-21 | `cd8e32ebf` | explicit KMP | `core:data` to KMP | Concrete repositories moved into shared source sets |
| 2026-02-21 | `3157bdd7d` | explicit KMP | `core:datastore` to KMP | Shared preferences/storage infrastructure consolidated |
| 2026-02-21 | `727f48b45` | explicit KMP | `core:ui` to KMP | Shared UI layer became real instead of aspirational |
| 2026-03-02 | `f3cddf5a1` | explicit KMP | repository interfaces/models to common KMP modules | Finished pushing core contracts into shared code |
| 2026-03-03 | `6a858acb4` | explicit KMP | `core:database` to Room Kotlin Multiplatform | Reinforced the Room KMP migration |
| 2026-03-05 | `b9b68d277` | explicit KMP | preferences to DataStore, `core:domain` decoupling | Reduced Android/JVM-specific domain assumptions |
| 2026-03-06 | `8b13b947a` | explicit KMP | `core:service` to KMP | Shared service orchestration moved out of app-only code |
| 2026-03-06 | `62b5f127d` | explicit KMP | `feature:messaging` to KMP | Shared feature migration accelerated |
| 2026-03-06 | `4089ba913` | explicit KMP | `feature:intro` to KMP | Same pattern extended to another feature |
| 2026-03-08 | `4e3bb4a83` | explicit KMP | `feature:node` and `feature:settings` to KMP | Major user-facing features moved into shared modules |
| 2026-03-08 | `50bcefd31` | explicit KMP | `feature:firmware` to KMP | Firmware orchestration became largely shareable |
| 2026-03-09 | `875cf1cff` | DI + explicit KMP | Hilt → Koin finalized and KMP common modules expanded | Completed the DI pivot that supports current KMP architecture |
| 2026-03-09 | `4320c6bd4` | navigation | Navigation 3 split | Cemented shared backstack/state direction |
| 2026-03-09 | `fb0a9a180` | explicit KMP | `core:ui` KMP follow-up | Stabilization after migration |
| 2026-03-10 | `5ff6b1ff8` | docs | docs mark `feature:node` UI migration completed | Documentation catch-up after the migration burst |

---

## DI evidence

### App root assembly

- [`AppKoinModule.kt`](../app/src/main/kotlin/org/meshtastic/app/di/AppKoinModule.kt) includes shared Koin modules from:
  - `core:*`
  - `feature:*`
  - `app`
- [`MeshUtilApplication.kt`](../app/src/main/kotlin/org/meshtastic/app/MeshUtilApplication.kt) starts Koin directly via `startKoin { ... modules(AppKoinModule().module()) }`

### Shared-module Koin evidence

| Location | Evidence |
|---|---|
| [`core/domain/.../CoreDomainModule.kt`](../core/domain/src/commonMain/kotlin/org/meshtastic/core/domain/di/CoreDomainModule.kt) | `@Module` + `@ComponentScan` in `commonMain` |
| [`feature/map/.../FeatureMapModule.kt`](../feature/map/src/commonMain/kotlin/org/meshtastic/feature/map/di/FeatureMapModule.kt) | `@Module` in `commonMain` |
| [`feature/settings/.../FeatureSettingsModule.kt`](../feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/di/FeatureSettingsModule.kt) | `@Module` in `commonMain` |
| [`feature/map/.../SharedMapViewModel.kt`](../feature/map/src/commonMain/kotlin/org/meshtastic/feature/map/SharedMapViewModel.kt) | `@KoinViewModel` in `commonMain` |

### Conclusion

The codebase has functionally adopted **shared-module Koin annotations** even though the old guide still describes an `app`-centralized DI policy.

---

## CommonMain Android-import check

A grep scan across:

- `core/**/src/commonMain/**/*.kt`
- `feature/**/src/commonMain/**/*.kt`

found **no direct `import android.*` lines**.

This is one of the strongest signals that the migration is architecturally healthy.

---

## CI evidence

Current reusable CI workflow:

- [`.github/workflows/reusable-check.yml`](../.github/workflows/reusable-check.yml)

What it verifies today:

- `spotlessCheck`
- `detekt`
- Android assemble
- Android unit tests
- Android instrumented tests
- Kover reports

What it does **not** verify:

- JVM target compilation for shared modules
- iOS target compilation
- desktop target compilation
- non-Android publication smoke tests

---

## Publication evidence

[`publish-core.yml`](../.github/workflows/publish-core.yml) currently publishes:

- `:core:api`
- `:core:model`
- `:core:proto`

Interpretation:

- the public integration surface is still centered on Android API + shared model/proto artifacts
- the broader KMP core is not yet treated as a published reusable platform SDK set

---

## Prerelease dependency watchlist

From [`gradle/libs.versions.toml`](../gradle/libs.versions.toml):

| Dependency | Version in repo | Channel |
|---|---|---|
| Compose Multiplatform | `1.11.0-alpha03` | alpha |
| Koin | `4.2.0-RC1` | RC |
| Glance | `1.2.0-rc01` | RC |
| Dokka | `2.2.0-Beta` | beta |
| Wire | `6.0.0-alpha03` | alpha |
| Nordic BLE | `2.0.0-alpha16` | alpha |
| AndroidX core location altitude | `1.0.0-beta01` | beta |
| AndroidX Compose BOM | `2026.02.01` alpha BOM channel | alpha |

### Latest release signals referenced in the main review

| Dependency | Observed signal |
|---|---|
| Koin | Latest GitHub release matches current `4.2.0-RC1` |
| Compose Multiplatform | Latest GitHub stable release observed: `1.10.2` |
| Dokka | Latest GitHub stable release observed: `2.1.0` |
| Nordic BLE | Latest GitHub release matches current `2.0.0-alpha16` |

---

## Best-practice evidence anchors

The following current ecosystem references were reviewed while producing the main report:

- Kotlin Multiplatform overview: <https://kotlinlang.org/docs/multiplatform.html>
- Android KMP guidance: <https://developer.android.com/kotlin/multiplatform>
- Compose Multiplatform + Jetpack Compose guidance: <https://kotlinlang.org/docs/multiplatform/compose-multiplatform-and-jetpack-compose.html>
- Koin KMP reference: <https://insert-koin.io/docs/reference/koin-mp/kmp/>
- AndroidX Room release notes: <https://developer.android.com/jetpack/androidx/releases/room>
- Ktor client guidance: <https://ktor.io/docs/client-create-and-configure.html>

