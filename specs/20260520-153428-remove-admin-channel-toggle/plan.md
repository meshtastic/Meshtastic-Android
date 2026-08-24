# Implementation Plan: Remove Admin Channel Enabled Toggle

**Branch**: `jamesarich/issue-5545-alignment-remove-admin-channel-enabled-ca777c` | **Date**: 2025-05-20 | **Spec**: `specs/20260520-153428-remove-admin-channel-toggle/spec.md`
**Input**: Feature specification from `/specs/20260520-153428-remove-admin-channel-toggle/spec.md`

## Summary

Remove the `admin_channel_enabled` SwitchPreference toggle and its associated HorizontalDivider from the Security Config screen. This is a pure UI deletion (8 lines of composable code + 1 unused import) that aligns the Android client with the Apple client. No proto, business logic, or string resource changes required.

## Technical Context

**Language/Version**: Kotlin 2.3+ / JDK 21  
**Primary Dependencies**: JetBrains Compose Multiplatform, Material 3  
**Storage**: N/A (no persistence changes)  
**Testing**: `./gradlew :feature:settings:allTests :feature:settings:compileKotlinJvm`  
**Target Platform**: Android + Desktop (KMP)  
**Project Type**: Mobile app (KMP multi-platform)  
**Performance Goals**: N/A (removal only, no performance impact)  
**Constraints**: Must not break existing Security Config screen rendering  
**Scale/Scope**: Single file modification, ~9 lines removed

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Kotlin Multiplatform Core**: ✅ Only `commonMain` is touched (`feature/settings/src/commonMain/...SecurityConfigScreen.kt`). No platform-specific code introduced. No business logic change — only UI element removal.
- **II. Zero Lint Tolerance**: ✅ Will run `./gradlew spotlessApply spotlessCheck detekt` on the `:feature:settings` module. Removing an unused import improves lint compliance.
- **III. Compose Multiplatform UI**: ✅ Change is deletion of a Compose Multiplatform composable invocation. No new UI added. No navigation or float formatting involved.
- **IV. Privacy First**: ✅ No PII/location/crypto logging introduced. `core/proto` submodule not modified. The proto field `admin_channel_enabled` remains untouched.
- **V. Design Standards Compliance**: ✅ This is a removal aligning with Apple client behavior. Cross-Platform Spec field marked N/A — this is platform alignment removal, not a new cross-platform feature.
- **VI. Documentation Freshness**: ✅ No user-facing docs reference this toggle. The `skip-docs-check` label applies as no documentation page covers this specific setting.
- **VII. Verify Before Push**: ✅ Local verification commands:
  ```bash
  ./gradlew spotlessApply spotlessCheck detekt
  ./gradlew :feature:settings:allTests :feature:settings:compileKotlinJvm
  ```
  Post-push: `gh pr checks` or `gh run list --branch jamesarich/issue-5545-alignment-remove-admin-channel-enabled-ca777c --limit 5`

## Project Structure

### Documentation (this feature)

```text
specs/20260520-153428-remove-admin-channel-toggle/
├── plan.md              # This file
├── research.md          # Phase 0 output (minimal — no unknowns)
├── data-model.md        # Phase 1 output (minimal — no entity changes)
├── quickstart.md        # Phase 1 output (implementation guide)
└── tasks.md             # Phase 2 output (created by /speckit.tasks)
```

### Source Code (repository root)

```text
feature/settings/
└── src/commonMain/kotlin/org/meshtastic/feature/settings/radio/component/
    └── SecurityConfigScreen.kt   # Remove lines 49, 207–214
```

**Structure Decision**: Existing KMP multi-module structure. This feature touches a single file in the `feature:settings` module's `commonMain` source set.

## Complexity Tracking

> No constitution violations. This is a minimal UI deletion with no architectural concerns.
