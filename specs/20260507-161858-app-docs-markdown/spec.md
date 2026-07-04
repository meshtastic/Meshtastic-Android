# Feature Specification: App Documentation (Jekyll Site + In-App Docs + Gemini Nano Assistant)

**Feature Branch**: `003-app-docs-markdown`  
**Created**: 2026-05-07  
**Status**: Not Started  
**Input**: User description: "Complete markdown documentation for the Meshtastic Android app — served as a GitHub Pages Jekyll site, bundled in-app for offline browsing and Gemini Nano Q&A, with GitHub Actions CI auto-regeneration on push to main. Covers both end users and developers."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - End User Reads Docs on the Web (Priority: P1)

A new Meshtastic user visits the GitHub Pages documentation site to learn how to pair a radio, send messages, understand node status, and troubleshoot common tasks. They can browse a structured documentation site with screenshots, plain-language explanations, and release-specific versions.

**Why this priority**: This delivers immediate value to the broadest audience, is platform-independent, and can ship before any in-app UI work.

**Independent Test**: Navigate the deployed GitHub Pages site, open "Getting Started", complete the pairing flow, then move to "Messages & Channels" and "Nodes" using the sidebar without using source code or the app.

**Acceptance Scenarios**:

1. **Given** a user opens the docs site root, **When** they choose "Getting Started", **Then** they see a step-by-step guide to first-run setup with screenshots or annotated illustrations for each major step.
2. **Given** a user is on any page, **When** they use the sidebar or version selector, **Then** they can reach another page in two interactions or fewer.
3. **Given** a user is reading a feature page such as "Messages & Channels", **Then** they see at least one inline screenshot and a plain-language explanation of the main visible controls, indicators, and states.

---

### User Story 2 - End User Browses Docs Inside the App (Priority: P2)

A user already using the app opens **Settings → Help & Documentation** and browses the same content offline. Android devices render bundled HTML inside a WebView, while Desktop and iOS use a shared embedded browser abstraction or Compose markdown renderer backed by the same bundled content.

**Why this priority**: It meets users in context, works offline, and turns documentation into part of the product instead of an external destination.

**Independent Test**: Put the device in airplane mode, open **Settings → Help & Documentation**, search for "channel", open the matching page, and read the full article with inline images.

**Acceptance Scenarios**:

1. **Given** the app is installed, **When** the user opens Help & Documentation, **Then** a table of contents with User Guide and Developer Guide sections appears within 1 second.
2. **Given** a page contains images or tables, **When** the page renders on Android, Desktop, or iOS, **Then** screenshots and formatting appear inline without network access.
3. **Given** the device is offline, **When** the user opens any bundled page, **Then** content loads successfully from packaged resources or assets.

---

### User Story 3 - End User Asks the App a Question with AI (Priority: P3)

A user on a supported Android device types "How do I set a channel password?" into the in-app assistant. The app retrieves relevant doc pages from the bundled corpus and answers using Gemini Nano running on-device. On unsupported Android devices, `fdroid`, Desktop, and iOS, the app falls back to keyword search and suggested articles.

**Why this priority**: It is high-value and differentiating, but depends on the documentation corpus and in-app browser already existing.

**Independent Test**: On a supported Android 14+ device in the `google` flavor, open Help & Documentation, ask "How do I add a waypoint?", and receive an on-device answer or a clear keyword-search fallback if the model is unavailable.

**Acceptance Scenarios**:

1. **Given** a supported Android 14+ device with Gemini Nano available, **When** the user submits a question, **Then** a response appears within 5 seconds using only bundled documentation as context.
2. **Given** an unsupported device, unsupported flavor, Desktop, or iOS, **When** the user opens Help & Documentation, **Then** they see keyword search and suggested pages with no broken AI controls.
3. **Given** the model is busy, unavailable, or the question is outside the documentation scope, **When** the assistant responds, **Then** it acknowledges the limitation and offers direct links to relevant doc pages instead of hallucinating.

---

### User Story 4 - Developer Reads Architecture Docs (Priority: P4)

A contributor opens the Developer Guide to understand the KMP architecture, Navigation 3 patterns, DI wiring, transport layers, testing approach, and how to add a new feature module.

**Why this priority**: It improves contributor onboarding and reduces tribal knowledge, but is less urgent than end-user documentation.

**Independent Test**: A contributor unfamiliar with Meshtastic-Android reads the Architecture and Codebase docs and can explain the path from a `Routes.kt` NavKey to a screen and from transport events to shared state.

**Acceptance Scenarios**:

1. **Given** a developer visits the Developer Guide, **When** they browse the section, **Then** they see separate pages for Architecture, Codebase Structure, Adding a Feature, Transport, Persistence, Testing, and Contributing.
2. **Given** a developer reads any architecture page, **Then** key modules, routes, and responsibilities are described without requiring prior repository knowledge.

---

### User Story 5 - Docs Stay Current Automatically (Priority: P5)

When documentation-relevant UI or workflow changes merge to `main`, GitHub Actions rebuilds the docs site, refreshes screenshot assets when the configured screenshot task is enabled, validates the packaged bundle, and republishes the beta docs. Release tags publish immutable versioned docs.

**Why this priority**: Without automation, docs drift quickly and lose trust.

**Independent Test**: Merge a PR that changes a user-facing settings label. The docs workflow rebuilds the affected page, validates or regenerates screenshots, updates the beta site, and opens a bot PR if screenshot assets changed.

**Acceptance Scenarios**:

1. **Given** a push to `main`, **When** the docs workflow runs, **Then** it rebuilds the site, validates the index and bundle size, and refreshes screenshot assets using the configured Android screenshot pipeline.
2. **Given** a release tag `vX.Y.Z`, **When** the release workflow completes, **Then** a versioned `/vX.Y.Z/` docs snapshot is published and the version selector is updated.
3. **Given** the docs build, screenshot validation, or schema validation fails, **When** the workflow completes, **Then** deployment is blocked and the failure is reported clearly.

---

### Edge Cases

- What if Gemini Nano or AICore becomes unavailable mid-session? Show a fallback message and surface the top keyword-matched pages.
- What if the active flavor is `fdroid` and the AI stack is unavailable by design? Show the same browser and search UI with no dead-end affordances.
- What if a screenshot file referenced by markdown is missing? Render the page without the image, log the problem in validation, and fail CI before deployment.
- What if the user opens a deep link to a page slug removed in a later version? Open the docs home screen and surface a "page not found" message with suggested pages.
- What if the user asks a question in a language different from the English source docs? Answer in the user’s language when the runtime supports it; otherwise fall back to English plus suggested articles.
- What if the screenshot framework is temporarily disabled or unavailable in CI? The workflow may run in validation-only mode for screenshots, but it must still fail on missing referenced assets and must report that automation is degraded.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The documentation system MUST produce markdown source files organised into at least two top-level sections: **User Guide** and **Developer Guide**.
- **FR-002**: Every User Guide page MUST include at least one screenshot or annotated illustration where a corresponding asset exists for the documented feature.
- **FR-003**: The Jekyll site MUST be built and deployed by GitHub Actions using `actions/deploy-pages`. All markdown conversion, asset validation, version-path assembly, and size enforcement MUST happen before the Pages deployment step. Native GitHub Pages Jekyll build MUST be disabled via `.nojekyll` in the published output.
- **FR-004**: `docs/_config.yml` MUST configure Jekyll with `just-the-docs` or an equivalent minimal theme that supports sidebar navigation, search, and a hierarchy matching the doc section structure.
- **FR-005**: All authored documentation source MUST live under the repository-root `docs/` directory so that content changes are reviewed alongside app changes.
- **FR-006**: The app MUST expose **Help & Documentation** inside the Settings flow. The feature MUST live inside the existing Navigation 3 settings graph and MUST NOT introduce a new top-level tab or destination group.
- **FR-007**: The in-app doc browser MUST render GitHub Flavored Markdown content offline. Android MUST load generated bundled HTML through `WebView` via `AndroidView`. Desktop and iOS MUST render the same bundled content through either an embedded browser surface or a shared Compose markdown renderer.
- **FR-008**: Screenshot assets used by docs MUST come from checked-in documentation captures, Android screenshot automation (`Roborazzi` preferred, `Paparazzi` acceptable), or equivalent generated PNG assets, and MUST be copied into the bundled docs output during the build pipeline.
- **FR-009**: On supported Android 14+ devices running the `google` flavor, Help & Documentation MUST expose free-text Q&A powered by Gemini Nano running on-device through Google AI Edge SDK (the canonical API across the project; see also spec 001).
- **FR-010**: On unsupported Android devices, unsupported flavors, Desktop, and iOS, Help & Documentation MUST show the standard doc browser with keyword search and suggested pages only. No broken AI controls or loading indicators may be shown.
- **FR-011**: The AI integration MUST use a pre-built keyword index JSON generated at build time to retrieve the top 2–3 most relevant pages for a given question. The combined prompt context passed to Gemini Nano consists of bundled documentation context and active radio node diagnostics. The prompt context retrieval budget is dynamically tuned to leverage the available on-device model context limit.
- **FR-012**: A GitHub Actions workflow MUST trigger on every push to `main` and: (a) build the docs site, (b) run the configured screenshot generation or validation task, (c) sync screenshot assets into the docs bundle, and (d) deploy the beta docs site.
- **FR-013**: If CI detects screenshot asset changes, it MUST open an automated PR from a bot branch targeting `main`. It MUST NOT push screenshot changes directly to `main`.
- **FR-014**: The initial User Guide MUST cover at minimum these 14 feature areas: Onboarding & First Launch, Bluetooth / USB / TCP Device Connection, Messages & Channels, Nodes List, Node Detail & Metrics, Map & Waypoints, Settings – Radio & User, Settings – Module & Administration, Telemetry & Sensors, TAK Integration, MQTT, Local Mesh Discovery, Firmware Updates, and Desktop App / Cross-Platform Hosts.
- **FR-015**: The initial Developer Guide MUST cover at minimum: Architecture Overview, Codebase Structure, Adding a New Feature Module, Navigation 3 & Deep Links, BLE / TCP / Serial Transport, Room KMP & DataStore Architecture, Testing (unit + screenshot), and Contributing & PR Workflow.
- **FR-016**: The markdown-to-HTML build step MUST use a JVM-native parser such as `flexmark-java` (preferred) or `commonmark-java` so it can run inside Gradle on the existing CI infrastructure without adding Node.js.
- **FR-017**: Because Meshtastic-Android has no existing help system, documentation authoring MUST treat current in-app screens, existing user-facing strings, and feature module flows as the authoritative source of truth. At minimum, content mapping MUST be established from:
  - `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/WelcomeScreen.kt`, `BluetoothScreen.kt`, `LocationScreen.kt`, `NotificationsScreen.kt`, `CriticalAlertsScreen.kt` → onboarding, permissions, and connection setup pages.
  - `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/ui/contact/Contacts.kt`, `Message.kt`, `component/MessageScreenComponents.kt` → Messages & Channels.
  - `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/list/NodeListScreen.kt`, `detail/NodeDetailScreens.kt`, and `metrics/*` → Nodes, metrics, and telemetry pages.
  - `feature/map/src/androidMain/kotlin/org/meshtastic/feature/map/MapScreen.kt` and `feature/map/src/commonMain/kotlin/org/meshtastic/feature/map/navigation/MapNavigation.kt` → Map & Waypoints.
  - `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/SettingsViewModel.kt`, `DeviceConfigurationScreen.kt`, `ModuleConfigurationScreen.kt`, and `navigation/SettingsNavigation.kt` → settings documentation.
  - `feature/firmware/src/commonMain/kotlin/org/meshtastic/feature/firmware/FirmwareUpdateScreen.kt` → firmware update guidance and warnings.
- **FR-018**: The docs authoring process MUST lift short-form guidance from onboarding screens, warnings, banners, disclaimers, and empty-state messaging into highlighted Tips/Warnings callouts inside the relevant pages.
- **FR-019**: The in-app doc browser MUST use typed Navigation 3 routes declared in `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt` and registered through the Settings flow. The docs home entry MUST behave like any other `NavKey` destination and support standard back navigation via `NavigationBackHandler`.
- **FR-020**: Documentation versions MUST be tied to app release versions, not firmware versions:
  - A git tag push matching `v*.*.*` MUST publish a stable snapshot under `/vX.Y.Z/`.
  - Pushes to `main` without a release tag MUST overwrite `/beta/` and visibly mark pages as pre-release.
  - The site MUST expose a version selector for stable releases and current beta.
  - The bundled in-app docs MUST always match the app version that shipped with them.
  - The site root MUST redirect to the latest stable release by default.
- **FR-021**: The complete bundled docs corpus (HTML, markdown resources if retained, index JSON, CSS, SVGs, and screenshots) MUST NOT exceed 10 MB at build time. The build MUST warn at 8 MB and fail at 10 MB.
- **FR-022**: Generated docs styling MUST support light and dark themes. CSS MUST define theme variables for background, text, links, code blocks, and callouts. Android `WebView` surfaces and non-Android renderer surfaces MUST align with Material 3 background colors to avoid white flashes during load.
- **FR-023**: Bundled docs MUST be delivered through Gradle-managed resources. Shared docs assets MUST be available to KMP targets through common resources, and Android MUST additionally support an asset-based mirror if local `WebView` file access requires `AssetManager` paths.
- **FR-024**: Help & Documentation MUST be addressable by a deep link that resolves to the Settings graph. The canonical deep link slug MUST be lowercase-hyphenated (`meshtastic://meshtastic/settings/help-docs`), consistent with the project-wide deep link convention established in spec 001. A camelCase compatibility alias (`helpDocs`) MUST also be accepted for backward compatibility.
- **FR-025**: The Gradle docs-generation task MUST strip YAML frontmatter and unsupported attribute lines before HTML conversion so they never appear as literal content in generated pages.
- **FR-026**: The build pipeline MUST post-process markdown output so Tip and Warning blockquotes become styled callouts, and beta builds inject a reusable pre-release banner class instead of inline styles.
- **FR-027**: The shared docs stylesheet MUST define `.tips-callout`, `.warning-callout`, and `.pre-release-banner` using CSS custom properties so those surfaces adapt cleanly in light and dark mode.
- **FR-028**: Each generated `KeywordIndexEntry` MUST include a `navOrder` integer sourced from frontmatter. The in-app bundle loader MUST sort pages by `navOrder` within each section instead of sorting alphabetically.
- **FR-029**: The AI assistant MUST be branded as **Chirpy** and presented as a chat UI, not a plain form. The UI MUST use a scrollable message list, right-aligned user bubbles, left-aligned Chirpy bubbles, a pinned input bar, session message history, and keyboard-dismiss-on-scroll behavior appropriate to Compose. The AI assistant MUST support word-by-word streaming responses (via `answerStream` and `Flow<AIDocAssistantResult>`) with real-time, in-place UI updates and responsive error state transitions (including "routing through the mesh..." loaders and themed, localized connection-error messaging).
- **FR-030**: Any User Guide page with matching screenshot assets MUST embed those assets inline using normal markdown image syntax or generated equivalent HTML. Images MUST be responsive and visually bounded with radius and spacing rules.
- **FR-031**: The screenshot strategy MUST include dedicated icon/state coverage for connection status, security/lock indicators, node status, and at least one docs-browser rendering case. The generated assets MUST be copied into the docs bundle and referenced from the appropriate pages.
- **FR-032**: Any page showing two or more small icon or state screenshots as standalone blocks MUST instead convert them into reference tables with at least Icon and Description columns. Named states or roles MUST use a 3-column Icon / Name / Description table.
- **FR-033**: Icon-level screenshot tests SHOULD use transparent PNG output when the selected screenshot framework supports alpha output, and they MUST render plain icon composables rather than interactive wrappers so background artifacts do not leak into docs assets.
- **FR-034**: The generated HTML template MUST emit a stable page identifier (for example `data-page="messages"`) so page-specific CSS overrides remain possible while keeping a shared global icon/table baseline.
- **FR-035**: Chirpy branding MUST use a shared SVG or vector drawable sourced from the Meshtastic design system and bundled as a scalable asset that renders crisply across Android, Desktop, and iOS.
- **FR-036**: Connection-state icon captures and inline docs illustrations MUST use `MeshtasticIcons` equivalents and Material 3 semantic colors so they remain legible in both light and dark themes without relying on ad-hoc color inversion.
- **FR-037**: Lock and security icon captures using `MeshtasticIcons.Lock`, `MeshtasticIcons.LockOpen`, `MeshtasticIcons.KeyOff`, or their final equivalents MUST preserve portrait aspect ratio at the shared 44dp reference height and MUST avoid canvas squashing in generated docs assets.
- **FR-039**: A sync script and GitHub Actions workflow MUST exist to export Android in-app docs into the `meshtastic/meshtastic` Docusaurus site under `docs/software/android/`. The script MUST handle relative link resolution (sibling `.md` links, image paths) and produce Docusaurus-compatible frontmatter. The workflow MUST open a PR in `meshtastic/meshtastic` rather than pushing directly, matching the pattern established by Apple's `sync-apple-docs.yml`.
- **FR-040**: The docs site sync pipeline SHOULD convert screenshot PNGs to WebP format before publishing to the Docusaurus site. A `--convert-webp` flag or equivalent MUST be supported. Original PNGs remain canonical in-repo; WebP is a site-publishing optimization only.
- **FR-041**: A `docs/user/translate.md` page MUST explain how contributors can translate the Android app via Crowdin. The page MUST link to the Crowdin project, describe which resource files are translatable (composeResources strings, user guide markdown), and provide step-by-step contribution instructions. This is a contributor guide, not a runtime translation feature.
- **FR-042**: A `docs/developer/measurement.md` page MUST document the `MetricFormatter` API, locale-aware unit conversion patterns, and how to add new measurement types. This provides developer-facing guidance complementing the user-facing `units-and-locale.md`.
- **FR-043**: Documentation governance MUST enforce a 3-consumer propagation model: every doc page automatically flows to (1) the in-app docs browser via `syncDocsToComposeResources`, (2) the Jekyll/GitHub Pages site via `docs-deploy.yml`, and (3) the Docusaurus meshtastic.org site via `sync-android-docs.js`. CI MUST validate that every `docs/**/*.md` page slug is registered in `DocBundleLoader.kt` for in-app discovery.
- **FR-044**: Doc governance scripts MUST share a common frontmatter parsing library (`scripts/lib/frontmatter.js`) to avoid duplication across link validation, freshness checks, coverage checks, and sync scripts. Slug discovery MUST be filesystem-derived, not hardcoded.
- **FR-045**: All docs CI checks (staleness, link validation, coverage, freshness, registry validation) MUST be consolidated into a single governance workflow with separate jobs for staleness detection and quality gates.
- **FR-046**: The governance workflow MUST include an advisory preview-staleness job that detects UI composable changes (`feature/*/ui/`, `core/ui/`) without corresponding `*Previews.kt` updates. The check MUST be bypassable via a `skip-preview-check` label.
- **FR-047**: The governance workflow MUST include an advisory screenshot-reference-staleness job that detects `*Previews.kt` changes without updates to reference images in `screenshot-tests/src/screenshotTestDebug/reference/`. The advisory MUST include the `updateDebugScreenshotTest` command.
- **FR-048**: User Guide documentation MUST support a three-tier translation cascade: (1) **Crowdin bundled** — community-translated markdown shipped with the app via Crowdin `%android_code%` locale directories synced to CMP `files-{qualifier}/` resources; (2) **ML Kit runtime** — on-device translation via `DocTranslationService` on supported Android `google` flavor devices when no Crowdin translation exists; (3) **English fallback** — the default `files/docs/` content used when neither bundled nor runtime translation is available. The `fdroid` flavor, Desktop, and iOS MUST use a `NoOpDocTranslator` that skips ML Kit and falls back to English. Developer Guide pages remain English-only. The locale resolution chain MUST try region-qualified (`files-pt-rBR/`) then language-only (`files-pt/`) before English.
- **FR-049**: The `crowdin.yml` configuration MUST use the `%android_code%` placeholder for docs translation paths so that Crowdin outputs locale directories in CMP resource qualifier format (e.g., `pt-rBR`, `zh-rCN`) with zero format conversion needed at build or runtime. The `syncTranslatedDocsToComposeResources` Gradle task MUST copy translated docs into `composeResources/files-{locale}/docs/` without locale format transformation.
- **FR-050**: The Jekyll documentation site MUST support locale-qualified paths using Android resource qualifier format (`docs/pt-rBR/user/*.md`) with a `_data/locales.yml` registry of supported locales including native names and text direction (LTR/RTL). The site MUST include a language switcher component.
- **FR-051**: A `currentLocaleQualifier()` expect/actual function in `core:common` MUST return the device locale in CMP resource qualifier format (e.g., `"pt-rBR"` or `"fr"`). This function is used by `DocBundleLoader` to resolve locale-qualified resource paths at runtime.
- **FR-038**: The Compose Multiplatform markdown renderer (`multiplatform-markdown-renderer-m3`) used by `DocsPageRouteScreen` on non-WebView targets MUST be configured with a custom `ImageTransformer` that resolves relative image paths (e.g., `assets/screenshots/*.png`) to bundled Compose resource URIs via `Res.getUri()` and loads them asynchronously using Coil 3 (`rememberAsyncImagePainter`). The default `NoOpImageTransformerImpl` MUST NOT be used for docs rendering. The `syncDocsToComposeResources` task MUST include screenshot assets alongside markdown files so that images are available at runtime. The `copyDocsScreenshots` task from `screenshot-tests/` MUST be wired as a dependency of `syncDocsToComposeResources` to ensure generated screenshots are available before resource bundling.
- **FR-052**: The on-device assistant MUST inject real-time local radio node diagnostics (hardware model, firmware, battery percentage/voltage, user names, channel utilization, and active mesh peer counts) from `NodeRepository` directly into the system prompt context on every query, enabling highly-personalized, offline network troubleshooting.
- **FR-053**: The context retrieval budget for the on-device model (`MAX_CONTEXT_CHARS`) MUST be tuned to **32,000 characters** to leverage the native 32K token context window of the modern `nano-v4-full` (Gemini Nano v4) model, supplying vastly more complete documentation fragments and richer multi-turn conversation history.

### Key Entities

- **DocPage**: A single documentation topic with a stable slug, title, section, nav order, resource path, keywords, and token budget metadata.
- **DocSection**: A top-level grouping such as User Guide or Developer Guide that determines sidebar and in-app TOC hierarchy.
- **KeywordIndexEntry**: A generated JSON descriptor used by both keyword search and Gemini Nano retrieval.
- **DocBundle**: The runtime view of all packaged docs, screenshots, CSS, and metadata available offline.
- **AIDocAssistant**: The shared docs-assistant abstraction that provides on-device answers on supported Android devices and fallback search elsewhere.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 14 feature areas in FR-014 have a published User Guide page in the initial release.
- **SC-002**: All Developer Guide topics in FR-015 have a published page in the initial release.
- **SC-003**: The in-app docs browser displays a table of contents within 1 second on supported devices.
- **SC-004**: A user can locate the answer to "How do I connect a device?" in 3 steps or fewer from the in-app Help entry point.
- **SC-005**: The beta docs CI workflow rebuilds and republishes documentation within 10 minutes of a push to `main`.
- **SC-006**: On supported Gemini Nano devices, the assistant responds to a valid documentation question within 5 seconds using only on-device processing.
- **SC-007**: Unsupported devices and flavors still provide keyword search and suggested pages with no broken AI affordances.
- **SC-008**: The published docs home page scores 90+ on Lighthouse accessibility.
- **SC-009**: Every User Guide page with matching screenshot assets embeds at least one inline image sourced from the bundled docs asset set.
- **SC-010**: A Desktop App / Cross-Platform Hosts page exists and documents Desktop-specific entry points, transport options, and parity notes.
- **SC-011**: Pages that document icon-heavy states use reference tables instead of repeated standalone icon blocks.
- **SC-012**: Icon/state screenshot coverage exists for connection state, security/lock state, node status, and at least one docs-browser rendering case.
- **SC-013**: The Chirpy assistant appears as a chat interface with a bundled vector asset and preserves per-session message history while the browser is open.
- **SC-014**: Connection and security icon assets remain legible in light and dark modes and preserve expected aspect ratio in generated docs output.
- **SC-015**: Android docs are published on meshtastic.org under `docs/software/android/` and stay current via automated sync workflow.
- **SC-016**: A "Translate the App" page exists in the User Guide and links to the Crowdin project with step-by-step contribution instructions.
- **SC-017**: A developer measurement/locale page exists documenting `MetricFormatter` internals and locale-aware patterns.
- **SC-018**: All docs CI checks (staleness, links, coverage, freshness, registry) run in a single consolidated `docs-governance.yml` workflow with no duplicate validation steps across workflows.
- **SC-019**: Sync script slug discovery is filesystem-derived — adding a new `.md` file under `docs/` requires no hardcoded string updates in scripts.
- **SC-020**: PRs that modify UI composables without updating previews receive an advisory PR comment with a checklist. The check is bypassable via `skip-preview-check` label.
- **SC-021**: PRs that modify previews without updating screenshot reference images receive an advisory PR comment with the regeneration command.
- **SC-022**: The `crowdin.yml` docs entries use `%android_code%` placeholders and the sync task copies translations into CMP resources with zero locale format conversion.
- **SC-023**: `DocBundleLoader` resolves locale-qualified docs resources using a region → language → English fallback chain driven by `currentLocaleQualifier()`.
- **SC-024**: On Google-flavor Android devices, ML Kit runtime translation is available as a fallback when no Crowdin bundled translation exists for the user's locale.
- **SC-025**: The Jekyll site supports 38 locale paths with a language switcher, and all locale directories use Android resource qualifier format (`pt-rBR`, not `pt-BR`).

## Clarifications

### Session 2026-05-07

- Q: Where should the feature live in the codebase? → A: In a new KMP feature module at `feature/docs/` using the `meshtastic.kmp.feature` plugin.
- Q: Where does the in-app entry point belong? → A: Inside the existing settings flow, routed as a typed Navigation 3 destination under `SettingsRoute` and `SettingsGraph`.
- Q: How should in-app docs render across targets? → A: Android uses `WebView` with bundled HTML; Desktop and iOS use a shared embedded browser abstraction or Compose markdown renderer over the same bundled content.
- Q: What converts markdown to bundled HTML? → A: A Gradle task using `flexmark-java` (preferred) or `commonmark-java`.
- Q: How should search and AI retrieval work? → A: A generated keyword index JSON powers both keyword search and Gemini Nano retrieval. Runtime retrieval uses top-ranked pages only.
- Q: Which targets get AI? → A: Supported Android 14+ devices in the `google` flavor when Gemini Nano/AICore is available. `fdroid`, Desktop, and iOS fall back to keyword search.
- Q: How should the feature respect KMP architecture boundaries? → A: Shared models, search, and UI state live in `commonMain`; Android-specific `WebView` and Gemini Nano code live in `androidMain` or host flavor bindings.
- Q: Which existing app content is authoritative for the first draft of docs? → A: Existing Compose screens, strings, warnings, and feature flows in `feature/intro`, `feature/messaging`, `feature/node`, `feature/map`, `feature/settings`, and `feature/firmware`.
- Q: How should screenshot automation be adapted? → A: Prefer Roborazzi because it fits Gradle-heavy Ubuntu CI and Compose UI; Paparazzi remains acceptable if it better fits the final implementation.
- Q: How should versioning work? → A: Release tags publish immutable versioned docs; `main` publishes `/beta/`; the in-app bundle stays pinned to the shipped app version.
- Q: What is the bundle size ceiling? → A: 10 MB hard limit, 8 MB warning threshold.
- Q: What deep link should external callers use? → A: `meshtastic://meshtastic/settings/help-docs` (canonical, lowercase-hyphenated per project convention). The camelCase form `helpDocs` is accepted as a backward-compatible alias.

## Assumptions

- No existing help system, docs routes, or AI integrations are present in Meshtastic-Android today; this feature introduces the full stack.
- Docs source content is authored in English. Translating long-form docs into the app’s 35+ UI languages is out of scope for this feature.
- The published website remains GitHub Pages + Jekyll because that is platform-independent and already familiar to Meshtastic contributors.
- The repo’s existing CI architecture on `ubuntu-24.04` remains the default execution environment for docs build and screenshot automation.
- Roborazzi is the preferred screenshot technology for Android/KMP automation, but Paparazzi is an acceptable substitute if implementation constraints require it.
- Gemini Nano availability is gated at runtime and may vary by hardware, region, downloaded models, and Google/AICore rollout. Unsupported environments must gracefully fall back to keyword search.
- The `google` flavor can host AI bindings; `fdroid` must remain functional without requiring proprietary AI integrations.
- Chirpy branding will be sourced from the Meshtastic design repository and packaged as a vector-compatible asset for all KMP targets.

## Apple Alignment (Cross-Platform Parity)

### Session 2026-05-12

Gap analysis against `meshtastic-apple` identified these alignment items for Android:

**Implemented:**
1. **Per-page TOC icons** — Apple uses SF Symbols per `DocPage`; Android now maps `iconId` to `MeshtasticIcons` via `DocPageIconResolver.kt`.
2. **Signal meter user guide page** — `docs/user/signal-meter.md` explains RSSI vs SNR, bar-level criteria, and LoRa-specific signal concepts. Adapted from Apple equivalent for Android signal surfaces.
3. **Units & locale user guide page** — `docs/user/units-and-locale.md` explains automatic metric/imperial formatting via `MetricFormatter`. Adapted from Apple equivalent for Android/KMP stack.
4. **Docs staleness CI workflow** — `.github/workflows/docs-staleness.yml` posts advisory PR comments when user-facing UI files change without corresponding `docs/` updates. Adapted from Apple's workflow for KMP feature/core paths.

**Skipped (platform-specific to Apple):**
- `docs/user/watch.md` — watchOS-only
- `docs/user/carplay.md` — iOS CarPlay only
- `docs/developer/carplay.md` — iOS CarPlay architecture only
- `docs/developer/swiftdata.md` — Android has `persistence.md` (Room KMP)
- `docs/developer/deep-links.md` — Android has `navigation-and-deep-links.md`
- TipKit contextual tips — iOS TipKit has no direct KMP equivalent; contextual help is deferred

**Corrected (previously skipped, now implemented or planned):**
- `docs/user/translate.md` — Previously marked "iOS Translate framework only" but is actually a **Crowdin contribution guide** applicable to all platforms. Android equivalent planned as FR-041.
- `docs/developer/measurement.md` — Previously marked "covered by user page" but Apple version provides developer-facing API guidance. Android equivalent planned as FR-042.

### Session 2026-05-13

Gap analysis against PR [meshtastic/meshtastic#2393](https://github.com/meshtastic/meshtastic/pull/2393) (Apple docs sync to Docusaurus) and `meshtastic-apple` `specs/003-app-docs-markdown/` identified these additional alignment items:

**Planned (Phase 10):**
1. **Docusaurus sync script + workflow** — Apple has `sync-apple-docs.js` + `sync-apple-docs.yml` syncing in-app docs to meshtastic.org under `docs/software/apple/`. Android needs an equivalent `sync-android-docs.js` publishing to `docs/software/android/` (FR-039).
2. **WebP image optimization** — Apple converts PNGs to WebP for the docs site via `--convert-webp` flag. Android should add the same optimization (FR-040).
3. **Translate the App page** — `docs/user/translate.md` contributor guide for Crowdin (FR-041).
4. **Developer measurement page** — `docs/developer/measurement.md` for MetricFormatter API docs (FR-042).

**Confirmed non-goals:**
- `docs/user/watch.md`, `docs/user/carplay.md`, `docs/developer/carplay.md` — remain Apple-only.

**Implemented (Phase 11 — Governance & Consolidation):**

Audit of docs infrastructure identified duplication across 4 JS scripts, 3 CI workflows, and hardcoded slug registries. Consolidation implemented:

1. **Shared frontmatter library** — `scripts/lib/frontmatter.js` provides `parseFrontmatter()`, `discoverSlugs()`, and `forEachDocPage()` used by all governance scripts. Eliminated 4 independent frontmatter parsers (FR-044).
2. **Filesystem-derived slugs** — `sync-android-docs.js` now auto-discovers page slugs from `docs/user/` and `docs/developer/` instead of maintaining hardcoded `KNOWN_*_SLUGS` sets (26 strings eliminated). The slug registry CI check is no longer needed (FR-044).
3. **Workflow consolidation** — `docs-staleness.yml` merged into `docs-governance.yml` as a parallel `staleness` job alongside the existing `validate` job. Duplicate link validation removed from `docs-deploy.yml` (FR-045).
4. **3-consumer propagation** — Constitution principle VI updated to explicitly name in-app, Jekyll, and Docusaurus consumers with propagation rules. Staleness check comment includes new-page checklist (FR-043).
5. **Duplicate script removal** — `sync-android-docs.js` copy removed from meshtastic/meshtastic PR #2405 since the workflow runs from the Android repo clone.

**Implemented (Phase 11 continued — Preview & Screenshot Governance):**

Extended governance to cover preview composables and screenshot testing:

1. **Preview staleness advisory** — `preview-staleness` job detects UI composable changes without `*Previews.kt` updates. Posts advisory PR comment with checklist. Bypassable via `skip-preview-check` label (FR-046).
2. **Screenshot reference staleness advisory** — Same job detects preview changes without reference image updates. Posts advisory with `updateDebugScreenshotTest` command (FR-047).
3. **Workflow renamed** — `Docs Governance` → `UI & Docs Governance` to reflect expanded scope.
4. **Contributing checklist** — `docs/developer.md` updated with preview/screenshot maintenance guidance.

### Session 2026-05-18

Translation cascade and locale pipeline implementation:

1. **Scope change** — Translating User Guide docs is now in scope. Crowdin provides community translations bundled with the app; ML Kit provides runtime fallback on Google flavor. Developer Guide remains English-only.
2. **Zero-conversion locale pipeline** — Crowdin `%android_code%` outputs locale directories in CMP resource qualifier format (`pt-rBR`, `fr`). The sync task just prepends `files-` — no format conversion at build or runtime.
3. **Locale resolution chain** — `DocBundleLoader.localeQualifiers()` tries region-qualified (`files-pt-rBR/`) → language-only (`files-pt/`) → English default (`files/`).
4. **Platform actuals** — `currentLocaleQualifier()` expect/actual in `core:common` returns CMP qualifier format on all platforms.
5. **ML Kit translation** — `DocTranslationService` interface with `MlKitDocTranslator` (google), `NoOpDocTranslator` (fdroid/desktop/iOS). `DocTranslationCache` provides file-backed caching. `MarkdownTranslationSegmenter` splits docs into translatable segments preserving markdown structure.
6. **Web i18n** — Jekyll `_config.yml` and `_data/locales.yml` support 38 locales with Android qualifier paths. Language switcher included.
