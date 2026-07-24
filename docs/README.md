# Documentation Structure

This directory contains the source documentation for the Meshtastic Android/Desktop/iOS app.
It serves three consumers:

1. **In-app docs browser** — bundled via Compose Resources at build time
2. **Jekyll site** — GitHub Pages (this directory is the Jekyll source root)
3. **meshtastic.org** — Docusaurus sync (upstream consumption)

## Locale Layout

```
docs/
├── _config.yml, _data/, _layouts/, _sass/   ← Jekyll site infrastructure
├── en/                                       ← English source (edit here)
│   ├── user/                                 ← User Guide pages
│   ├── developer/                            ← Developer Guide pages
│   ├── index.md                              ← Site home page
│   ├── user.md                               ← User Guide nav parent
│   └── developer.md                          ← Developer Guide nav parent
├── fr-rFR/                                   ← French (Crowdin-generated)
│   └── user/                                 ← Translated user guide
├── de-rDE/                                   ← German (Crowdin-generated)
│   └── user/
└── ...                                       ← Other locales
```

## Editing Guidelines

- **English source**: Edit files under `docs/en/`. These are the authoritative source.
- **Translations**: Do **not** edit files in locale folders directly. They are auto-generated
  by [Crowdin](https://crowdin.com/project/meshtastic-android) and will be overwritten on sync.
  Contribute translations via Crowdin instead.
- **Adding a page**: Create the `.md` file in `docs/en/user/` or `docs/en/developer/`, then
  register it in `feature/docs/.../DocBundleLoader.kt` for in-app bundling.

## How Translations Work

1. English source files (`docs/en/user/*.md`) are uploaded to Crowdin as translation sources
2. Volunteers translate via the Crowdin web UI
3. Crowdin PRs land translated files at `docs/{android_code}/user/*.md` (e.g., `fr-rFR`, `pt-rBR`)
4. At build time, the Gradle `syncTranslatedDocsToComposeResources` task bundles them into
   locale-qualified Compose Resources for the in-app reader
5. The in-app `DocBundleLoader` tries the user's locale first, then falls back to English

## Publishing & Versioning

The GitHub Pages site is published to the persistent `gh-pages` branch as parallel
channels (GitHub Pages must be configured to serve from that branch):

| Path | Content | Published by |
|------|---------|--------------|
| `/` | Latest published release (default landing) | `docs-release.yml` on `vX.Y.Z` tags |
| `/vX.Y.Z/` | Permanent per-release copy | `docs-release.yml` on `vX.Y.Z` tags |
| `/main/` | Snapshot of the `main` branch | `docs-deploy.yml` on pushes to `main` |
| `/api/` | Dokka API reference | both workflows |
| `/versions.json` | Version manifest for the site's version switcher | regenerated on every deploy |

Each deploy overlays only its own channels via `scripts/docs/publish-to-gh-pages.sh`,
so release history accumulates instead of being wiped by the next deploy. The header
version dropdown (`_includes/version_switcher.html`) reads `/versions.json` at runtime;
a separate header link points to the upstream docs at meshtastic.org. To backfill a
release (e.g. after first enabling this), run the "Docs Release" workflow manually
against the release tag.
