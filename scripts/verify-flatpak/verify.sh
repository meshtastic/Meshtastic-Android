#!/usr/bin/env bash
# Local replica of vid's flatpak CI (vidplace7/org.meshtastic.desktop, .github/workflows/build-flatpak.yml)
# but flipped to true-offline mode: our flatpak-sources.json is included and --share=network is removed.
#
# Goal: validate flatpak-sources.json without bugging vid to push & re-run his workflow.
#
# Requirements:
#   - Docker (Docker Desktop on macOS is fine; needs ~10GB free + ability to run --privileged)
#   - This Meshtastic-Android checkout has produced flatpak-sources.json
#     (run `./gradlew :generateFlatpakSourcesFromCache` first, or this script will do it)
#
# Usage:
#   scripts/verify-flatpak/verify.sh                 # full build, x86_64
#   scripts/verify-flatpak/verify.sh --arch aarch64  # cross-arch via QEMU emulation
#   scripts/verify-flatpak/verify.sh --shell         # drop into the container shell instead of building

set -euo pipefail

ARCH="x86_64"
DROP_TO_SHELL=0
while [[ $# -gt 0 ]]; do
    case "$1" in
        --arch) ARCH="$2"; shift 2 ;;
        --shell) DROP_TO_SHELL=1; shift ;;
        -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
        *) echo "Unknown arg: $1" >&2; exit 2 ;;
    esac
done

# Map flatpak arch names to docker platform names
case "$ARCH" in
    x86_64)  DOCKER_PLATFORM="linux/amd64" ;;
    aarch64) DOCKER_PLATFORM="linux/arm64" ;;
    *) echo "Unsupported --arch: $ARCH (use x86_64 or aarch64)" >&2; exit 2 ;;
esac

REPO_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
WORK="$REPO_ROOT/build/flatpak-verify"
OVERLAY="$REPO_ROOT/scripts/verify-flatpak/desktop-offline.yaml"
SOURCES_JSON="$REPO_ROOT/flatpak-sources.json"
VID_REPO="https://github.com/vidplace7/org.meshtastic.desktop.git"

# Image provides flatpak + flatpak-builder. The freedesktop 25.08 runtime declared in
# the manifest is pulled from flathub at build time (no 25.08 image exists yet; 24.08 is
# fine as the builder host because the SDK used at compile time comes from flathub).
BUILDER_IMAGE="bilelmoussaoui/flatpak-github-actions:freedesktop-24.08"

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31m!! %s\033[0m\n' "$*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "docker is required; install Docker Desktop or equivalent."

step "Ensuring flatpak-sources.json is fresh"
if [[ ! -f "$SOURCES_JSON" ]]; then
    (cd "$REPO_ROOT" && ./gradlew :generateFlatpakSourcesFromCache)
fi

step "Preparing workspace at $WORK"
mkdir -p "$WORK"
if [[ ! -d "$WORK/org.meshtastic.desktop/.git" ]]; then
    git clone --depth 1 --recurse-submodules "$VID_REPO" "$WORK/org.meshtastic.desktop"
else
    git -C "$WORK/org.meshtastic.desktop" fetch --depth 1 origin main
    git -C "$WORK/org.meshtastic.desktop" reset --hard origin/main
    git -C "$WORK/org.meshtastic.desktop" submodule update --init --recursive --depth 1
fi

step "Wiring overlay manifest + our flatpak-sources.json"
cp "$OVERLAY" "$WORK/org.meshtastic.desktop/org.meshtastic.desktop.yaml"
cp "$SOURCES_JSON" "$WORK/org.meshtastic.desktop/flatpak-sources.json"

# Materialize a clean copy of our checkout (excluding build outputs) for `type: dir`.
# flatpak-builder copies the whole tree — skip heavy/irrelevant paths.
step "Snapshotting Meshtastic-Android checkout (excluding build/, .gradle/)"
rsync -a --delete \
    --exclude='/build/' \
    --exclude='/.gradle/' \
    --exclude='*/build/' \
    --exclude='*/.gradle/' \
    --exclude='/.idea/' \
    --exclude='/local.properties' \
    "$REPO_ROOT/" "$WORK/org.meshtastic.desktop/meshtastic-android/"

step "Pulling builder image: $BUILDER_IMAGE ($DOCKER_PLATFORM)"
docker pull --platform "$DOCKER_PLATFORM" "$BUILDER_IMAGE" >/dev/null

if [[ $DROP_TO_SHELL -eq 1 ]]; then
    step "Dropping into builder shell — flatpak-builder is on PATH"
    exec docker run --rm -it --privileged \
        -v "$WORK/org.meshtastic.desktop:/work" \
        -w /work \
        --platform "$DOCKER_PLATFORM" \
        "$BUILDER_IMAGE" bash
fi

step "Running flatpak-builder (arch=$ARCH)"
docker run --rm --privileged \
    -v "$WORK/org.meshtastic.desktop:/work" \
    -w /work \
    --platform "$DOCKER_PLATFORM" \
    "$BUILDER_IMAGE" \
    bash -c "set -e
        flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
        flatpak-builder --user --repo=repo --install-deps-from=flathub --force-clean \
            --disable-rofiles-fuse \
            builddir org.meshtastic.desktop.yaml
        echo
        echo '=== Offline build SUCCEEDED ==='
    "
