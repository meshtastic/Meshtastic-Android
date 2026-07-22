# `:feature:car`

## Overview

The `:feature:car` module provides the **Android Auto integration** built on `androidx.car.app`. `MeshtasticCarAppService` is a `CarAppService` registered in the MESSAGING category (`androidx.car.app.category.MESSAGING`); each host connection gets a `MeshtasticCarSession` that wires up state, alerts, and conversation shortcuts.

**Target: Android only, `google` flavor only** — the module is added via `googleImplementation(projects.feature.car)` in `:androidApp`, and `FeatureCarModule` is included from the google flavor's `FlavorModule`.

## Templates Gating

Production ships **notification-only** Android Auto messaging (MessagingStyle notifications + `ConversationShortcutManager`). The full templated car app is gated:

- Build with `-PenableCarTemplates=true` to enable it (Internal/Closed Play tracks only).
- The Gradle property resolves the `carTemplatesEnabled` manifest placeholder — in **this module's own** `build.gradle.kts`/`AndroidManifest.xml` (a library resolves its own placeholders; the app's value does not override it) and again in `:androidApp` as defense-in-depth.
- The decisive host-side gate is `automotive_app_desc`'s `<uses name="template" />`; the `android:enabled="${carTemplatesEnabled}"` service flag keeps the templated service unbindable in production.

## Key Components

### Service layer (`service/`)

- `MeshtasticCarAppService` — `CarAppService`; host validation (allowlist in release, `ALLOW_ALL_HOSTS_VALIDATOR` in debug), creates sessions.
- `MeshtasticCarSession` — per-connection `Session`; starts `CarStateCoordinator`, `EmergencyHandler`, and `ConversationShortcutManager`, tags the session in Crashlytics, and returns the first `Screen`.
- `CarStateCoordinator` — bridges repository flows (nodes, messages, connection state) into car presentation state (`MessageSnapshot`, `NodeUi` in `model/CarUiModels.kt`). Created per car session.
- `ConversationShortcutManager` — publishes dynamic shortcuts for active DMs/channels so Android Auto surfaces Meshtastic conversations and links notifications to conversations via `LocusIdCompat` (the notification/reply plumbing that also serves the notification-only production path).

### Screens (`screens/`)

- `HomeScreen` — `TabTemplate` dashboard (messages / nodes / status tabs) fed by `CarStateCoordinator`.
- `NodeDetailScreen` — `PaneTemplate` detail for one node (signal, battery, last heard) with a message action.

### Emergency alerts (`alerts/`)

- `EmergencyHandler` — observes incoming `PortNum.ALERT_APP` packets, maintains the active `EmergencyAlert` list, and triggers audio tones on the car head unit.

### Util layer (`util/`)

- `MessageFilter` — decides which packets are displayable (text, non-blank, not emoji-only) and validates outgoing message byte length.
- `CarScreenDataBuilder` — pure functions converting domain models to car UI models; free of Car App Library types so they unit-test without Robolectric.
- `NodeSubtitleFormatter` — node subtitle text with signal colouring and responsive variants.
- `PersonIconFactory` — circular initial avatars for `Person` icons in MessagingStyle notifications and shortcut avatars.
- `CrashlyticsCarTagger` — sets the `car_session` Crashlytics custom key.

## Key Dependencies

From `feature/car/build.gradle.kts`:

- `core:common`, `core:data`, `core:database`, `core:domain`, `core:model`, `core:repository`
- `androidx.car.app`, `androidx.car.app.projected` (+ `androidx.car.app.testing` for tests)
- `koin.android`, `firebase.crashlytics` (via Firebase BoM), `kermit`

## Testing

Screen and flow tests run on the JVM via `TestCarContext` + mokkery + Robolectric (`CarScreensTest`) — the Desktop Head Unit cannot drive current Car App API levels. See [TESTING.md](TESTING.md) for the full recipe.
