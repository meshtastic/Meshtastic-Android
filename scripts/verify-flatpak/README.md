# Local Flatpak Verification

Replicates vid's `org.meshtastic.MeshtasticDesktop` GHA flatpak build on your machine so you
can validate `flatpak-sources.json` end-to-end without round-tripping through his repo.

## What it tests that our CI doesn't

Our CI (`:desktopApp:packageUberJarForCurrentOS :captureFlatpakSources`) only proves the manifest can be *generated*.
Vid's CI is where the manifest actually gets *consumed* by `flatpak-builder`. This script
runs that step locally:

1. Clones `vidplace7/org.meshtastic.MeshtasticDesktop`.
2. Overlays a patched manifest that:
   - Swaps the `meshtastic/Meshtastic-Android.git` source for a `type: dir` pointing at
     **your local checkout** (so you can test uncommitted changes).
   - Uncomments `- flatpak-sources.json`.
   - Drops `--share=network` from the build-args (true offline — what Flathub requires).
   - Adds `--offline` to the Gradle invocation (belt + suspenders).
3. Runs `flatpak-builder` in a Docker container with the same Freedesktop 25.08 SDK
   vid's GHA image uses.

If your `flatpak-sources.json` has the wrong URL, wrong sha256, or a missing entry,
the build fails with the same error vid would see. You can iterate in ~5–15 min loops
instead of waiting on cross-repo CI.

## Prerequisites

- Docker (Docker Desktop on macOS works — the container needs `--privileged` to use
  bubblewrap; that's enabled by default).
- ~10 GB free disk for the SDK + Gradle cache.
- A populated Gradle cache (`./gradlew :desktopApp:packageUberJarForCurrentOS` must have
  run; the script does this implicitly via `:captureFlatpakSources`).

## Usage

```bash
# Full offline build — Linux host required (~15–30 min first time)
scripts/verify-flatpak/verify.sh

# URLs + sha256 verification only; skips the Gradle build phase.
# Works on macOS where nested bwrap fails under Docker Desktop's seccomp.
scripts/verify-flatpak/verify.sh --download-only

# Reuse an already-generated flatpak-sources.json (don't re-run Gradle)
scripts/verify-flatpak/verify.sh --skip-regen

# Tight iteration loop after a failed run: refresh overlay yaml + manifest
# only, then re-run flatpak-builder. Skips Gradle regen, vid-repo fetch,
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
- `desktop-offline.yaml` — patched manifest. Kept in sync manually with vid's upstream;
  diff against `https://raw.githubusercontent.com/vidplace7/org.meshtastic.MeshtasticDesktop/main/org.meshtastic.MeshtasticDesktop.yaml`
  if vid changes something material.
