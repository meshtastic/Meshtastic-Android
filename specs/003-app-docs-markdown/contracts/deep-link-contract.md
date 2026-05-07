# Deep Link Contract: Help & Documentation

## Public URLs

Primary user-facing contract:

```text
meshtastic://meshtastic/settings/helpDocs
```

Specific page form:

```text
meshtastic://meshtastic/settings/helpDocs/{pageId}
```

Accepted compatibility aliases:

```text
meshtastic://meshtastic/settings/help-docs
meshtastic://meshtastic/settings/help-docs/{pageId}
```

## Routing Behavior

| Component | Value |
|-----------|-------|
| Scheme | `meshtastic` |
| Host | `meshtastic` |
| Path | `/settings/helpDocs` or `/settings/helpDocs/{pageId}` |
| Normalized internal slug | `help-docs` |
| Query params | none in MVP |

## Typed Route Mapping

Planned additions in `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt`:

```kotlin
@Serializable data object HelpDocs : SettingsRoute
@Serializable data class HelpDocPage(val pageId: String) : SettingsRoute
```

### Routing rules

| Incoming URI | Resulting backstack |
|--------------|---------------------|
| `meshtastic://meshtastic/settings/helpDocs` | `listOf(SettingsRoute.SettingsGraph(null), SettingsRoute.HelpDocs)` |
| `meshtastic://meshtastic/settings/helpDocs/messages-and-channels` | `listOf(SettingsRoute.SettingsGraph(null), SettingsRoute.HelpDocs, SettingsRoute.HelpDocPage("messages-and-channels"))` |
| `meshtastic://meshtastic/settings/help-docs` | same as above after normalization |

## Dispatch Path

1. App receives the deep link as a `CommonUri`.
2. `DeepLinkRouter.route(uri)` normalizes hostless and host-based forms.
3. `settings` is resolved as the first path segment.
4. `helpDocs` or `help-docs` is normalized to the docs route.
5. `SettingsRoute.SettingsGraph(destNum = null)` is added as the root backstack entry.
6. `SettingsRoute.HelpDocs` is added.
7. If a `{pageId}` segment is present, `SettingsRoute.HelpDocPage(pageId)` is added.
8. `feature/settings/.../SettingsNavigation.kt` and `feature/docs/.../DocsNavigation.kt` render the docs browser/page.

## Required Code Changes

| File | Change |
|------|--------|
| `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt` | Add `HelpDocs` and `HelpDocPage` routes |
| `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/DeepLinkRouter.kt` | Map `helpDocs` / `help-docs` to docs routes |
| `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/navigation/SettingsNavigation.kt` | Add Settings entry point and docs destination wiring |
| `feature/docs/src/commonMain/kotlin/org/meshtastic/feature/docs/navigation/DocsNavigation.kt` | Register docs browser and page destinations |
| `README.md` | Document `meshtastic://meshtastic/settings/helpDocs` |

## Expected UX

- Root docs deep link opens the Help & Documentation home screen inside Settings.
- Specific page deep links open the targeted page while preserving a valid backstack.
- Unknown `{pageId}` values open the docs home screen and surface a "page not found" message with suggestions.

## Acceptance Tests

```kotlin
@Test
fun `root help docs deep link routes to settings docs`() {
    assertEquals(
        listOf(SettingsRoute.SettingsGraph(destNum = null), SettingsRoute.HelpDocs),
        route("/settings/helpDocs"),
    )
}

@Test
fun `page help docs deep link routes to docs page`() {
    assertEquals(
        listOf(
            SettingsRoute.SettingsGraph(destNum = null),
            SettingsRoute.HelpDocs,
            SettingsRoute.HelpDocPage("messages-and-channels"),
        ),
        route("/settings/helpDocs/messages-and-channels"),
    )
}
```

Where `route(path)` mirrors the style already used in `core/navigation/src/commonTest/kotlin/org/meshtastic/core/navigation/DeepLinkRouterTest.kt`.
