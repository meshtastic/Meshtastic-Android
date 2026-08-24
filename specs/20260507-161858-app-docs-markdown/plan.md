# Implementation Plan: App Documentation (Android/KMP)

**Branch**: `003-app-docs-markdown` | **Date**: 2026-05-07 | **Spec**: [spec.md](spec.md)  
**Status**: Not Started  
**Input**: Feature specification from `specs/003-app-docs-markdown/spec.md`

## Summary

Build a complete documentation system for Meshtastic-Android that includes:
1. a versioned GitHub Pages Jekyll site,
2. an offline in-app documentation browser inside Settings,
3. keyword search across the bundled docs corpus, and
4. an Android-only on-device AI assistant powered by Gemini Nano on supported devices, with graceful fallback elsewhere.

The implementation centers on a new `feature/docs/` KMP module using the `meshtastic.kmp.feature` plugin. Shared state, models, search, and most UI live in `commonMain`. Android-specific HTML rendering and Gemini integration live behind platform or flavor abstractions. Build-time markdown conversion is handled by Gradle using `flexmark-java`, and bundled output is packaged through generated resources/assets.

## Technical Context

**Language/Version**: Kotlin 2.3.x, Gradle Kotlin DSL, JDK 21  
**Primary Dependencies**: Compose Multiplatform, Navigation 3, Koin annotations, `flexmark-java` (or `commonmark-java` fallback), existing `multiplatform-markdown-renderer`, Android `WebView`, optional Gemini Nano via AICore / ML Kit GenAI Prompt API  
**Storage**: Bundled resources/assets only; no Room persistence for docs content  
**Testing**: KMP unit tests, Android host/unit tests, optional Roborazzi/Paparazzi screenshot tests, workflow validation  
**Target Platforms**: Android, Desktop JVM, iOS  
**Project Type**: KMP feature module + build logic + GitHub Pages deployment  
**Performance Goals**: TOC render < 1 second; AI response < 5 seconds on supported devices; docs workflow < 10 minutes  
**Constraints**: Bundle <= 10 MB, no `android.*` in `commonMain`, typed NavKey routes only, `fdroid` flavor must remain functional without proprietary AI stack  
**Scale/Scope**: ~14 User Guide pages, ~8 Developer Guide pages, one keyword index JSON, two GitHub Actions workflows, one new KMP module

## Architecture / Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| KMP boundaries respected | ✅ PASS | Shared models/search/UI live in `commonMain`; Android WebView + AI live outside `commonMain`. |
| Navigation 3 typed routes | ✅ PASS | Docs routes are added to `Routes.kt` and wired through `SettingsGraph`. |
| Koin annotations first | ✅ PASS | `feature/docs` will export a `FeatureDocsModule` and be included in app/desktop roots. |
| No Room for static docs | ✅ PASS | Docs are packaged assets/resources, not database rows. |
| Strings/resources discipline | ✅ PASS | UI labels for the browser/assistant go in `core/resources`. Long-form docs remain markdown files under `docs/`. |
| Flavor safety | ✅ PASS | Gemini bindings live in `google` flavor or Android-specific DI, with `fdroid` no-op fallback. |
| Design standards gate | ✅ REQUIRED | Must be reviewed before UI implementation and screenshot capture. |

**Gate result**: PASS. Proceed with design and implementation.

## Project Structure

### Spec artifacts

```text
specs/003-app-docs-markdown/
├── spec.md
├── data-model.md
├── research.md
├── plan.md
├── tasks.md
├── quickstart.md
├── checklists/
│   └── requirements.md
└── contracts/
    ├── deep-link-contract.md
    ├── keyword-index-schema.json
    └── ci-workflow-contract.md
```

### Planned source additions

```text
# Authored docs content
/docs/
├── _config.yml
├── index.md
├── _data/
│   └── versions.yml
├── user/
│   ├── onboarding.md
│   ├── connections.md
│   ├── messages-and-channels.md
│   ├── nodes.md
│   ├── node-metrics.md
│   ├── map-and-waypoints.md
│   ├── settings-radio-user.md
│   ├── settings-module-admin.md
│   ├── telemetry-and-sensors.md
│   ├── tak.md
│   ├── mqtt.md
│   ├── discovery.md
│   ├── firmware.md
│   └── desktop.md
└── developer/
    ├── architecture.md
    ├── codebase.md
    ├── adding-a-feature-module.md
    ├── navigation-and-deep-links.md
    ├── transport.md
    ├── persistence.md
    ├── testing.md
    └── contributing.md

# New KMP feature module
/feature/docs/
├── build.gradle.kts
├── src/commonMain/kotlin/org/meshtastic/feature/docs/
│   ├── model/DocModels.kt
│   ├── data/DocBundleLoader.kt
│   ├── data/KeywordSearchEngine.kt
│   ├── ui/DocsBrowserScreen.kt
│   ├── ui/DocsSearchBar.kt
│   ├── ui/DocsPageRouteScreen.kt
│   ├── ui/ComposeResourceImageTransformer.kt
│   ├── ui/ChirpyAssistantSheet.kt
│   ├── navigation/DocsNavigation.kt
│   ├── di/FeatureDocsModule.kt
│   └── ai/AIDocAssistant.kt
├── src/androidMain/kotlin/org/meshtastic/feature/docs/
│   ├── ui/DocHtmlView.android.kt
│   └── ai/AndroidDocsPlatformCapabilities.kt
├── src/jvmMain/kotlin/org/meshtastic/feature/docs/
│   └── ui/DocPageRenderer.jvm.kt
├── src/iosMain/kotlin/org/meshtastic/feature/docs/
│   └── ui/DocPageRenderer.ios.kt
└── src/commonTest/kotlin/org/meshtastic/feature/docs/
    ├── DocBundleLoaderTest.kt
    ├── KeywordSearchEngineTest.kt
    └── DocsNavigationTest.kt

# Host / flavor integration
/core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/
├── Routes.kt
└── DeepLinkRouter.kt

/feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/navigation/
└── SettingsNavigation.kt

/app/src/main/kotlin/org/meshtastic/app/di/AppKoinModule.kt
/app/src/google/kotlin/org/meshtastic/app/di/FlavorModule.kt
/app/src/google/kotlin/org/meshtastic/app/docs/GoogleDocsAiModule.kt
/app/src/fdroid/kotlin/org/meshtastic/app/di/FlavorModule.kt
/desktop/src/main/kotlin/org/meshtastic/desktop/di/DesktopKoinModule.kt

# Build logic and workflows
/build-logic/convention/src/main/kotlin/org/meshtastic/buildlogic/DocsTasks.kt
/.github/workflows/docs-deploy.yml
/.github/workflows/docs-release.yml
```

## Module Design

### `feature/docs/` responsibilities

**commonMain**
- Data model (`DocPage`, `DocBundle`, `KeywordIndexEntry`)
- Bundle loading and metadata validation
- Search normalization and ranking
- TOC UI, grouped list UI, loading states, error states
- Shared assistant UI state and Chirpy chat presentation
- Navigation entry registration for `SettingsRoute.HelpDocs` and `SettingsRoute.HelpDocPage`

**androidMain**
- `WebView` container via `AndroidView`
- Platform capability checks for WebView and AI
- Optional Android-only page rendering helpers

**jvmMain / iosMain**
- Renderer implementation backed by Compose markdown or embedded browser abstraction
- No Android framework dependencies

### AI boundary

The feature module should not hard-code Google-only APIs in shared code. Instead:

```kotlin
interface AIDocAssistant {
    suspend fun answer(question: String): AIDocAssistantResult
}
```

Bindings:
- `google` flavor Android: Gemini Nano implementation
- `fdroid` flavor Android: keyword-search fallback implementation
- Desktop/iOS: keyword-search fallback implementation

This keeps `feature/docs` KMP-friendly and protects the `fdroid` flavor from proprietary dependencies.

> **Cross-spec note (F3):** Feature 001 (Local Mesh Discovery) defines a parallel `DiscoveryRecommendationEngine` interface with the same platform-gating and fallback pattern. The two abstractions are intentionally separate because their prompts, result types, and domain contexts differ significantly. If a third AI-powered feature is added, a shared `core:ai` capability-check and session-factory module should be extracted to avoid further duplication.

## Build Pipeline Plan

### Docs generation

Implement Gradle tasks in build logic rather than shell scripts.

Recommended task layout:

```text
generateDocsBundle     # generate packaged docs artifacts + index + CSS sync
validateDocsBundle     # schema, missing assets, bundle size, ordering
recordDocsScreenshots  # preferred Roborazzi/Paparazzi capture task
publishDocsSite        # generate _site/ tree for Pages deployment
```

Possible internal task breakdown:
- parse `docs/**/*.md`
- extract frontmatter (`title`, `nav_order`, `section`, optional aliases)
- strip unsupported frontmatter/attribute lines before HTML conversion
- render HTML with `flexmark-java`
- inject shared CSS and page-level `data-page` marker
- generate `index.json`
- sync bundled artifacts into generated common resources and Android assets
- enforce the 8 MB warning / 10 MB hard limit

### Asset bundling strategy

**Canonical source**
- Markdown: `/docs/**`
- Shared styles: generated + checked-in CSS
- Screenshots: generated or curated PNGs referenced by markdown

**Generated outputs**
- `feature/docs/build/generated/docs/common/` → packaged into shared resources
- `feature/docs/build/generated/docs/androidAssets/` → optional Android WebView mirror
- `_site/` → Pages deployment artifact

**Reasoning**
- Avoid committing generated HTML into source directories.
- Keep the same content pipeline for both website and in-app docs.
- Support Android WebView without forcing every target to use assets.

### Screenshot image adapter for Compose renderer (FR-038)

The `DocsPageRouteScreen` on Desktop/iOS uses `com.mikepenz.markdown.m3.Markdown` for rendering. By default it uses `NoOpImageTransformerImpl`, which silently drops all `![alt](path)` image references. Two changes are needed to render screenshots inline:

**1. Bundle screenshots into Compose resources**

The `syncDocsToComposeResources` task must include `assets/screenshots/**/*.png` alongside `user/**/*.md` and `developer/**/*.md`. The `:screenshot-tests:copyDocsScreenshots` task must run first to populate `docs/screenshots/`, which is then synced as `assets/screenshots/` into compose resources. Task dependency: `syncDocsToComposeResources.dependsOn(":screenshot-tests:copyDocsScreenshots")`.

**2. Custom `ImageTransformer` using `Res.getUri()` + Coil3**

The CMP generated `Res` object provides two APIs:
- `suspend fun readBytes(path: String): ByteArray` — cannot be used in `@Composable`
- `fun getUri(path: String): String` — **synchronous**, returns a platform URI

Since `ImageTransformer.transform(link: String)` is `@Composable` (not suspend), we CANNOT use `Res.readBytes()` directly. Instead, use `Res.getUri()` to resolve the local resource URI, then pass it to Coil3's `rememberAsyncImagePainter()`:

```kotlin
// ComposeResourceImageTransformer.kt
class ComposeResourceImageTransformer : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? {
        if (link.startsWith("http://") || link.startsWith("https://")) return null
        val resourcePath = "files/docs/$link" // e.g., "files/docs/assets/screenshots/foo.png"
        val uri = Res.getUri(resourcePath)
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(uri)
                .size(coil3.size.Size.ORIGINAL)
                .build()
        )
        return ImageData(painter)
    }
}
```

**Why Coil3 after all?** The `ImageTransformer.transform()` is `@Composable`, not `suspend`. `Res.readBytes()` is a suspend function and cannot be called in composition. `Res.getUri()` gives us a synchronous local URI that Coil3 can load asynchronously via `rememberAsyncImagePainter`. Since the project already has Coil 3.4.0 in the version catalog, this is the correct approach — it handles the composable→async bridge that CMP resources require.

**Dependencies**: Add `libs.coil` to `feature/docs/build.gradle.kts` commonMain dependencies.

Wire into the renderer in `DocsPageRouteScreen.kt`:
```kotlin
Markdown(
    content = markdownText,
    imageTransformer = ComposeResourceImageTransformer(),
    modifier = ...
)
```

## Navigation Plan

### Route additions

Add to `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt`:

```kotlin
@Serializable data object HelpDocs : SettingsRoute
@Serializable data class HelpDocPage(val pageId: String) : SettingsRoute
```

### Deep links

Extend `DeepLinkRouter` so the following resolve into typed routes:
- `meshtastic://meshtastic/settings/helpDocs`
- `meshtastic://meshtastic/settings/helpDocs/{pageId}`
- optional canonical alias `meshtastic://meshtastic/settings/help-docs`

### Settings integration

Update `feature/settings/.../SettingsNavigation.kt` so Help & Documentation appears as a new Settings row and the docs routes are registered as part of the same Settings graph.

## UI Plan

### Docs browser

- Sectioned TOC grouped into User Guide / Developer Guide
- Search bar filtering by title, aliases, and keywords
- Loading, empty-state, and missing-page surfaces
- Responsive layout that works in compact phones and larger Desktop/iPad form factors

### Chirpy assistant

- Shared Compose chat UI with `LazyColumn`
- Right-aligned user messages, left-aligned Chirpy messages
- Pinned bottom input bar with `imePadding()`
- Scroll-to-dismiss-keyboard behavior
- Source chips or page links beneath generated answers
- Hidden entirely when the bound assistant implementation reports unsupported

### Styling

- UI labels and button text go into `core/resources/.../strings.xml`
- Long-form docs stay in markdown under `/docs/`
- Use `MeshtasticIcons` for help/search/info/security references inside app UI
- Chirpy avatar shipped as vector/SVG compatible asset

## AI Integration Plan

### Supported environments

**Primary target**
- Android 14+
- `google` flavor
- Runtime Gemini Nano/AICore availability

**Fallback targets**
- Android `fdroid`
- Android devices without supported model/runtime
- Desktop
- iOS

### Flow

1. User submits a question.
2. Shared search engine normalizes and scores pages from `index.json`.
3. Top-ranked pages are selected within the token budget.
4. Android Google implementation calls Gemini Nano on-device.
5. If unavailable or busy, return fallback result with suggested pages.

### Safety/UX guardrails

- Never send source code or private mesh data to the model.
- Never show the AI input when the implementation is unsupported.
- Always include the supporting page links used for the answer.
- Respect runtime quota/battery/busy errors with clear user messaging.

## Testing Plan

### Unit / common tests

- `DocBundleLoaderTest`: index decoding, page ordering, missing assets, size metadata
- `KeywordSearchEngineTest`: scoring, aliases, tie-breaking, token budgeting
- `DocsNavigationTest`: route serialization, deep-link mapping, page navigation

### Android-specific tests

- WebView surface smoke test or rendering wrapper test
- AI capability gate tests
- Screenshot tests for docs browser TOC and state/icon reference assets

### Integration validation

- `./gradlew :feature:docs:detekt :feature:docs:allTests`
- `./gradlew kmpSmokeCompile`
- docs-specific workflow dry run (site build + schema validation)

## CI / Release Plan

### Continuous beta

- Trigger: push to `main`
- Runner: `ubuntu-24.04`
- Steps:
  1. checkout + submodules
  2. JDK 21 + Gradle setup
  3. `./gradlew generateDocsBundle validateDocsBundle publishDocsSite -Pdocs.channel=beta -Pci=true`
  4. run `recordDocsScreenshots` or screenshot validation task
  5. compare screenshot asset diff and open bot PR if changed
  6. upload Pages artifact and deploy `/beta/`

### Release publish

- Trigger: tag `v*.*.*`
- Adds version manifest update, `/latest/` redirect refresh, and stable site publish to `/vX.Y.Z/`

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Gemini Nano API or device support changes | Medium | Hide AI behind interface + runtime gating; keep keyword fallback first-class |
| WebView/resource packaging is awkward on Android | Medium | Mirror generated HTML into Android assets if direct resource loading is unreliable |
| Screenshot automation adds too much CI cost | Medium | Prefer Roborazzi on Ubuntu and keep validation-only fallback mode documented |
| Docs bundle exceeds size budget | Medium | Enforce size checks and trim screenshots before adding more features |
| Desktop/iOS rendering parity diverges from Android HTML | Medium | Keep `DocPageContent` capable of serving both HTML and markdown and test representative pages on all targets |

## Implementation Phases

1. **Phase 0 – Design standards gate**: review design standards, confirm docs/browser/chat visuals.
2. **Phase 1 – Content authoring**: write user and developer markdown, collect screenshots.
3. **Phase 2 – Web site setup**: Jekyll config, versioning, Pages scaffolding.
4. **Phase 3 – Build pipeline**: Gradle tasks, markdown conversion, bundle validation, generated resources.
5. **Phase 4 – In-app browser**: feature module, navigation, TOC, page rendering.
6. **Phase 5 – Search/index**: keyword index, search bar, result ranking.
7. **Phase 6 – AI assistant**: Android Google flavor Gemini implementation + fallbacks.
8. **Phase 7 – CI automation**: deploy/release workflows, screenshot PR bot.
9. **Phase 8 – Polish**: accessibility, dark mode, edge cases, documentation cleanup.
10. **Phase 9 – Apple alignment**: per-page TOC icons, signal-meter/units-locale pages, staleness CI.
11. **Phase 10 – Docusaurus sync & content gaps**: meshtastic.org sync script + workflow, WebP optimization, translate page, developer measurement page.
12. **Phase 11 – Governance consolidation**: shared frontmatter library, filesystem-derived slugs, workflow merge, 3-consumer propagation model, preview & screenshot staleness advisories.

## Validation Matrix

| Change Area | Minimum Validation |
|-------------|--------------------|
| Docs markdown / build pipeline | `./gradlew generateDocsBundle validateDocsBundle` |
| Shared docs feature logic | `./gradlew :feature:docs:allTests` |
| Android rendering integration | `./gradlew :feature:docs:compileKotlinJvm kmpSmokeCompile` plus Android host tests if added |
| CI/workflow updates | Dry-run logic locally where possible, then validate YAML structure and contract |
| Final feature branch verification | `./gradlew spotlessCheck detekt kmpSmokeCompile test allTests` plus docs generation tasks |

## Definition of Done

- `feature/docs/` exists and is wired into settings navigation and DI.
- Website docs build and deploy from the same source corpus used in-app.
- Deep links open docs home and specific pages through typed routes.
- Search works on all platforms.
- Gemini Nano assistant works on supported Android Google devices and gracefully falls back everywhere else.
- CI enforces schema, size, and deployment correctness.
- Documentation and assets remain under the configured bundle-size ceiling.
- Android docs are synced to meshtastic.org via automated workflow under `docs/software/android/`.
- A "Translate the App" user page and a developer measurement page exist.
- All docs CI checks are consolidated into a single `docs-governance.yml` workflow.
- Governance scripts share a common frontmatter library with filesystem-derived slug discovery.
