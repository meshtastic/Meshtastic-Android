# Implementation Plan: Android App Functions Integration

**Spec**: `specs/20260521-091500-app-functions/spec.md`  
**Branch**: `jamesarich/crispy-barnacle`  
**Created**: 2026-05-21

## Overview

Implement a minimal MVP (2 App Functions: `sendMessage` + `getMeshStatus`) to validate the Meshtastic ↔ Android system AI integration pattern. The architecture follows KMP conventions: platform-agnostic interfaces + logic in `commonMain`, Android-specific `@AppFunction` wiring in the Google flavor.

## Key Findings from Exploration

- **compileSdk = 37** (already satisfies the ≥36 requirement)
- **Koin uses its own compiler plugin** (not KSP) — AppFunctions KSP processor is separate and needs the `com.google.devtools.ksp` Gradle plugin applied to `androidApp`
- **Google flavor already has `ai/` package** with `GeminiNanoDocAssistant.kt` and `GoogleAiModule.kt` in DI
- **`FlavorModule.kt`** includes `GoogleAiModule` — we'll add our AppFunctions module here
- **Application class** (`MeshUtilApplication`) already implements `Configuration.Provider` — we'll add `AppFunctionConfiguration.Provider`
- **`CommandSender.sendData(DataPacket)`** is the method to send messages
- **`DataPacket`** uses `channel: Int` (index) and `to: String?` (nodeID or `ID_BROADCAST`)
- **`NodeRepository`** has `nodeDBbyNum: StateFlow<Map<Int, Node>>` and `getNodes()` with filter
- **`ServiceRepository.connectionState: StateFlow<ConnectionState>`** for connection status

## Implementation Phases

### Phase 1: Dependencies & Build Setup

**Files to modify:**
- `gradle/libs.versions.toml` — add AppFunctions library versions
- `androidApp/build.gradle.kts` — apply KSP plugin, add AppFunctions dependencies

**Details:**
```toml
# libs.versions.toml
appfunctions = "1.0.0-alpha09"

# libraries
androidx-appfunctions = { group = "androidx.appfunctions", name = "appfunctions", version.ref = "appfunctions" }
androidx-appfunctions-service = { group = "androidx.appfunctions", name = "appfunctions-service", version.ref = "appfunctions" }
androidx-appfunctions-compiler = { group = "androidx.appfunctions", name = "appfunctions-compiler", version.ref = "appfunctions" }
```

In `androidApp/build.gradle.kts`:
- Apply `com.google.devtools.ksp` plugin
- Add `implementation(libs.androidx.appfunctions)` and `implementation(libs.androidx.appfunctions.service)`
- Add `ksp(libs.androidx.appfunctions.compiler)`
- Add `ksp { arg("appfunctions:aggregateAppFunctions", "true") }`

---

### Phase 2: commonMain Contracts & Utilities (`core/data`)

**New files:**
- `core/data/src/commonMain/kotlin/org/meshtastic/core/data/ai/AiFunctionProvider.kt`
- `core/data/src/commonMain/kotlin/org/meshtastic/core/data/ai/AiFunctionResult.kt`
- `core/data/src/commonMain/kotlin/org/meshtastic/core/data/ai/FuzzyNameResolver.kt`
- `core/data/src/commonMain/kotlin/org/meshtastic/core/data/ai/RateLimiter.kt`
- `core/data/src/commonMain/kotlin/org/meshtastic/core/data/ai/AiFunctionProviderImpl.kt`
- `core/data/src/commonTest/kotlin/org/meshtastic/core/data/ai/FuzzyNameResolverTest.kt`
- `core/data/src/commonTest/kotlin/org/meshtastic/core/data/ai/RateLimiterTest.kt`
- `core/data/src/commonTest/kotlin/org/meshtastic/core/data/ai/AiFunctionProviderImplTest.kt`

**AiFunctionProvider interface:**
```kotlin
package org.meshtastic.core.data.ai

interface AiFunctionProvider {
    /** Send a text message to a channel or node resolved by name. */
    suspend fun sendMessage(text: String, recipientName: String?, channelName: String?): SendMessageResult

    /** Get current mesh network status. */
    suspend fun getMeshStatus(): MeshStatusResult
}
```

**AiFunctionResult sealed types:**
```kotlin
sealed class SendMessageResult {
    data class Success(val messageId: Int, val channel: String, val timestamp: Long) : SendMessageResult()
    data class NotConnected(val message: String) : SendMessageResult()
    data class AmbiguousName(val candidates: List<String>) : SendMessageResult()
    data class InvalidArgument(val reason: String) : SendMessageResult()
    data class RateLimited(val retryAfterSeconds: Int) : SendMessageResult()
}

data class MeshStatusResult(
    val connectionState: String,
    val onlineNodeCount: Int,
    val totalNodeCount: Int,
    val localBatteryLevel: Int?,
    val localNodeName: String?,
)
```

**FuzzyNameResolver:**
- Takes a query string and a list of candidate names
- Uses longest common substring for matching
- Returns: single match (exact or unique fuzzy) or error with candidate list
- Case-insensitive comparison
- Also resolves channel names from `RadioConfigRepository` channel set

**RateLimiter:**
- Sliding window: tracks last 5 invocation timestamps, rejects if all within 60s
- Uses `kotlinx.datetime.Clock` (or injected `Clock` from existing `CoreDataModule`)
- Thread-safe via `Mutex` (already used in project for commonMain concurrency)

**AiFunctionProviderImpl:**
- `@Single` Koin annotation
- Constructor-injects: `NodeRepository`, `ServiceRepository`, `CommandSender`, `RadioConfigRepository`, `FuzzyNameResolver`, `RateLimiter`, `Clock`
- `sendMessage`: check connection → check rate → resolve name → validate length → create `DataPacket` → `commandSender.sendData()` → return success
- `getMeshStatus`: read `connectionState.value`, `onlineNodeCount.first()`, `totalNodeCount.first()`, `ourNodeInfo.value?.batteryLevel`

---

### Phase 3: Android App Function Declarations (Google flavor)

**New files:**
- `androidApp/src/google/kotlin/org/meshtastic/app/appfunctions/MeshtasticAppFunctions.kt`
- `androidApp/src/google/kotlin/org/meshtastic/app/appfunctions/models/SendMessageResponse.kt`
- `androidApp/src/google/kotlin/org/meshtastic/app/appfunctions/models/MeshStatusResponse.kt`
- `androidApp/src/google/kotlin/org/meshtastic/app/appfunctions/di/AppFunctionsModule.kt`

**Modify:**
- `androidApp/src/google/kotlin/org/meshtastic/app/di/FlavorModule.kt` — include `AppFunctionsModule`
- `androidApp/src/main/kotlin/org/meshtastic/app/MeshUtilApplication.kt` — add `AppFunctionConfiguration.Provider`

**MeshtasticAppFunctions:**
```kotlin
@Suppress("unused") // Invoked by system via AppFunctionManager
class MeshtasticAppFunctions(
    private val provider: AiFunctionProvider
) {
    /**
     * Send a text message over the Meshtastic mesh network.
     *
     * Messages are broadcast to all nodes on a channel, or sent directly to a
     * specific node. The recipient is resolved by name using fuzzy matching.
     *
     * @param appFunctionContext The execution context provided by the system.
     * @param text The message text to send (max 237 characters for standard mesh).
     * @param recipientName Optional node name for direct messages. Omit for channel broadcast.
     * @param channelName Optional channel name to send on. Defaults to primary channel if omitted.
     * @return Confirmation with message ID, channel name, and send timestamp.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun sendMessage(
        appFunctionContext: AppFunctionContext,
        text: String,
        recipientName: String? = null,
        channelName: String? = null,
    ): SendMessageResponse { ... }

    /**
     * Get the current status of the Meshtastic mesh network.
     *
     * Returns connection state, number of online and total nodes in the mesh,
     * local device battery level, and the local node's display name.
     *
     * @param appFunctionContext The execution context provided by the system.
     * @return Current mesh network status summary.
     */
    @AppFunction(isDescribedByKDoc = true)
    suspend fun getMeshStatus(
        appFunctionContext: AppFunctionContext,
    ): MeshStatusResponse { ... }
}
```

**AppFunctionConfiguration.Provider** in Application:
```kotlin
// In MeshUtilApplication (or subclass in google flavor)
override val appFunctionConfiguration: AppFunctionConfiguration
    get() = AppFunctionConfiguration.Builder()
        .addEnclosingClassFactory(MeshtasticAppFunctions::class.java) {
            MeshtasticAppFunctions(get<AiFunctionProvider>())
        }
        .build()
```

**Note**: Since the Application class is in `src/main/` (shared), but `AppFunctionConfiguration.Provider` is Android 16+, we need to handle this carefully. Options:
1. Make google-flavor `GoogleMeshUtilApplication` extend `MeshUtilApplication` and add the provider there
2. Use a conditional check in the base class

**Decision**: Use option 1 — a `GoogleMeshUtilApplication` subclass in the Google flavor that adds `AppFunctionConfiguration.Provider`. This keeps the base class clean and the fdroid flavor unaffected.

---

### Phase 4: Error Mapping

In `MeshtasticAppFunctions`, map `AiFunctionResult` sealed types to platform exceptions:
- `SendMessageResult.NotConnected` → `AppFunctionAppException("Not connected...")`
- `SendMessageResult.AmbiguousName` → `AppFunctionInvalidArgumentException("Multiple matches: ...")`
- `SendMessageResult.InvalidArgument` → `AppFunctionInvalidArgumentException(...)`
- `SendMessageResult.RateLimited` → `AppFunctionLimitExceededException(...)`

---

### Phase 5: Testing & Verification

1. **Unit tests** (commonMain):
   - `FuzzyNameResolverTest` — exact, fuzzy, ambiguous, no-match cases
   - `RateLimiterTest` — permits, exhaustion, refill
   - `AiFunctionProviderImplTest` — happy path, disconnected, rate limited, ambiguous

2. **Build verification**:
   - `./gradlew :core:data:allTests`
   - `./gradlew :androidApp:assembleGoogleDebug`
   - `./gradlew spotlessApply detekt`
   - `./gradlew test allTests`

3. **On-device verification** (manual):
   - `adb shell cmd app_function list-app-functions | grep org.meshtastic`

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| AppFunctions alpha library has breaking API changes | Pin to `1.0.0-alpha09`; isolate behind our own interface |
| KSP plugin conflicts with existing Koin compiler | KSP and Koin compiler are independent; Koin uses its own Gradle plugin |
| `AppFunctionConfiguration.Provider` on Application conflicts with `Configuration.Provider` | Use flavor subclass approach |
| Rate limiter state lost on process death | Acceptable — resets on app restart; mesh flooding concern is per-session |
| Fuzzy matching too permissive/restrictive | Tunable threshold; start conservative (require ≥50% substring match) |

## File Change Summary

| Action | File |
|--------|------|
| Modify | `gradle/libs.versions.toml` |
| Modify | `androidApp/build.gradle.kts` |
| Create | `core/data/src/commonMain/kotlin/org/meshtastic/core/data/ai/AiFunctionProvider.kt` |
| Create | `core/data/src/commonMain/kotlin/org/meshtastic/core/data/ai/AiFunctionResult.kt` |
| Create | `core/data/src/commonMain/kotlin/org/meshtastic/core/data/ai/FuzzyNameResolver.kt` |
| Create | `core/data/src/commonMain/kotlin/org/meshtastic/core/data/ai/RateLimiter.kt` |
| Create | `core/data/src/commonMain/kotlin/org/meshtastic/core/data/ai/AiFunctionProviderImpl.kt` |
| Create | `core/data/src/commonTest/kotlin/org/meshtastic/core/data/ai/FuzzyNameResolverTest.kt` |
| Create | `core/data/src/commonTest/kotlin/org/meshtastic/core/data/ai/RateLimiterTest.kt` |
| Create | `core/data/src/commonTest/kotlin/org/meshtastic/core/data/ai/AiFunctionProviderImplTest.kt` |
| Create | `androidApp/src/google/kotlin/org/meshtastic/app/appfunctions/MeshtasticAppFunctions.kt` |
| Create | `androidApp/src/google/kotlin/org/meshtastic/app/appfunctions/models/SendMessageResponse.kt` |
| Create | `androidApp/src/google/kotlin/org/meshtastic/app/appfunctions/models/MeshStatusResponse.kt` |
| Create | `androidApp/src/google/kotlin/org/meshtastic/app/appfunctions/di/AppFunctionsModule.kt` |
| Modify | `androidApp/src/google/kotlin/org/meshtastic/app/di/FlavorModule.kt` |
| Create or Modify | `androidApp/src/google/kotlin/org/meshtastic/app/GoogleMeshUtilApplication.kt` |
| Modify | `androidApp/src/google/AndroidManifest.xml` (point to GoogleMeshUtilApplication) |
