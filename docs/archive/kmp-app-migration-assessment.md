# KMP Migration Assessment — App Module & Expect/Actual Evaluation

> Date: 2026-03-10

## Summary of Changes Made

### Expect/Actual Consolidation (Completed)

| Expect/Actual | Resolution | Rationale |
|---|---|---|
| `Base64Factory` | ✅ **Replaced** with pure `commonMain` using `kotlin.io.encoding.Base64` | Both Android/JVM used `java.util.Base64` — Kotlin stdlib provides a cross-platform equivalent |
| `isDebug` | ✅ **Replaced** with `commonMain` constant `false` | Both actuals returned `false`; runtime debug detection uses `BuildConfigProvider.isDebug` via DI |
| `NumberFormatter` | ✅ **Replaced** with pure Kotlin `commonMain` implementation | Both actuals used identical `String.format(Locale.ROOT, ...)` — pure math-based formatting works everywhere |
| `UrlUtils` | ✅ **Replaced** with pure Kotlin `commonMain` RFC 3986 encoder | Both actuals used `URLEncoder.encode` — simple byte-level encoding is trivially portable |
| `SfppHasher` | ✅ **Consolidated** into `jvmAndroidMain` intermediate source set | Byte-for-byte identical implementations using `java.security.MessageDigest` |
| `platformRandomBytes` | ✅ **Consolidated** into `jvmAndroidMain` intermediate source set | Byte-for-byte identical implementations using `java.security.SecureRandom` |
| `getShortDateTime` | ✅ **Consolidated** into `jvmAndroidMain` intermediate source set | Functionally identical `java.text.DateFormat` usage |

### Expect/Actual Retained (Genuinely Platform-Specific)

| Expect/Actual | Why It Must Remain |
|---|---|
| `BuildUtils` (isEmulator, sdkInt) | Android uses `Build.FINGERPRINT`/`Build.VERSION.SDK_INT`; JVM stubs return defaults |
| `CommonUri` | Android wraps `android.net.Uri`; JVM wraps `java.net.URI` — different parsing semantics |
| `CommonUri.toPlatformUri()` | Returns platform-native URI type for interop |
| `Parcelable` abstractions (6 declarations) | AIDL/Android Parcel is a fundamentally Android-only concept |
| `Location` | Android wraps `android.location.Location`; JVM is an empty stub |
| `DateFormatter` | Android uses `DateUtils`/`ContextServices.app`; JVM uses `java.time` formatters |
| `MeasurementSystem` | Android uses ICU `LocaleData` with API-level branching; JVM uses `Locale.getDefault()` |
| `NetworkUtils.isValidAddress` | Android uses `InetAddresses`/`Patterns`; JVM uses regex/`InetAddress` |
| `core:ui` expects (7 declarations) | Dynamic color, lifecycle, clipboard, HTML, toast, map, URL, QR, brightness — all genuinely platform-specific UI |

---

## App Module Evaluation — What's Left

### Already Migrated to Shared KMP Modules

The vast majority of business logic now lives in `core:*` and `feature:*` modules. The following pure passthrough wrappers have been eliminated from `:app`:

- `AndroidCompassViewModel` (was wrapping `feature:node → CompassViewModel`)
- `AndroidContactsViewModel` (was wrapping `feature:messaging → ContactsViewModel`)
- `AndroidQuickChatViewModel` (was wrapping `feature:messaging → QuickChatViewModel`)
- `AndroidSharedMapViewModel` (was wrapping `feature:map → SharedMapViewModel`)
- `AndroidFilterSettingsViewModel` (was wrapping `feature:settings → FilterSettingsViewModel`)
- `AndroidCleanNodeDatabaseViewModel` (was wrapping `feature:settings → CleanNodeDatabaseViewModel`)
- `AndroidFirmwareUpdateViewModel` (was wrapping `feature:firmware → FirmwareUpdateViewModel`)
- `AndroidIntroViewModel` (was wrapping `feature:intro → IntroViewModel`)
- `AndroidNodeListViewModel` (was wrapping `feature:node → NodeListViewModel`)
- `AndroidNodeDetailViewModel` (was wrapping `feature:node → NodeDetailViewModel`)
- `AndroidMessageViewModel` (was wrapping `feature:messaging → MessageViewModel`)

The remaining `app` ViewModels are ones with **genuine Android-specific logic**:

| App ViewModel | Shared Base Class | Extra Android Logic |
|---|---|---|
| `AndroidSettingsViewModel` | `feature:settings → SettingsViewModel` | File I/O via `android.net.Uri` |
| `AndroidRadioConfigViewModel` | `feature:settings → RadioConfigViewModel` | Location permissions, file I/O |
| `AndroidDebugViewModel` | `feature:settings → DebugViewModel` | `Locale`-aware hex formatting |
| `AndroidMetricsViewModel` | `feature:node → MetricsViewModel` | CSV export via `android.net.Uri` |

### Candidates for Migration (Medium Effort)

| Component | Current Location | Target | Blockers |
|---|---|---|---|
| `GetDiscoveredDevicesUseCase` | `app/domain/usecase/` | `core:domain` | Depends on BLE/USB/NSD discovery — needs platform abstraction |
| `UIViewModel` (266 lines) | `app/model/` | Split: shared → `core:ui`, Android → `app` | `android.net.Uri` deep links, alert management mostly portable |
| `SavedStateHandle`-driven ViewModels | `feature:messaging`, `feature:node` | Shared route-arg abstraction | Replace direct `SavedStateHandle` dependency in shared VMs with route params/interface |
| `DeviceListEntry` (sealed class) | `app/model/` | `core:model` (Ble, Tcp, Mock); `app` (Usb) | `Usb` variant needs `UsbManager`/`UsbSerialDriver` |

### Permanently Android-Only in `:app`

| Component | Reason |
|---|---|
| `MeshService` (392 lines) | Android `Service` with foreground notifications, AIDL `IBinder` |
| `MeshServiceClient` | Android `Activity` lifecycle `ServiceConnection` bindings |
| `BootCompleteReceiver` | Android `BroadcastReceiver` |
| `MeshServiceStarter` | Android service lifecycle management |
| `MarkAsReadReceiver`, `ReplyReceiver`, `ReactionReceiver` | Android notification action receivers |
| `MeshLogCleanupWorker`, `ServiceKeepAliveWorker` | Android `WorkManager` workers |
| `LocalStatsWidget*` | Android Glance widget |
| `AppKoinModule`, `NetworkModule`, `FlavorModule` | Android-specific DI assembly with `ConnectivityManager`, `NsdManager`, `ImageLoader`, etc. |
| `MainActivity`, `MeshUtilApplication` | Android entry points |
| `repository/radio/*` (22 files) | USB serial, BLE interface, NSD discovery — hardware-level Android APIs |
| `repository/usb/*` | `UsbSerialDriver`, `ProbeTableProvider` |
| `*Navigation.kt` (7 files) | Android Navigation 3 composable wiring |

---

## Desktop Module (formerly `jvm_demo`)

### Changes Made
- **Renamed** `:jvm_demo` → `:desktop` as the first full non-Android target
- **Added** Compose Desktop (JetBrains Compose) with Material 3 windowed UI
- **Registered** `:desktop` in `settings.gradle.kts`
- **Added** dependencies on all core KMP modules with JVM targets, including `core:ui`
- **Implemented** Koin DI bootstrap with `BuildConfigProvider` stub
- **Implemented** `DemoScenario.renderReport()` exercising Base64, NumberFormatter, UrlUtils, DateFormatter, CommonUri, DeviceVersion, Capabilities, SfppHasher, platformRandomBytes, getShortDateTime, Channel key generation
- **Implemented** JUnit tests validating report output
- **Implemented** Navigation 3 shell with `NavigationRail` + `NavDisplay` + `SavedStateConfiguration`
- **Wired** `feature:settings` with ~30 real composable screens via `DesktopSettingsNavigation.kt`
- **Created** desktop-specific `DesktopSettingsScreen.kt` (replaces Android-only `SettingsScreen`)

### Roadmap for Desktop
1. ~~Implement real navigation with shared `core:navigation` keys~~ ✅
2. ~~Wire `feature:settings` with real composables~~ ✅ (~30 screens)
3. Wire `feature:node` and `feature:messaging` composables into the desktop nav graph
4. Add serial/USB transport for direct radio connection on Desktop
5. Add MQTT transport for cloud-connected operation
6. Package native distributions (DMG, MSI, DEB)

---

## Architecture Improvement: `jvmAndroidMain` Source Set

Added `jvmAndroidMain` intermediate source sets to `core:common` and `core:model` for sharing JVM-specific code (like `java.security.*` usage) between the `androidMain` and `jvmMain` targets without duplication.

```
commonMain
    └── jvmAndroidMain     ← NEW: shared JVM code
            ├── androidMain
            └── jvmMain
```

This pattern should be adopted by other modules as they add JVM targets to eliminate duplicate actual implementations.


