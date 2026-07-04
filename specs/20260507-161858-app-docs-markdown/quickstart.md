# Quickstart: App Documentation (Android/KMP)

## Prerequisites

- JDK 21
- Android SDK (`ANDROID_HOME` set or discoverable)
- Git submodules initialized
- `local.properties` present (`cp secrets.defaults.properties local.properties` if needed)
- Ruby + Bundler only if you want to preview Jekyll locally
- No Node.js requirement

## 1. Bootstrap the workspace

```bash
git submodule update --init
[ -f local.properties ] || cp secrets.defaults.properties local.properties
```

## 2. Author or edit docs content

Markdown source lives in:

- `docs/user/*.md`
- `docs/developer/*.md`

Example frontmatter:

```markdown
---
title: Messages & Channels
nav_order: 3
aliases:
  - channels
  - direct-messages
---

# Messages & Channels
```

## 3. Generate the bundled docs corpus

```bash
./gradlew generateDocsBundle validateDocsBundle
```

Expected outputs:

- generated HTML for Android/Web docs parity
- optional markdown mirror for Compose renderers
- `index.json` keyword index
- shared CSS and callout styling
- size/schema/asset validation

## 4. Build the GitHub Pages site artifact locally

```bash
./gradlew publishDocsSite -Pdocs.channel=beta
```

This should produce a deployable `_site/` tree with `/beta/` output.

## 5. Refresh screenshot assets

Preferred path if Roborazzi is used:

```bash
./gradlew recordDocsScreenshots
```

If the project adopts Paparazzi instead, run the equivalent Paparazzi record task defined by the implementation.

## 6. Run docs-specific tests

```bash
./gradlew :feature:docs:allTests :feature:docs:detekt
./gradlew kmpSmokeCompile
```

## 7. Run full repo verification before shipping

```bash
./gradlew spotlessCheck detekt assembleDebug test allTests generateDocsBundle validateDocsBundle
```

## 8. Preview the Jekyll site locally (optional)

```bash
cd docs
bundle exec jekyll serve --livereload
# open http://127.0.0.1:4000
```

Recommended gems:

```bash
gem install bundler jekyll just-the-docs jekyll-redirect-from
```

## 9. Test the in-app route

Open the app and navigate to:

- **Settings → Help & Documentation**

Deep link contract:

```text
meshtastic://meshtastic/settings/helpDocs
```

Optional specific-page form:

```text
meshtastic://meshtastic/settings/helpDocs/messages-and-channels
```

## 10. Verify target-specific behavior

### Android
- Docs open in a WebView-backed page renderer
- `google` flavor on supported Android 14+ devices may show Chirpy AI
- `fdroid` flavor must still show keyword search and docs pages

### Desktop / iOS
- Docs open through the shared renderer abstraction
- Keyword search works even when AI is unsupported

## Key file locations

| Path | Purpose |
|------|---------|
| `docs/` | Authored markdown content and site config |
| `feature/docs/` | New KMP feature module for in-app docs |
| `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt` | Typed docs routes |
| `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/DeepLinkRouter.kt` | Deep-link mapping |
| `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/navigation/SettingsNavigation.kt` | Settings entry point |
| `build-logic/convention/.../DocsTasks.kt` | Gradle docs pipeline |
| `.github/workflows/docs-deploy.yml` | Continuous beta docs deploy |
| `.github/workflows/docs-release.yml` | Versioned release deploy |

## Troubleshooting

**`generateDocsBundle` fails on markdown parsing**  
Check frontmatter syntax, unsupported attribute lines, and table formatting.

**`validateDocsBundle` reports missing assets**  
Confirm every referenced screenshot exists in the generated or curated asset set.

**AI assistant not visible on Android**  
Check device support, Android version, flavor (`google` only), and runtime model availability.

**Desktop/iOS page looks different from Android**  
Confirm whether the renderer is using HTML or markdown mode and compare against the generated HTML output.

**Bundle size exceeds 10 MB**  
Trim screenshots, compress assets, or reduce duplicated artifacts before raising the limit.
