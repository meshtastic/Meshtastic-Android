# Local Flatpak Verification

Replicates the official `flathub/org.meshtastic.MeshtasticDesktop` flatpak build on your machine
so you can validate `flatpak-sources.json` end-to-end without round-tripping through that repo's CI.

## What it tests

The release build (`:desktopApp:packageUberJarForCurrentOS :captureFlatpakSources`) only proves the manifest can be
*generated*. `flatpak-builder` *consumes* it, offline, in Flathub's CI and in `verify-flatpak.yml`, which runs nightly,
on pull requests that change this directory or the workflow, and on merges to `main` that change either one or the Gradle wrapper. This script runs the same step locally:

1. Clones `flathub/org.meshtastic.MeshtasticDesktop` (the official Flathub packaging repo).
2. Overlays a patched manifest that:
   - Swaps the `meshtastic/Meshtastic-Android.git` source for a `type: dir` pointing at
     **your local checkout** (so you can test uncommitted changes).
   - Uncomments `- flatpak-sources.json`.
   - Drops `--share=network` from the build-args (true offline — what Flathub requires).
   - Adds `--offline` to the Gradle invocation (belt + suspenders).
3. Runs `flatpak-builder` in the `bilelmoussaoui/flatpak-github-actions` Docker image that Flathub's CI
   uses, which installs the Freedesktop 25.08 runtime and SDK the manifest declares from Flathub.

If your `flatpak-sources.json` has the wrong URL, wrong sha256, or a missing entry,
the build fails with the same error Flathub CI would see. You can iterate in ~5–15 min loops
instead of waiting on cross-repo CI.

## Prerequisites

- Docker (Docker Desktop on macOS works — the container needs `--privileged` to use
  bubblewrap; that's enabled by default).
- ~15 GB free disk for the SDK, the Gradle cache and the build directory.
- Network access: unless `--skip-regen` is passed, the script regenerates `flatpak-sources.json` in a fresh,
  isolated Gradle home (`build/flatpak-gradle-home`), so every dependency is downloaded and captured.

## Usage

```shell
# Full offline build — Linux host required (~15–30 min first time)
scripts/verify-flatpak/verify.sh

# URLs + sha256 verification only; skips the Gradle build phase.
# Works on macOS where nested bwrap fails under Docker Desktop's seccomp.
scripts/verify-flatpak/verify.sh --download-only

# Reuse an already-generated flatpak-sources.json (don't re-run Gradle)
scripts/verify-flatpak/verify.sh --skip-regen

# Tight iteration loop after a failed run: refresh overlay yaml + manifest
# only, then re-run flatpak-builder. Skips Gradle regen, upstream-repo fetch,
# and Meshtastic-Android rsync. Use when you've just patched the YAML
# overlay or regenerated flatpak-sources.json by hand.
scripts/verify-flatpak/verify.sh --rebuild-only

# Cross-arch test via QEMU emulation (slower)
scripts/verify-flatpak/verify.sh --arch aarch64

# Drop into the builder container shell to poke at things
scripts/verify-flatpak/verify.sh --shell
```

### macOS limitation

`flatpak-builder` runs the build phase inside `bwrap` (bubblewrap). Nested
bwrap fails inside Docker Desktop on macOS with
`prctl(PR_SET_SECCOMP) EINVAL`. The script refuses to run a full build on
macOS by default — pass `--download-only` to validate URLs + sha256s without
executing the Gradle build, or run the full script on a Linux host.

## Interpreting failures

| Symptom                                                          | Likely cause                                                                                              |
| ---------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `Error downloading mirror: ... 404` on `repo.maven.apache.org`   | URL captured by the listener was wrong or artifact moved. Check the source repo hosting it.               |
| `sha256 mismatch`                                                | Stale `flatpak-sources.json`; re-run `:desktopApp:packageUberJarForCurrentOS :captureFlatpakSources`.                       |
| Gradle: `Could not resolve all artifacts ... offline mode`       | Missing dep in the manifest — usually a compiler plugin or BOM that wasn't downloaded during capture.     |

## Files

- `verify.sh` — entry point. Idempotent: re-running just re-syncs the overlay and re-runs flatpak-builder.
- `desktop-offline.yaml` — patched manifest. Kept in sync manually with the upstream packaging;
  diff against `https://raw.githubusercontent.com/flathub/org.meshtastic.MeshtasticDesktop/master/org.meshtastic.MeshtasticDesktop.yaml`
  if upstream changes something material. Its Gradle zip is a placeholder; `verify.sh` and
  `verify-flatpak.yml` fill it from `gradle/wrapper/gradle-wrapper.properties`.
- `check-platform-deps.py` - checks that the root `build.gradle.kts` platform-dependency list still declares
  every per-architecture runtime `desktopApp` resolves, so the arm64 offline build cannot miss one. `pull-request.yml`
  runs it on pull requests.
- `bump-flathub-manifest.py` - points the upstream manifest at a release: the source tag and
  commit, and the Gradle zip the tag's wrapper pins. `promote.yml`'s `update-flathub` job runs it
  on every production promotion, then commits the release's `flatpak-sources.json` beside it.
  `--gradle-only` rewrites just the Gradle zip, for the overlay.
