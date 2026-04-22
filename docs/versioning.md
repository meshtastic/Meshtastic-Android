# Meshtastic-Android Versioning Standards

This document describes how `versionCode` and `versionName` are computed for all Android and
Desktop builds, and how the project integrates `release-please` for automated changelog and
version-name management.

---

## 1. `versionCode` — canonical source of truth

### Formula

```
versionCode = git rev-list --count HEAD + VERSION_CODE_OFFSET
```

`VERSION_CODE_OFFSET` is stored in `config.properties` and is a one-time fixed integer large
enough to ensure the resulting `versionCode` is always higher than any code published before
this monotonic scheme was adopted.

### Why commit-count + offset?

| Property | Rationale |
|---|---|
| **Monotonically increasing** | Every merged commit increments the count, satisfying Google Play's strict "must never decrease" requirement across all tracks. |
| **Deterministic** | Given a full-history clone and the offset constant, any machine reproduces the same value — no "who ran the CI" race. |
| **CI-friendly** | `lint-check` computes it once and passes it via `VERSION_CODE` env-var to downstream jobs that use `fetch-depth: 1`, preserving speed. |
| **Human-readable** | The value visually conveys how many commits exist in the repo, which is useful in support contexts. |

### Multi-track monotonicity

Google Play maintains a single `versionCode` pool across all release tracks (internal, alpha,
beta, production). The commit-count formula guarantees global monotonicity because:

- Internal builds always point to the most recent commit on `main`.
- Subsequent promotions (`internal → closed → open → production`) re-use the **same** build artifact
  and therefore the **same** `versionCode`.
- No build produced from a later commit can have a lower code than one produced from an earlier
  commit, regardless of track.

### ABI-split `versionCode`

The project builds per-ABI APKs for F-Droid/IzzyOnDroid. Each ABI gets a unique code via the
standard Android `splits.abi` mechanism, which uses `versionCodeOverride`:

| ABI | Multiplier |
|---|---|
| `armeabi-v7a` | base |
| `arm64-v8a` | base + 1 |
| `x86` | base + 2 |
| `x86_64` | base + 3 |

### Requirements for CI correctness

`git rev-list --count HEAD` requires a **full-history** clone. Wherever `versionCode` is
calculated (CI `lint-check` job, release workflow `prepare-build-info` job), the checkout
**must** use `fetch-depth: 0`. Shallow clones produce incorrect counts and are explicitly
rejected by `GitVersionValueSource`.

---

## 2. `versionName` — canonical source of truth

`versionName` follows **Semantic Versioning** (`MAJOR.MINOR.PATCH`).

| Context | Source |
|---|---|
| Local dev / debug builds | `VERSION_NAME_BASE` in `config.properties` |
| CI PR / push builds | `VERSION_NAME_BASE` in `config.properties` |
| Release builds | `VERSION_NAME` env-var injected by Fastlane / CI, derived from the Git tag (`v2.7.14` → `2.7.14`) |

The displayed version string for product flavors appends the versionCode and flavor name:

```
2.7.14 (29314800) fdroid
```

---

## 3. Updating `VERSION_NAME_BASE` — the release-please pilot

Starting with the 2.7.x series, the project runs `release-please` as a non-shipping assistant
that proposes version bumps and maintains `CHANGELOG.md` automatically.

### How it works

1. Every push to `main` triggers `.github/workflows/release-please.yml`.
2. `release-please` parses [Conventional Commit](https://www.conventionalcommits.org/) messages
   (`feat:`, `fix:`, `feat!:` / `BREAKING CHANGE`, etc.) and determines the next SemVer bump.
3. It opens (or updates) a **Release PR** that:
   - Bumps `version.txt` to the next version.
   - Updates the `# x-release-please-start-version` block in `config.properties`
     (keeping `VERSION_NAME_BASE` in sync).
   - Prepends the new section to `CHANGELOG.md`.
4. When the team is ready to ship, they **merge the Release PR** on GitHub.
5. `release-please` then creates a `v{version}` Git tag and a draft GitHub Release.
6. The team then runs the existing **`Create or Promote Release`** workflow using the new version
   as the `base_version` input to build and deploy to the channel pipeline.

> **Pilot mode:** During the pilot, `skip-github-release` is set to `false` so that a draft
> release is created as an anchor for release-please's version tracking. The existing
> channel-promotion pipeline is still the authoritative deployment path.

### Conventional Commit → SemVer mapping

| Commit prefix | SemVer bump |
|---|---|
| `fix:` | patch (`2.7.14 → 2.7.15`) |
| `feat:` | minor (`2.7.x → 2.8.0`) |
| `feat!:` / `BREAKING CHANGE:` | major (`2.x.y → 3.0.0`) |
| `chore:`, `docs:`, `refactor:`, `test:`, `build:` | no bump (not releasable units) |

### Forcing a specific version

Add `Release-As: x.y.z` to the **commit body** of any commit on `main` to override the
auto-computed version:

```
git commit --allow-empty -m "chore: release 3.0.0" -m "Release-As: 3.0.0"
```

### Config files

| File | Purpose |
|---|---|
| `.github/release-please-config.json` | Strategy, extra-files, bootstrap SHA |
| `.release-please-manifest.json` | Current version per package (updated on each release PR merge) |
| `version.txt` | Primary version file tracked by the `simple` release strategy |
| `CHANGELOG.md` | Auto-generated and maintained by release-please |

---

## 4. Decision record

| Decision | Rationale |
|---|---|
| **Commit-count + offset for `versionCode`**, not a manually bumped integer | Eliminates human error (forgetting to increment), makes codes deterministic across machines, and automatically handles hotfix branches. |
| **`simple` release-please strategy** instead of `java` / `maven` | This is a Kotlin/Gradle repo; the `simple` strategy's `version.txt` is the least invasive primary file, while `extra-files` keeps `config.properties` in sync. |
| **`skip-github-release: false`** during pilot | Needed so release-please can find its own tags on subsequent runs; the existing channel-promotion pipeline still owns deployment. |
| **Keep existing channel-promotion pipeline** | Google Play's internal→alpha→beta→production promotion model doesn't map onto release-please's single-track model; the existing bespoke pipeline handles this correctly. |
