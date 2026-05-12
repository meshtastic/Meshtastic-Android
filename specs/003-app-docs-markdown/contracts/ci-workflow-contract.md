# CI Workflow Contract: App Documentation (Android/KMP)

## Workflow 1: `docs-deploy.yml` (continuous beta publish)

**Trigger**: `push` to `main`  
**Runner**: `ubuntu-24.04`  
**Primary goal**: Build and publish `/beta/` docs, validate bundled assets, and surface screenshot changes via bot PRs.

### Required steps (in order)

| Step | Action | Expected output |
|------|--------|-----------------|
| Checkout | `actions/checkout@v6` with full history as needed and `submodules: true` | Repository workspace |
| JDK / Gradle setup | Reuse existing project setup action or equivalent | JDK 21 + Gradle cache |
| Docs bundle generation | `./gradlew generateDocsBundle -Pdocs.channel=beta -Pci=true` | Generated HTML/resources/index |
| Docs validation | `./gradlew validateDocsBundle -Pdocs.channel=beta -Pci=true` | Schema, size, and asset pass/fail |
| Screenshot generation/validation | `./gradlew recordDocsScreenshots -Pci=true` or the configured equivalent | Updated PNGs or validation report |
| Site artifact generation | `./gradlew publishDocsSite -Pdocs.channel=beta -Pci=true` | `_site/beta/` output |
| Screenshot diff handling | Detect changed PNGs and open/update bot PR | Reviewable screenshot changes |
| Upload Pages artifact | `actions/upload-pages-artifact` | Deployable artifact |
| Deploy Pages | `actions/deploy-pages` | Updated `/beta/` site |

### Required behavior

- The workflow MUST fail if docs generation fails.
- The workflow MUST fail if `validateDocsBundle` reports a schema error, missing asset, or bundle size above the hard limit.
- The workflow SHOULD warn (not fail) when screenshot automation is explicitly configured in validation-only mode, but it MUST still fail if referenced screenshot files are missing.
- The workflow MUST mark beta pages as pre-release in the published output.
- The workflow MUST avoid direct commits to `main` when screenshots change.

### Screenshot PR behavior

If screenshot PNGs differ after the screenshot step:
1. Create or update a bot branch (for example `bot/update-doc-screenshots`).
2. Commit only screenshot asset changes and any generated screenshot manifest files.
3. Open or update a PR targeting `main`.
4. Continue publishing docs only if the published site does not depend on uncommitted screenshot changes; otherwise fail with a clear message.

---

## Workflow 2: `docs-release.yml` (versioned stable release)

**Trigger**: `push` tags matching `v*.*.*`  
**Runner**: `ubuntu-24.04`  
**Primary goal**: Publish immutable docs for a released app version and refresh the version selector.

### Required steps (in order)

| Step | Action | Expected output |
|------|--------|-----------------|
| Checkout | `actions/checkout@v6` with tag context and `submodules: true` | Repository workspace |
| Extract version | Derive `X.Y.Z` from `vX.Y.Z` | Release version string |
| JDK / Gradle setup | Reuse existing project setup action or equivalent | JDK 21 + Gradle cache |
| Docs bundle generation | `./gradlew generateDocsBundle -Pdocs.channel=release -Pdocs.version=X.Y.Z -Pci=true` | Versioned generated docs |
| Docs validation | `./gradlew validateDocsBundle -Pdocs.version=X.Y.Z -Pci=true` | Validation pass/fail |
| Screenshot generation/validation | Run configured screenshot task | Release-aligned assets |
| Site artifact generation | `./gradlew publishDocsSite -Pdocs.version=X.Y.Z -Pci=true` | `_site/vX.Y.Z/` output |
| Update versions manifest | Append or update `docs/_data/versions.yml` | New version visible in selector |
| Refresh `/latest/` redirect | Point `/latest/` to `/vX.Y.Z/` | Stable default |
| Upload Pages artifact | `actions/upload-pages-artifact` | Deployable artifact |
| Deploy Pages | `actions/deploy-pages` | Updated stable docs |

### Required behavior

- Release docs MUST publish to `/vX.Y.Z/`.
- `/latest/` MUST point to the newest stable release.
- `/beta/` MUST remain separate and must not be overwritten by release publishing.
- The workflow MUST fail if the version manifest cannot be updated consistently.

---

## Gradle Task Interface Contract

### `generateDocsBundle`

**Purpose**: Convert markdown source into packaged docs artifacts and metadata.

**Inputs**:
- `docs/**/*.md`
- shared CSS/callout templates
- screenshot assets or manifests
- optional properties: `docs.channel`, `docs.version`

**Outputs**:
- generated HTML for each page
- optional bundled markdown mirror
- `index.json`
- generated resource/asset directories for `feature/docs`

**Failure conditions**:
- markdown parse failure
- invalid frontmatter
- missing required page metadata

### `validateDocsBundle`

**Purpose**: Enforce correctness and size constraints.

**Checks**:
- `index.json` matches `contracts/keyword-index-schema.json`
- every index entry maps to a packaged page
- every referenced screenshot asset exists
- bundle size warning at 8 MB, hard failure at 10 MB
- nav ordering and duplicate page IDs are valid

### `recordDocsScreenshots`

**Purpose**: Refresh screenshot assets using the chosen Android screenshot tool.

**Expected behavior**:
- Preferred implementation uses Roborazzi.
- Acceptable alternative uses Paparazzi.
- Output PNGs are written to the docs asset staging area or copied there immediately after generation.
- When running in validation-only mode, the task MUST still verify that referenced screenshots exist.

### `publishDocsSite`

**Purpose**: Assemble the final Pages artifact.

**Outputs**:
- `_site/beta/` for beta builds
- `_site/vX.Y.Z/` for tagged releases
- refreshed root redirects / version-selector inputs as required

---

## Environment Expectations

- JDK 21 is required.
- Android SDK availability is required only if the chosen screenshot task depends on Android tooling.
- CI should reuse the repository’s existing Gradle setup and cache strategy where possible.
- Commands must use explicit task paths or root lifecycle tasks; avoid ambiguous shorthand.

---

## Failure Modes

| Failure | Expected behavior |
|---------|-------------------|
| Markdown conversion fails | Workflow fails; no publish |
| Keyword-index schema invalid | Workflow fails; no publish |
| Screenshot task unavailable but validation-only mode is configured | Workflow warns and continues only if all referenced assets are present |
| Screenshot task fails in required-record mode | Workflow fails; no publish |
| Bundle size > 8 MB | Warning annotation |
| Bundle size > 10 MB | Hard failure; no publish |
| Pages upload/deploy failure | Workflow fails after artifact step |
| Bot PR creation fails | Workflow fails or emits explicit actionable error; silent failure is not allowed |

---

## Minimum Observability

The workflows should emit clear logs for:
- number of generated pages
- number of indexed pages
- total bundle size
- number of screenshot assets processed
- whether AI-specific docs sections were included unchanged or updated
- release version / channel being published
