# Implementation Checklist: Android App Functions Integration

> Auto-generated from `specs/20260521-091500-app-functions/spec.md`

## Pre-Implementation

- [ ] **Read skill docs**: `.skills/kmp-architecture/SKILL.md` for source-set rules
- [ ] **Bootstrap**: Run `git submodule update --init && [ -f local.properties ] || cp secrets.defaults.properties local.properties`
- [ ] **Baseline verification**: `./gradlew spotlessApply detekt assembleDebug test allTests` passes before any changes
- [ ] **Confirm compileSdk**: Check current `compileSdk` in `build-logic/` — must be ≥ 36 for AppFunctions
- [ ] **Confirm KSP setup**: Verify KSP plugin is already applied in `androidApp/build.gradle.kts`

## Dependencies & Build Configuration

- [ ] Add `androidx.appfunctions:appfunctions:1.0.0-alpha09` to androidApp dependencies
- [ ] Add `androidx.appfunctions:appfunctions-service:1.0.0-alpha09` to androidApp dependencies
- [ ] Add `androidx.appfunctions:appfunctions-compiler:1.0.0-alpha09` as KSP processor
- [ ] Add `ksp { arg("appfunctions:aggregateAppFunctions", "true") }` to androidApp build config
- [ ] Bump `compileSdk` to 36 if not already (check build-logic conventions plugin)
- [ ] Verify build compiles: `./gradlew :androidApp:compileGoogleDebugKotlin`

## commonMain: Platform-Agnostic Contracts (`core/data`)

- [ ] Create `core/data/src/commonMain/kotlin/org/meshtastic/core/data/ai/` package
- [ ] **AiFunctionProvider.kt**: Interface with `sendMessage()` and `getMeshStatus()` suspend functions
- [ ] **AiFunctionResult.kt**: Sealed class hierarchy for success/error results (no Android dependencies!)
- [ ] **FuzzyNameResolver.kt**: Longest-substring matching logic; returns single match or throws with candidates
- [ ] **RateLimiter.kt**: Token-bucket implementation (5 tokens, 60s refill); use `kotlinx.datetime` or `Clock` for time
- [ ] Unit tests for `FuzzyNameResolver` — exact match, single fuzzy match, ambiguous match, no match
- [ ] Unit tests for `RateLimiter` — under limit, at limit, over limit, refill after window
- [ ] Verify no `android.*` or `java.*` imports in any commonMain files
- [ ] Run: `./gradlew :core:data:allTests`

## commonMain: AiFunctionProvider Implementation

- [ ] Create `AiFunctionProviderImpl.kt` wiring to existing repositories
- [ ] Inject `NodeRepository`, `ServiceRepository`, `CommandSender`, `RadioConfigRepository` via constructor
- [ ] `sendMessage`: Check connection → rate limit → resolve name → validate length → send → return result
- [ ] `getMeshStatus`: Read connection state, node counts, battery from existing flows (`.first()`)
- [ ] Register in Koin module (`core/data` DI module)
- [ ] Integration test: `AiFunctionProviderImpl` with mocked repositories

## androidApp: App Function Declarations (Google flavor)

- [ ] Create `androidApp/src/google/kotlin/org/meshtastic/app/appfunctions/` package
- [ ] **MeshtasticAppFunctions.kt**: Class with `@AppFunction(isDescribedByKDoc = true)` methods
  - [ ] `sendMessage(appFunctionContext, text, recipientName?, channelName?)` → `SendMessageResponse`
  - [ ] `getMeshStatus(appFunctionContext)` → `MeshStatusResponse`
- [ ] **models/SendMessageResponse.kt**: `@AppFunctionSerializable` with messageId, timestamp, channel
- [ ] **models/MeshStatusResponse.kt**: `@AppFunctionSerializable` with connectionState, onlineNodes, totalNodes, batteryLevel
- [ ] **AppFunctionFactory.kt**: `AppFunctionConfiguration.Provider` using Koin to resolve `AiFunctionProviderImpl`
- [ ] Register `AppFunctionConfiguration.Provider` in `GoogleMeshUtilApplication` (Google flavor subclass)
- [ ] KDoc on every `@AppFunction` method — clear enough for AI agent to understand without context
- [ ] KDoc on every `@AppFunctionSerializable` field — descriptive for schema generation

## Error Handling

- [ ] Disconnected state → throw `AppFunctionAppException("Not connected to a Meshtastic radio")`
- [ ] Ambiguous name match → throw `AppFunctionInvalidArgumentException` with candidate list in message
- [ ] No name match → throw `AppFunctionElementNotFoundException`
- [ ] Message too long → throw `AppFunctionInvalidArgumentException` with max length info
- [ ] Rate limit exceeded → throw `AppFunctionLimitExceededException`
- [ ] Timeout (>5s) → throw `AppFunctionCancelledException`
- [ ] No generic `Exception` or `RuntimeException` thrown from AppFunction methods

## Security & Privacy

- [ ] No admin channel data exposed in any response
- [ ] No encryption keys or PSK material in responses
- [ ] No raw protobuf payloads returned — only structured, safe data
- [ ] No PII beyond what user has already shared on mesh (node names, messages are user-consented)
- [ ] Rate limiter prevents AI-driven mesh flooding

## Testing & Verification

- [ ] `./gradlew :core:data:allTests` — commonMain unit tests pass
- [ ] `./gradlew :androidApp:testGoogleDebugUnitTest` — Android unit tests pass
- [ ] `./gradlew :androidApp:assembleGoogleDebug` — builds successfully
- [ ] `./gradlew spotlessApply spotlessCheck` — formatting passes
- [ ] `./gradlew detekt` — static analysis passes
- [ ] `adb shell cmd app_function list-app-functions | grep org.meshtastic` — functions registered on device
- [ ] Manual test: invoke via test agent app on API 35+ device/emulator
- [ ] Verify rate limiting works (5 rapid calls → exception on 6th)
- [ ] Verify disconnected state returns proper error (not crash)

## Documentation

- [ ] KDoc comprehensive on all public APIs
- [ ] Update spec status from "Draft" to "Implemented" after verification
- [ ] Add entry to CHANGELOG.md under next release

## Final Verification

- [ ] Full verification pass: `./gradlew spotlessApply detekt assembleDebug test allTests`
- [ ] No regressions in existing tests
- [ ] PR description references spec: `specs/20260521-091500-app-functions/spec.md`
- [ ] Branch naming follows convention
