# Screenshots

This directory is the **single source of truth** for screenshot assets referenced by the
documentation pages. It is consumed by both:

- the **Jekyll docs site** (markdown references `../../assets/screenshots/{name}.png`), and
- the **in-app docs browser** — `:feature:docs:syncDocsToComposeResources` bundles this
  directory into compose resources at `files/docs/assets/screenshots/`.

`DocImageWiringTest` (in `:feature:docs`) fails the build if a doc page references an image
that is not present here.

## Updating Screenshots

Most screenshots are generated from the Compose Preview Screenshot Testing reference images
in `screenshot-tests/src/screenshotTestDebug/reference/`. After changing a UI component:

```bash
./gradlew :screenshot-tests:updateDebugScreenshotTest   # regenerate reference images
./gradlew :screenshot-tests:copyDocsScreenshots         # refresh this directory
```

`copyDocsScreenshots` copies **only** the light-mode reference images that have a semantic
alias in `screenshot-tests/docs-screenshot-aliases.properties`, renaming them on the way.
Commit the refreshed PNGs together with the reference-image changes.

## Adding a Screenshot for a New Doc Page

1. Add (or reuse) a `Preview*`/`*Preview` composable with representative mock data in the
   feature module, and a `Screenshot*` wrapper in `screenshot-tests` (see
   `DiscoveryScreenshotTests.kt` for the pattern). If the component renders timestamps, give
   it a `timeTextOverride`-style parameter so renders stay deterministic across machines.
2. Make sure the test class is covered by `screenshot-tests/docs-screenshots-manifest.txt`.
3. Map the semantic name in `screenshot-tests/docs-screenshot-aliases.properties`:
   `{page-id}_{description}.png=Screenshot{Name}_Light_{hash}_0.png`
4. Run the two Gradle tasks above and reference the image from the doc page.

## Naming Convention

```
{page-id}_{description}.png
```

Examples: `onboarding_welcome.png`, `connections_bluetooth_scan.png`, `discovery_preset_result.png`.

## Guidelines

- PNG format, light-mode only (dark variants live in the reference directory)
- Name screenshots to match the docs page they appear in
- Keep filenames lowercase with underscores
- A few screenshots (`connections_wifi_*.png`) are manual captures with no CST source yet;
  they are hand-maintained until matching previews exist
