# Obtainium app configurations

Distributable [Obtainium](https://github.com/ImranR98/Obtainium) configurations for
Meshtastic for Android, written to the format Obtainium and the crowdsourced
config site expect. Setup instructions and channel reference live in
[docs/en/developer/test-builds.md](../docs/en/developer/test-builds.md); this
directory holds the machine-readable artifacts.

| File | What it is | Generated? |
|---|---|---|
| `generate-links.py` | Source of truth for every channel/flavor config | — |
| `meshtastic-obtainium-export-google.json` | **Import/Export → Obtainium import** file: `google` release + both snapshots | ✅ |
| `meshtastic-obtainium-export-fdroid.json` | Same, with the `fdroid` release | ✅ |
| the deep-link table in [test-builds.md](../docs/en/developer/test-builds.md) | `obtainium://app/…` one-tap links, every channel × both flavors | ✅ |
| the deep-link table in [README.md](../README.md) | same links, narrowed to latest release + open beta × both flavors | ✅ |
| `com.geeksville.mesh.json` | Submission file for [apps.obtainium.imranr.dev](https://github.com/ImranR98/apps.obtainium.imranr.dev) | hand-maintained |

`generate-links.py` writes the three generated targets from one `CHANNELS` ×
`FLAVORS` definition, so the links and the import files cannot disagree:

```bash
python3 obtainium/generate-links.py          # regenerate
python3 obtainium/generate-links.py --check  # verify, exits 1 on drift
```

Run `--check` after editing anything in this directory. Editing a generated file
by hand is always wrong — change `generate-links.py` and regenerate.

### How many entries a user can have

Obtainium keys apps by application ID, and that governs the whole layout:

| | Application ID | Coexist? |
|---|---|---|
| Release builds — stable, open, closed, bleeding edge, either flavor | `com.geeksville.mesh` | **No**, one only |
| Snapshot `google` | `com.geeksville.mesh.google.debug` | Yes |
| Snapshot `fdroid` | `com.geeksville.mesh.fdroid.debug` | Yes |

So each import file carries exactly **one** release entry — hence one file per
flavor — plus **both** snapshots. Import is `saveApps()` keyed by `id`, so two
release entries in one file would silently overwrite rather than error. The beta
channels are deliberately left to the deep links for the same reason.

Only the snapshot labels carry a flavor suffix (`Meshtastic Snapshot (fdroid)`),
because only those can coexist and need telling apart. Labels are set via
`appName`, not `name`: `App.finalName` is `additionalSettings['appName'] ?? name`,
and without `appName` Obtainium falls back to the installed app's own label — a
debug build shows up as "Google Debug".

> `com.geeksville.mesh.json` is a **byte-identical mirror** of what we submitted
> upstream as [apps.obtainium.imranr.dev#1566](https://github.com/ImranR98/apps.obtainium.imranr.dev/pull/1566)
> (`public/data/apps/complex/com.geeksville.mesh.json`). Change it here and
> upstream together, or the config site and this repo will disagree. Their
> formatting conventions are load-bearing for review: 4-space indent, trailing
> newline, top-level key order `configs, icon, categories, description`, config
> key order `id, url, author, name, additionalSettings, altLabel` (`altLabel`
> last), and short lowercase `altLabel`s. Verify with `diff` against their tree
> rather than by eye. No formatter in this repo touches it — spotless only
> targets `*.kt` and `*.gradle.kts`.

## The format

An Obtainium app configuration is a flat JSON object. Four keys are required:

| Key | Value |
|---|---|
| `id` | Android package name — `com.geeksville.mesh` (debug snapshots: `com.geeksville.mesh.<flavor>.debug`) |
| `url` | `https://github.com/meshtastic/Meshtastic-Android` — this is what selects the GitHub source |
| `author` | `meshtastic` |
| `name` | Display name |

Everything else goes in `additionalSettings`, which is a **JSON string nested
inside the JSON** — its quotes and backslashes are escaped one extra level. Any
key you omit takes its default, so only set what you need. The GitHub source
accepts `includePrereleases`, `fallbackToOlderReleases`,
`filterReleaseTitlesByRegEx`, `filterReleaseNotesByRegEx`, `verifyLatestTag`,
`sortMethodChoice`, `useLatestAssetDateAsReleaseDate` and
`releaseTitleAsVersion`, on top of the source-agnostic keys (`apkFilterRegEx`,
`versionExtractionRegEx`, `versionDetection`, `autoApkFilterByArch`, `trackOnly`,
and so on).

Three distribution shapes, all carrying the same object:

- **Config site submission** — `{ "configs": [ …, … ], "icon", "categories", "description" }`.
  A `configs` array (with per-entry `altLabel`) is the "complex" form used when
  an app needs more than one variant; a single `config` object is the "simple"
  form. `categories` must come from
  [`public/data/categories.json`](https://github.com/ImranR98/apps.obtainium.imranr.dev/blob/main/public/data/categories.json)
  — `messaging` for us.
- **Import/export file** — `{ "apps": [ … ], "settings": null }`, one entry per
  application ID (see [above](#how-many-entries-a-user-can-have)). The legacy
  shape without `schemaVersion` is deliberate: Obtainium reads it through the
  `schemaVersion`-absent branch, which avoids inventing `exportedAt`/`appVersion`
  provenance for a file no Obtainium install actually produced.
- **Deep link** — `obtainium://app/<percent-encoded config JSON>` adds the app
  with settings baked in. `obtainium://add/<url>` only prefills the Add-App page.
  Wrapping it as `https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://app/…`
  keeps the link clickable in places that won't linkify a custom scheme.

## Why our settings are what they are

- **`apkFilterRegEx`** — each release attaches the google-flavor APK, several
  fdroid-flavor APKs, an `.aab`, and the desktop installers. Obtainium ignores
  non-APK assets on its own, but the flavors still need disambiguating: both
  share the `com.geeksville.mesh` package name, so the filter must select
  exactly one flavor. The fdroid filter deliberately matches *every* ABI split
  and lets `autoApkFilterByArch` choose, so a new ABI doesn't need a config change.
- **`autoApkFilterByArch: true` is set explicitly** even though it is the form
  default. `appJSONCompatibilityModifiers` coerces the key to `false` when it
  arrives unset, and an unset `preferredApkIndex` becomes `0` — together those
  would silently pick whichever split happens to be first.
- **No `versionExtractionRegEx`** — see
  [Version detection](../docs/en/developer/test-builds.md#version-detection-turns-itself-off--thats-expected).
  It only rewrites the version parsed from the release, not the installed
  `versionName` that actually fails to parse, so it cannot fix reconciliation —
  and it converts a quiet degradation into a hard `NoVersionError` whenever it
  fails to match.
- **Snapshot uses date-based pseudo-versioning** — the snapshot tag never moves
  off the literal string `snapshot`, so there is no version to compare;
  `releaseDateAsVersion` plus `useLatestAssetDateAsReleaseDate` tracks it by
  asset date instead. These keys are already in Obtainium's post-migration form,
  so `_migrateVersionDetectionFormat` passes them through untouched.
- **`fallbackToOlderReleases` is left at its default (`true`)** on the
  channel-pinned configs, deliberately. It reads like a strictness knob but
  isn't: `_selectGitHubTargetRelease` runs
  `if (!fallbackToOlderReleases && i > prereleaseSkipped) break;`, and
  `prereleaseSkipped` stays `0` when `includePrereleases` is on — so with
  fallback off, only the release at index 0 is ever considered and a title
  filter can never skip past it. Setting it to `false` made the snapshot and
  beta configs fail outright with `NoReleasesError`. The title filter alone
  pins the channel.

## Submitting to the config site

[apps.obtainium.imranr.dev](https://apps.obtainium.imranr.dev) is a
crowdsourced repo that takes pull requests. Meshtastic is not listed there yet.

1. Read [`APP_CRITERIA.md`](https://github.com/ImranR98/apps.obtainium.imranr.dev/blob/main/APP_CRITERIA.md).
   We qualify: official upstream source, no fork, no reupload mirror.
2. Copy `com.geeksville.mesh.json` to
   `public/data/apps/complex/com.geeksville.mesh.json` in a fork of that repo.
3. `npm i && npm run dev` (Node 22) and confirm the entry renders and its
   install link works.
4. Open the PR.

Only the stable configs belong there — the criteria ask for the minimum set of
variants and for defaults to be left alone, so the beta and snapshot channels
stay in this repo's own docs.
