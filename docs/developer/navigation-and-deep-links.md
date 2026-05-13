---
title: Navigation & Deep Links
nav_order: 4
last_updated: 2026-05-13
aliases:
  - deeplinks
  - navigation-3
  - routes
---

# Navigation & Deep Links

The app uses **Navigation 3** with typed, serializable routes and centralized deep link resolution.

## Route Architecture

All routes are defined in `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt`.

### Route Hierarchy

```kotlin
interface Route : NavKey           // All routes implement NavKey
interface Graph : Route            // Graph roots for navigation hierarchies

@Serializable
sealed interface SettingsRoute : Route {
    @Serializable data class SettingsGraph(val destNum: Int?) : SettingsRoute, Graph
    @Serializable data object DeviceConfiguration : SettingsRoute
    @Serializable data object HelpDocs : SettingsRoute
    @Serializable data class HelpDocPage(val pageId: String) : SettingsRoute
    // ...
}
```

### Conventions

- Routes are `@Serializable` for state restoration
- Use `data object` for routes without parameters
- Use `data class` for parameterized routes
- Group related routes under a `sealed interface`
- Graph entry points implement both the route interface and `Graph`

## Deep Link Router

`DeepLinkRouter` in `core/navigation` maps URI deep links to typed backstack lists.

### URI Format

```
meshtastic://meshtastic/{path}
```

### Supported Deep Links

| URI Path | Route | Notes |
|----------|-------|-------|
| `/connections` | `ConnectionsRoute.ConnectionsGraph` | Connections screen |
| `/settings` | `SettingsRoute.SettingsGraph(null)` | Settings root |
| `/settings/module-config` | `SettingsRoute.ModuleConfiguration` | Module config |
| `/settings/helpDocs` | `SettingsRoute.HelpDocs` | Docs browser |
| `/settings/helpDocs/{pageId}` | `SettingsRoute.HelpDocPage(pageId)` | Specific doc page |
| `/settings/help-docs` | `SettingsRoute.HelpDocs` | Compatibility alias |
| `/nodes` | `NodesRoute.NodesGraph` | Node list |
| `/nodes/{destNum}` | `NodesRoute.NodeDetail(destNum)` | Node detail |
| `/messages` | `ContactsRoute.ContactsGraph` | Conversation list |
| `/messages/{contactKey}` | `ContactsRoute.Messages(contactKey)` | Conversation |
| `/map` | `MapRoute.Map(null)` | Map view |
| `/firmware` | `FirmwareRoute.FirmwareGraph` | Firmware screen |
| `/channels` | `ChannelsRoute.ChannelsGraph` | Channel editor |

### Backstack Synthesis

Deep links synthesize a full backstack, not just the target screen:

```kotlin
// /settings/helpDocs/messages-and-channels produces:
listOf(
    SettingsRoute.SettingsGraph(null),
    SettingsRoute.HelpDocs,
    SettingsRoute.HelpDocPage("messages-and-channels"),
)
```

This ensures the user can navigate "up" correctly.

## Adding a Deep Link

1. Define the typed route in `Routes.kt`.
2. Add the mapping in `DeepLinkRouter.settingsSubRoutes` (or equivalent for other graphs).
3. Add a test in `DeepLinkRouterTest.kt`.
4. Register the navigation entry in the appropriate feature module.

## Navigation Entry Registration

Each feature module provides entries via an extension function:

```kotlin
fun EntryProviderScope<*>.docsEntries(backStack: NavBackStack) {
    entry<SettingsRoute.HelpDocs> { DocsBrowserScreen(backStack) }
    entry<SettingsRoute.HelpDocPage> { DocsPageRouteScreen(it.pageId, backStack) }
}
```

These are called from the settings navigation composition.

## Testing

Deep link routing is tested in:
```
core/navigation/src/commonTest/kotlin/org/meshtastic/core/navigation/DeepLinkRouterTest.kt
```

Example:
```kotlin
@Test
fun `help docs deep link routes correctly`() {
    val result = DeepLinkRouter.route(CommonUri.parse("meshtastic://meshtastic/settings/helpDocs"))
    assertEquals(
        listOf(SettingsRoute.SettingsGraph(null), SettingsRoute.HelpDocs),
        result,
    )
}
```

## Self-Referential Deep Links in Docs

User guide documentation pages can include a `deep_link` frontmatter field that maps the doc page to its corresponding app screen. When present, the in-app docs viewer shows an **"Open in App"** button in the top bar, allowing users to jump directly from documentation to the live feature.

### How It Works

1. The `deep_link` field in frontmatter specifies the target `meshtastic://` URI.
2. `DocBundleLoader` populates `DocPage.deepLink` from the page definition.
3. `DocsPageRouteScreen` renders a `TextButton` in the top app bar when `deepLink` is non-null.
4. Tapping the button fires the URI through the platform `UriHandler`, which routes back through `UIViewModel.handleDeepLink()` → `DeepLinkRouter`.

### Pages with Deep Links

| Doc Page | Deep Link | Target Screen |
|----------|-----------|---------------|
| Connections | `meshtastic://meshtastic/connections` | Connections |
| Messages & Channels | `meshtastic://meshtastic/messages` | Conversations |
| Nodes | `meshtastic://meshtastic/nodes` | Node list |
| Map & Waypoints | `meshtastic://meshtastic/map` | Map |
| Settings — Radio & User | `meshtastic://meshtastic/settings` | Settings root |
| Settings — Modules & Admin | `meshtastic://meshtastic/settings/module-config` | Module config |
| Firmware Updates | `meshtastic://meshtastic/firmware` | Firmware |

### Adding a Deep Link to a Doc Page

1. Add `deep_link: meshtastic://meshtastic/{path}` to the page's YAML frontmatter.
2. Add the matching `deepLink` parameter to the page's `UserPageDef` in `DocBundleLoader`.
3. The in-app docs viewer picks up the deep link automatically — no further UI changes needed.

> **Note:** Deep links in frontmatter are ignored by Jekyll and Docusaurus, ensuring no broken links on external doc sites. The `meshtastic://` URI scheme is only active in-app.

### Inline Deep Links in Markdown

The `DocsLinkUriHandler` also intercepts `meshtastic://` URIs in markdown content. Authors can use inline deep links for contextual "try it now" actions:

```markdown
Configure your LoRa preset in [Settings](meshtastic://meshtastic/settings/lora).
```

These links only work in the in-app docs viewer. On Jekyll/Docusaurus, they render as non-functional links. Use sparingly and always provide equivalent text instructions.

---

