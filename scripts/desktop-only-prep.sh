#!/usr/bin/env bash
# desktop-only-prep.sh — Prepare source tree for a desktop-only (JVM) build.
#
# Usage:
#   ./scripts/desktop-only-prep.sh
#   DESKTOP_ONLY=true ./gradlew :desktop:packageUberJarForCurrentOS
#
# This script comments out Android-specific blocks in module build scripts so
# Gradle can configure without the Android SDK. It is designed for Flatpak and
# other sandboxed Linux packaging environments.
#
# Prerequisites:
#   npm install -g @ast-grep/cli
#   OR
#   pipx install ast-grep-cli
#
# The companion in-code guards in build-logic convention plugins handle:
#   - Skipping Android/iOS plugin application (KmpLibraryConventionPlugin)
#   - Skipping iOS targets (configureKotlinMultiplatform)
#   - Creating a placeholder androidMain source set
#   - Excluding Android-only modules from settings.gradle.kts
#
# This script handles what can't be done in-code:
#   - `kotlin { android { ... } }` blocks in module build.gradle.kts files
#   - `androidMain.dependencies { ... }` blocks with project dependencies to
#     excluded modules (e.g., projects.core.barcode, projects.core.api)
#
# To reverse: `git checkout -- .` or rebuild from clean source.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT_DIR="$REPO_ROOT/scripts"
cd "$REPO_ROOT"

if ! command -v sg &>/dev/null; then
    echo "ERROR: ast-grep (sg) is required but not found in PATH." >&2
    echo "       Install: https://ast-grep.github.io/guide/quick-start.html" >&2
    exit 1
fi

echo "==> Preparing desktop-only build..."

# Collect target build.gradle.kts files (excluding modules that stay as-is)
mapfile -t FILES < <(find . -name "build.gradle.kts" \
    -not -path "./build-logic/*" \
    -not -path "./.agent_refs/*" \
    -not -path "./coil/*" \
    -not -path "./kable/*" \
    -not -path "./app/*" \
    -not -path "./core/api/*" \
    -not -path "./core/barcode/*" \
    -not -path "./feature/widget/*" \
    -not -path "./desktop/*" \
    -not -path "./build/*")

if [[ ${#FILES[@]} -eq 0 ]]; then
    echo "WARNING: No build.gradle.kts files found to process." >&2
    exit 0
fi

# Apply all ast-grep rules from the rules directory.
# Each rule YAML defines a pattern to match and a fix (comment replacement).
sg scan -c "$SCRIPT_DIR/sgconfig.yml" --update-all "${FILES[@]}"

echo "==> Desktop-only prep complete."
echo "    Run: DESKTOP_ONLY=true ./gradlew :desktop:packageUberJarForCurrentOS"
