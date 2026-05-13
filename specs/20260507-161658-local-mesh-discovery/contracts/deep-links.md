# Deep Link Contract — Local Mesh Discovery

## Base URI

Meshtastic-Android deep links use the shared base URI from `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt`:

```text
meshtastic://meshtastic
```

## Canonical Discovery Links

| Use case | URI | Resulting typed backstack |
|---|---|---|
| Open Local Mesh Discovery landing screen | `meshtastic://meshtastic/settings/local-mesh-discovery` | `SettingsRoute.SettingsGraph(destNum = null)`, `SettingsRoute.LocalMeshDiscovery` |
| Open Local Mesh Discovery for a settings context with `destNum` | `meshtastic://meshtastic/settings/{destNum}/local-mesh-discovery` | `SettingsRoute.SettingsGraph(destNum)`, `SettingsRoute.LocalMeshDiscovery` |
| Open a stored discovery session | `meshtastic://meshtastic/settings/local-mesh-discovery/session/{sessionId}` | `SettingsRoute.SettingsGraph(destNum = null)`, `SettingsRoute.LocalMeshDiscovery`, `SettingsRoute.LocalMeshDiscoverySession(sessionId)` |

## Compatibility Alias

For compatibility with camelCase path naming used in early design notes, the router may also accept the following alias paths and normalize them to the same typed routes:

- `meshtastic://meshtastic/settings/localMeshDiscovery`
- `meshtastic://meshtastic/settings/localMeshDiscovery/session/{sessionId}`

The canonical path used in docs, tests, and generated links should remain **hyphenated** to match existing Meshtastic-Android deep-link conventions.

## Proposed Route Additions

Add these typed routes to `SettingsRoute`:

```kotlin
@Serializable data object LocalMeshDiscovery : SettingsRoute
@Serializable data class LocalMeshDiscoverySession(val sessionId: String) : SettingsRoute
```

## Router Mapping Rules

### `DeepLinkRouter`

Add discovery entries to the settings-subroute mapping:

```kotlin
"local-mesh-discovery" to SettingsRoute.LocalMeshDiscovery
"localMeshDiscovery" to SettingsRoute.LocalMeshDiscovery
```

Session detail requires one additional parsing rule because it is nested under the feature slug:

- `/settings/local-mesh-discovery/session/{sessionId}`
- `/settings/localMeshDiscovery/session/{sessionId}`

### Navigation graph registration

The settings graph must register both routes so the typed backstack resolves correctly.

## Argument Contract

### `sessionId`

| Field | Type | Required | Rules |
|---|---|---:|---|
| `sessionId` | `String` | Yes for session detail | Must match a persisted `DiscoverySessionEntity.sessionId`. Use a UUID-like string; preserve case exactly. |

## Behavior Rules

1. If the landing deep link is opened while the user is not connected to a radio, the feature still opens and shows history / informational UI; only scan start is blocked.
2. If a session deep link references an unknown `sessionId`, the app should land on the discovery history/landing screen and show a non-fatal error or snackbar.
3. If a deep link includes a `destNum`, discovery should still verify that the feature is valid for the current local-radio context before allowing scan start.
4. Query parameters outside this contract should be ignored rather than causing route failure.

## Tests to Add

- `DeepLinkRouterTest`
  - `/settings/local-mesh-discovery`
  - `/settings/localMeshDiscovery`
  - `/settings/local-mesh-discovery/session/{sessionId}`
  - `/settings/{destNum}/local-mesh-discovery`
- `NavigationConfigTest`
  - serialization coverage for `SettingsRoute.LocalMeshDiscovery`
  - serialization coverage for `SettingsRoute.LocalMeshDiscoverySession(sessionId)`

## Failure Cases

| Input | Expected Result |
|---|---|
| `/settings/local-mesh-discovery/session/` | Ignore invalid detail path and fall back to `SettingsRoute.SettingsGraph + SettingsRoute.LocalMeshDiscovery` |
| `/settings/local-mesh-discovery/session/not-found` | Open discovery landing/history and show “session not found” UI state |
| `/settings/abc/local-mesh-discovery` | Treat `abc` as a subroute slug, not a `destNum`; fall back to discovery landing if matched, otherwise default settings behavior |
| Unknown query parameters | Ignore them |

## Implementation Notes

- `MainActivity` already forwards `ACTION_VIEW` intents through the shared deep-link router, so no discovery-specific activity plumbing should be required once router support exists.
- Keep path parsing centralized in `DeepLinkRouter` so Desktop and Android share the same typed route behavior.
- Prefer stable, human-readable route names because they become part of persistent backstack serialization and tests.
