#!/usr/bin/env bash
# Local replica of vid's flatpak CI (vidplace7/org.meshtastic.MeshtasticDesktop,
# .github/workflows/build-flatpak.yml) but flipped to true-offline mode: our
# flatpak-sources.json is included and --share=network is removed from the
# build phase.
#
# Goal: validate flatpak-sources.json end-to-end (download + verify sha256s +
# offline Gradle build) without bugging vid to push & re-run his workflow.
#
# Requirements:
#   - Docker (Docker Desktop on macOS works for --download-only mode; full builds
#     need a Linux host because flatpak-builder uses nested bwrap which fails
#     under Docker Desktop's seccomp sandbox).
#   - ~15GB free disk for the SDK + Gradle cache + builddir.
#
# Usage:
#   scripts/verify-flatpak/verify.sh                   # full offline build (Linux only)
#   scripts/verify-flatpak/verify.sh --download-only   # URLs+sha256 only (works on macOS)
#   scripts/verify-flatpak/verify.sh --arch aarch64    # cross-arch via QEMU emulation
#   scripts/verify-flatpak/verify.sh --shell           # drop into builder container shell
#   scripts/verify-flatpak/verify.sh --skip-regen      # reuse flatpak-sources.json; still re-clone vid + re-rsync source
#   scripts/verify-flatpak/verify.sh --rebuild-only    # tight loop: refresh overlay+manifest only, then re-run flatpak-builder
#
# Iteration tip: after a build fails partway, fix the overlay YAML (or the
# Meshtastic-Android source) and re-run with --rebuild-only — Gradle regen,
# vid-repo fetch, and full source rsync are all skipped, so you get straight
# back to flatpak-builder in seconds.

set -euo pipefail

ARCH="x86_64"
DROP_TO_SHELL=0
DOWNLOAD_ONLY=0
SKIP_REGEN=0
REBUILD_ONLY=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --arch) ARCH="$2"; shift 2 ;;
        --shell) DROP_TO_SHELL=1; shift ;;
        --download-only) DOWNLOAD_ONLY=1; shift ;;
        --skip-regen) SKIP_REGEN=1; shift ;;
        --rebuild-only) REBUILD_ONLY=1; SKIP_REGEN=1; shift ;;
        -h|--help) sed -n '2,28p' "$0"; exit 0 ;;
        *) echo "Unknown arg: $1" >&2; exit 2 ;;
    esac
done

case "$ARCH" in
    x86_64)  DOCKER_PLATFORM="linux/amd64" ;;
    aarch64) DOCKER_PLATFORM="linux/arm64" ;;
    *) echo "Unsupported --arch: $ARCH (use x86_64 or aarch64)" >&2; exit 2 ;;
esac

REPO_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
WORK="$REPO_ROOT/build/flatpak-verify"
OVERLAY="$REPO_ROOT/scripts/verify-flatpak/desktop-offline.yaml"
SOURCES_JSON="$REPO_ROOT/flatpak-sources.json"
GRADLE_HOME_ISOLATED="$REPO_ROOT/build/flatpak-gradle-home"
VID_REPO="https://github.com/vidplace7/org.meshtastic.MeshtasticDesktop.git"

# bilelmoussaoui's image is what vid's CI uses; freedesktop-24.08 is the latest
# tag available. The 25.08 runtime declared in the manifest is pulled from
# flathub at build time inside the container.
BUILDER_IMAGE="bilelmoussaoui/flatpak-github-actions:freedesktop-24.08"

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31m!! %s\033[0m\n' "$*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "docker is required; install Docker Desktop or equivalent."

# Refuse full-build mode on macOS — nested bwrap fails under Docker Desktop's
# seccomp and the user will spend 20 minutes finding out. They can override
# with --download-only.
if [[ "$(uname -s)" == "Darwin" && $DOWNLOAD_ONLY -eq 0 && $DROP_TO_SHELL -eq 0 ]]; then
    fail "Full flatpak-builder runs require a Linux host (nested bwrap fails under Docker Desktop on macOS). Re-run with --download-only, or use --shell to poke around manually."
fi

if [[ $SKIP_REGEN -eq 0 ]]; then
    step "Regenerating flatpak-sources.json via isolated Gradle home"
    rm -rf "$GRADLE_HOME_ISOLATED"
    # The settings plugin (org.meshtastic.flatpak.sources.settings) captures URLs from
    # build start — no init script or -I flag needed.
    (cd "$REPO_ROOT" && ./gradlew --no-build-cache --no-configuration-cache \
        -Dgradle.user.home="$GRADLE_HOME_ISOLATED" \
        :desktopApp:packageUberJarForCurrentOS :captureFlatpakSources)
    cp "$REPO_ROOT/build/flatpak-ops-sources.json" "$SOURCES_JSON"
elif [[ ! -f "$SOURCES_JSON" ]]; then
    fail "--skip-regen specified but $SOURCES_JSON does not exist."
fi

if [[ $REBUILD_ONLY -eq 1 ]]; then
    [[ -d "$WORK/org.meshtastic.MeshtasticDesktop/.git" ]] || \
        fail "--rebuild-only needs an existing workspace at $WORK; run without it once first."
else
    step "Preparing workspace at $WORK"
    mkdir -p "$WORK"
    if [[ ! -d "$WORK/org.meshtastic.MeshtasticDesktop/.git" ]]; then
        git clone --depth 1 --recurse-submodules "$VID_REPO" "$WORK/org.meshtastic.MeshtasticDesktop"
    else
        git -C "$WORK/org.meshtastic.MeshtasticDesktop" fetch --depth 1 origin main
        git -C "$WORK/org.meshtastic.MeshtasticDesktop" reset --hard origin/main
        git -C "$WORK/org.meshtastic.MeshtasticDesktop" submodule update --init --recursive --depth 1
    fi
fi

# Always refreshed — these are the iteration knobs:
#   overlay yaml = the manifest we're testing
#   flatpak-sources.json = the artifact we're validating
step "Wiring overlay manifest + our flatpak-sources.json"
cp "$OVERLAY" "$WORK/org.meshtastic.MeshtasticDesktop/org.meshtastic.MeshtasticDesktop.yaml"
cp "$SOURCES_JSON" "$WORK/org.meshtastic.MeshtasticDesktop/flatpak-sources.json"

if [[ $REBUILD_ONLY -eq 0 ]]; then
    step "Snapshotting Meshtastic-Android checkout (excluding build/, .gradle/)"
    rsync -a --delete \
        --exclude='/build/' \
        --exclude='/.gradle/' \
        --exclude='*/build/' \
        --exclude='*/.gradle/' \
        --exclude='/.idea/' \
        --exclude='/local.properties' \
        "$REPO_ROOT/" "$WORK/org.meshtastic.MeshtasticDesktop/meshtastic-android/"
fi

step "Pulling builder image: $BUILDER_IMAGE ($DOCKER_PLATFORM)"
docker pull --platform "$DOCKER_PLATFORM" "$BUILDER_IMAGE" >/dev/null

DOCKER_RUN_ARGS=(
    --rm
    --privileged
    -v "$WORK/org.meshtastic.MeshtasticDesktop:/work"
    -w /work
    --platform "$DOCKER_PLATFORM"
    --security-opt seccomp=unconfined
)

if [[ $DROP_TO_SHELL -eq 1 ]]; then
    step "Dropping into builder shell — flatpak-builder is on PATH"
    exec docker run -it "${DOCKER_RUN_ARGS[@]}" "$BUILDER_IMAGE" bash
fi

# Build flatpak-builder invocation. --download-only mode skips the bwrap-based
# build phase, which is the part that fails under Docker Desktop on macOS.
if [[ $DOWNLOAD_ONLY -eq 1 ]]; then
    BUILDER_EXTRA_FLAGS="--download-only"
    SUCCESS_MSG="All sources downloaded and sha256-verified successfully (URLs + hashes OK; Gradle build NOT exercised)"
else
    BUILDER_EXTRA_FLAGS=""
    SUCCESS_MSG="Full offline build succeeded — flatpak-sources.json is complete and self-sufficient"
fi

step "Running flatpak-builder (arch=$ARCH, mode=$([[ $DOWNLOAD_ONLY -eq 1 ]] && echo download-only || echo full-build))"
docker run "${DOCKER_RUN_ARGS[@]}" "$BUILDER_IMAGE" bash -c "set -e
    flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
    flatpak-builder --user --repo=repo --install-deps-from=flathub --force-clean \
        --disable-rofiles-fuse $BUILDER_EXTRA_FLAGS \
        builddir org.meshtastic.MeshtasticDesktop.yaml
    echo
    echo '=== $SUCCESS_MSG ==='
"
