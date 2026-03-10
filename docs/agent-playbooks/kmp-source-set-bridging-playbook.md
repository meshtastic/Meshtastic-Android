# KMP Source-Set Bridging Playbook

Use this playbook when introducing platform-specific behavior into shared modules.

## 1) Decide if `expect`/`actual` is needed

Use `expect`/`actual` only when a platform API cannot be abstracted cleanly behind an interface passed from app wiring.

- Prefer interface + DI when behavior is already app-owned.
- Prefer `expect`/`actual` for small platform primitives and utilities.

Examples in current code:
- `core/common/src/commonMain/kotlin/org/meshtastic/core/common/util/CommonUri.kt`
- `core/common/src/androidMain/kotlin/org/meshtastic/core/common/util/CommonUri.android.kt`
- `core/repository/src/commonMain/kotlin/org/meshtastic/core/repository/LocationRepository.kt`

## 2) Keep source-set boundaries strict

- `commonMain`: business logic, shared models, coroutine/Flow orchestration.
- `androidMain`: Android framework integration (`Context`, system services, Android SDK).
- `app`: app bootstrap, DI root inclusion, Activity/service wiring, flavor-specific providers.

## 3) Resource and UI bridging rules

- Shared strings/resources must come from `core:resources`.
- Platform/flavor UI implementations should be injected via `CompositionLocal` from app.

Examples:
- Contract: `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/util/MapViewProvider.kt`
- Provider wiring: `app/src/main/kotlin/org/meshtastic/app/MainActivity.kt`

## 4) DI and module activation checks

- If a new feature/core module adds Koin annotations, verify it is included by app root module includes.
- App root includes are defined in `app/src/main/kotlin/org/meshtastic/app/di/AppKoinModule.kt`.

## 5) Verification checklist

- No Android-only imports in `commonMain`.
- `expect`/`actual` declarations compile across relevant source sets.
- Routing/DI still resolves from app startup (`MeshUtilApplication`).
- Run verification tasks from `docs/agent-playbooks/testing-and-ci-playbook.md` appropriate to touched modules.

