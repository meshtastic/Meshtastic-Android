# Screenshots

This directory is the **single source of truth** for screenshot assets referenced by the
documentation pages. It is consumed by both:

- the **Jekyll docs site** (markdown references `../../assets/screenshots/{name}.png`), and
- the **in-app docs browser** — `:feature:docs:syncDocsToComposeResources` bundles this
  directory into compose resources at `files/docs/assets/screenshots/`.

`DocImageWiringTest` (in `:feature:docs`) fails the build if a doc page references an image
that is not present here.

## Two source modules

Doc screenshots come from Compose Preview Screenshot Testing references in **two** modules:

- **`:screenshot-tests`** — visual-regression gate (CI runs `:screenshot-tests:validateDebugScreenshotTest`).
  Holds atomic, dual-purpose components (signal/battery/hops info, list items, preference widgets,
  alerts) that are both regression-checked **and** used as doc images. Don't reframe these for docs.
- **`:docs-screenshots`** — generate-only, **not** gated in CI. Holds doc-framed compositions whose
  framing is tuned for the docs site (e.g. the firmware status crops, the connections BLE-scan /
  empty-state crops). Reframing one here never churns the regression gate.

`copyDocsScreenshots` (in `:screenshot-tests`) aggregates the reference images from **both** modules.

## Updating Screenshots

After changing a UI component, regenerate references for whichever module owns the wrapper, then copy:

```bash
./gradlew :screenshot-tests:updateDebugScreenshotTest    # regression references
./gradlew :docs-screenshots:updateDebugScreenshotTest    # doc-framed composition references
./gradlew :screenshot-tests:copyDocsScreenshots          # refresh this directory from both
```

`copyDocsScreenshots` copies **only** the light-mode reference images that have a semantic
alias in `screenshot-tests/docs-screenshot-aliases.properties`, renaming them on the way.
Commit the refreshed PNGs together with the reference-image changes.

## Adding a Screenshot for a New Doc Page

1. Add (or reuse) a `Preview*`/`*Preview` composable with representative mock data in the
   feature module. Add a `Screenshot*` wrapper: in **`:docs-screenshots`** if it's a doc-framed
   composition (full screen / doc-specific crop), or in **`:screenshot-tests`** if it's an atomic
   component you also want regression-gated. If the component renders timestamps, give it a
   `timeTextOverride`-style parameter so renders stay deterministic across machines.
2. Make sure the test class is covered by a pattern in `screenshot-tests/docs-screenshots-manifest.txt`
   (the patterns are `**/{Class}Kt/...`, so they match in either module).
3. Map the semantic name in `screenshot-tests/docs-screenshot-aliases.properties`:
   `{page-id}_{description}.png=Screenshot{Name}_Light_{hash}_0.png`
4. Run the relevant update task(s) + `copyDocsScreenshots`, then reference the image from the doc page.

## Naming Convention

```
{page-id}_{description}.png
```

Examples: `onboarding_welcome.png`, `connections_bluetooth_scan.png`, `discovery_preset_result.png`.

## Guidelines

- PNG format, light-mode only (dark variants live in the reference directory)
- Name screenshots to match the docs page they appear in
- Keep filenames lowercase with underscores
- Prefer CST-generated screenshots — they render real app composables, so they cannot drift from
  reality. Avoid hand-pasted captures: a stray screenshot from another app slipped in this way
  before (the old `connections_wifi_*.png` were from an unrelated WiFi-provisioning app, not
  Meshtastic). If a manual capture is unavoidable, it must be a genuine Meshtastic-Android screen.
