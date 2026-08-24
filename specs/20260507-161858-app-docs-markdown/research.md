# Research: App Documentation (Android/KMP)

## R-001: CI/CD pipeline architecture for docs generation and deployment

**Decision**: Use GitHub Actions on `ubuntu-24.04` with Gradle-driven docs generation and deployment. Split the workflow into a docs-build path and an optional screenshot path inside the same workflow. Prefer `Roborazzi` for screenshot automation because it fits the repository’s Gradle-heavy Ubuntu CI and does not require a macOS runner.

**Rationale**:
- Meshtastic-Android already runs its main CI on `ubuntu-24.04` for Gradle-heavy jobs. Reusing that environment aligns with the current runner strategy in `.github/workflows/reusable-check.yml`.
- Markdown conversion can run entirely inside Gradle with JDK 21, requiring no platform-specific build tools.
- Roborazzi integrates better with Compose and Gradle pipelines than a custom screenshot export shell flow. Paparazzi remains acceptable if the UI under test ends up being Android-only.

**Alternatives considered**:
- **macOS runner**: Rejected for the default path because it is slower, more expensive, and unnecessary for Gradle docs generation.
- **Native GitHub Pages Jekyll build**: Rejected because we need build-time preprocessing, schema validation, asset copying, and bundle-size enforcement.
- **Paparazzi as the default**: Considered. Deferred in favor of Roborazzi because the project already leans on KMP and Gradle-first tooling, while Paparazzi is more Android-view-centric.

**Implementation direction**:
- `docs-deploy.yml`: push to `main` → generate `/beta/`, validate assets, deploy Pages.
- `docs-release.yml`: tag `v*.*.*` → generate `/vX.Y.Z/`, update versions manifest, update `/latest/`, deploy Pages.
- Both workflows use JDK 21, existing Gradle setup helpers, and explicit Gradle task paths.

---

## R-002: Version selector strategy for release-specific docs

**Decision**: Keep the web documentation on Jekyll + GitHub Pages using a `just-the-docs` version selector backed by `_data/versions.yml`.

**Rationale**:
- The versioning problem is platform-independent and maps cleanly to this project's release cadence.
- `just-the-docs` gives us sidebar navigation, search, and version-switch UI without building a custom docs frontend.
- The in-app bundle remains pinned to the shipped app version, while the web site can expose stable and beta versions at the same time.

**Alternatives considered**:
- **Always show latest docs**: Rejected. The app ships multiple versions in the wild and docs must track app releases, not only firmware.
- **Separate repos or branches per release**: Rejected due to maintenance overhead and reduced traceability.
- **Custom static site frontend**: Rejected for unnecessary complexity.

**Implementation direction**:
- `docs/index.md` redirects to `/latest/`.
- `docs/_data/versions.yml` is updated by the release workflow.
- Beta builds publish `/beta/` with a visible pre-release banner.

---

## R-003: Markdown rendering strategy across Android, Desktop, and iOS

**Decision**: Use `flexmark-java` in Gradle for canonical markdown-to-HTML conversion. Package generated HTML for Android WebView parity and optionally package a markdown mirror so Desktop/iOS can use an existing Compose markdown renderer when that provides a better UX than an embedded browser.

**Rationale**:
- The repository already includes `com.mikepenz:multiplatform-markdown-renderer` in `gradle/libs.versions.toml`, so Desktop/iOS can reuse an existing CMP-oriented path.
- `flexmark-java` provides better GitHub-Flavored Markdown support than bare `commonmark-java`, including tables, task lists, and richer extension handling.
- Android WebView is the safest way to preserve exact site parity for complex tables, callouts, and responsive screenshots.

**Alternatives considered**:
- **`commonmark-java`**: Viable, but weaker out-of-the-box GFM coverage.
- **Compose-only rendering on all targets**: Rejected as the primary path because HTML/site parity is more important on Android, and not all advanced formatting maps cleanly to Compose text rendering.
- **Platform-specific web surfaces on every target**: Considered, but a Compose markdown renderer remains attractive for Desktop/iOS if it avoids platform web wrappers.

**Implementation direction**:
- Gradle task parses markdown, strips frontmatter, and generates HTML + index metadata.
- Android reads HTML through WebView.
- Desktop/iOS use `DocPageContent` and a renderer abstraction that can choose HTML or markdown at runtime.

---

## R-004: Keyword index JSON structure and search strategy

**Decision**: Generate a compact JSON array of `KeywordIndexEntry` objects containing `id`, `title`, `section`, `resourcePath`, `navOrder`, `keywords`, `aliases`, and `charCount`.

**Rationale**:
- The same index can power three different use cases: TOC ordering, keyword search, and AI retrieval.
- Including `navOrder` prevents the in-app browser from drifting away from authored reading order.
- Including `resourcePath` avoids recomputing target-specific asset paths at runtime.

**Alternatives considered**:
- **Full-text search index (Lunr-style)**: Rejected as overkill for the initial corpus size.
- **Vector embeddings**: Rejected because they add model complexity, larger assets, and more opaque retrieval behavior.
- **Store entire page text in the index**: Rejected because it duplicates the actual docs corpus and inflates bundle size.

**Implementation direction**:
- Keywords are generated from normalized headings, emphasized terms, and curated aliases.
- Search ranks title matches above keyword matches.
- AI retrieval consumes only top-ranked pages within the token budget.

---

## R-005: Android WebView and cross-platform rendering surface

**Decision**: On Android, wrap `WebView` with `AndroidView` in `androidMain` and load packaged HTML via local assets/resources. On Desktop/iOS, define a small `DocRenderSurface` abstraction so the module can choose either an embedded browser or Compose markdown renderer without changing the shared search/navigation logic.

**Rationale**:
- Android WebView is a mature, well-understood path for local HTML and complex CSS.
- Shared search state, bundle loading, and route handling belong in `commonMain`; only the final rendering surface needs to vary by platform.
- A renderer abstraction keeps the docs feature compatible with the project rule of preferring interfaces + DI over heavy `expect`/`actual` usage for non-trivial capabilities.

**Alternatives considered**:
- **Pure `expect`/`actual` screen per platform**: Rejected because it duplicates too much UI state and search logic.
- **Android-only docs feature**: Rejected because the project is KMP and the docs corpus is valuable on Desktop and iOS too.
- **Single Compose markdown renderer everywhere**: Rejected as the default because Android WebView gives better fidelity for generated HTML and web-site parity.

**Implementation direction**:
- `commonMain`: `DocBrowserScreen`, search state, TOC, models, bundle loader.
- `androidMain`: `DocHtmlView.android.kt`, optional Gemini Nano engine hooks.
- `jvmMain` / `iosMain`: renderer-specific page surface.

---

## R-006: Asset bundling strategy with Gradle resources and Android assets

**Decision**: Generate docs artifacts into `feature/docs/build/generated/...` and wire them into the module through Gradle source directories. Keep a shared common-resources path for KMP targets and optionally mirror generated HTML into Android assets for WebView file loading.

**Rationale**:
- Gradle must own the bundling pipeline for all targets.
- Generated outputs should not be committed into `src/` just to satisfy packaging.
- Android WebView sometimes benefits from asset-backed file URLs, while Desktop/iOS can read from common packaged resources.

**Alternatives considered**:
- **Commit generated HTML into source directories**: Rejected because it mixes authored and generated artifacts and encourages drift.
- **Bundle only markdown and render everything at runtime**: Rejected because it increases runtime complexity and risks inconsistent output.
- **Bundle only HTML and no markdown mirror**: Considered. Accepted for Android, but a markdown mirror remains useful for Desktop/iOS Compose rendering.

**Implementation direction**:
- Canonical source: `docs/**/*.md`.
- Generated output: `feature/docs/build/generated/docs/{common,android-site}`.
- Gradle registers generated resource directories lazily with provider APIs.
- Android asset mirror is produced only if the WebView implementation requires it.

---

## R-007: Navigation integration with SettingsGraph and typed routes

**Decision**: Add docs routes to `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt` as part of `SettingsRoute`, and register docs entries from `feature/docs` into the Settings graph. Deep links are resolved by `DeepLinkRouter` into typed docs routes, not raw strings.

**Rationale**:
- The repository already centralizes routes in `Routes.kt` and uses typed `@Serializable sealed interface` navigation.
- The docs feature is conceptually a Settings subsection, not a new top-level destination.
- Keeping docs routes typed ensures deep-link tests, saved state, and backstack behavior stay consistent with the rest of the app.

**Alternatives considered**:
- **Standalone `DocsRoute` graph**: Considered, but rejected for the first iteration because the entry point still belongs to Settings and would require extra indirection.
- **Raw string route names inside the feature module**: Rejected because it breaks the repository’s Navigation 3 convention.
- **App-only docs route wiring**: Rejected because Desktop also needs the route in shared navigation.

**Implementation direction**:
- Add `SettingsRoute.HelpDocs` and `SettingsRoute.HelpDocPage(val pageId: String)`.
- Extend `DeepLinkRouter.settingsSubRoutes` with `help-docs` and `helpDocs` aliases.
- Add docs navigation entries from `feature/docs` and wire them into `feature/settings` flow composition.

---

## Summary of chosen approach

1. **Web docs** stay on Jekyll + GitHub Pages.
2. **Build pipeline** moves into Gradle using `flexmark-java` and generated resources.
3. **In-app browser** becomes a new KMP feature module under `feature/docs/`.
4. **Android rendering** uses WebView; **Desktop/iOS** use a renderer abstraction.
5. **AI** is Android-only, on-device, and flavor-gated with keyword-search fallback everywhere else.
6. **CI** remains Ubuntu/Gradle-first and adds screenshot automation only through Android-friendly tooling.
