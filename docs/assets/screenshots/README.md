# Screenshots

This directory contains screenshot assets referenced by the documentation pages.

Screenshots are sourced from the Compose Preview Screenshot Testing reference images
in `screenshot-tests/src/screenshotTestDebug/reference/`. Light-mode variants are
copied here for use by the Jekyll docs site and in-app documentation browser.

## Updating Screenshots

After changing a UI component, regenerate reference images and copy them here:

```bash
./gradlew :screenshot-tests:updateDebugScreenshotTest
```

Then copy the relevant light-mode PNGs from the reference directory. The
`copyDocsScreenshots` task automates bulk copying based on the manifest:

```bash
./gradlew :screenshot-tests:copyDocsScreenshots
```

## Naming Convention

```
{page-id}_{description}.png
```

Examples:
- `onboarding_welcome.png`
- `connections_bluetooth_scan.png`
- `firmware_disclaimer.png`

## Guidelines

- PNG format, light-mode only (dark variants live in reference directory)
- Name screenshots to match the docs page they appear in
- Keep filenames lowercase with underscores

