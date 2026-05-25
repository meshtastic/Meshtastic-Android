# Local Flatpak Verification

Replicates vid's `org.meshtastic.desktop` GHA flatpak build on your machine so you
can validate `flatpak-sources.json` end-to-end without round-tripping through his repo.

## What it tests that our CI doesn't

Our CI (`generateFlatpakSourcesFromCache`) only proves the manifest can be *generated*.
Vid's CI is where the manifest actually gets *consumed* by `flatpak-builder`. This script
runs that step locally:

1. Clones `vidplace7/org.meshtastic.desktop`.
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
- A populated Gradle cache (`./gradlew :desktopApp:assemble` must have run; the script
  does this implicitly via `:generateFlatpakSourcesFromCache`).

## Usage

```bash
# Full offline build (~10–20 min the first time, faster after — Docker image is cached)
scripts/verify-flatpak/verify.sh

# Cross-arch test via QEMU emulation (slower)
scripts/verify-flatpak/verify.sh --arch aarch64

# Drop into the builder container shell to poke at things
scripts/verify-flatpak/verify.sh --shell
```

## Interpreting failures

| Symptom                                                          | Likely cause                                                                                              |
| ---------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `Error downloading mirror: ... 404` on `repo.maven.apache.org`   | An artifact is classified as Maven Central but actually lives elsewhere (Google, JitPack). Fix repo spec. |
| `sha256 mismatch`                                                | Stale `flatpak-sources.json`; re-run `:generateFlatpakSourcesFromCache`.                                  |
| `No repository spec matched <group>:<name>`                      | New group not covered by the default `MavenRepoSpec` table; add to `FlatpakPlugin.DEFAULT_REPOSITORIES`.  |
| Gradle: `Could not resolve all artifacts ... offline mode`       | Missing dep in the manifest — usually a compiler plugin or BOM that the cache walk skipped.               |

## Files

- `verify.sh` — entry point. Idempotent: re-running just re-syncs the overlay and re-runs flatpak-builder.
- `desktop-offline.yaml` — patched manifest. Kept in sync manually with vid's upstream;
  diff against `https://raw.githubusercontent.com/vidplace7/org.meshtastic.desktop/main/org.meshtastic.desktop.yaml`
  if vid changes something material.
