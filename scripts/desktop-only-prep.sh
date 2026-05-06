#!/usr/bin/env bash
# desktop-only-prep.sh — Prepare source tree for a desktop-only (JVM) build.
#
# Usage:
#   ./scripts/desktop-only-prep.sh
#   DESKTOP_ONLY=true ./gradlew :desktop:packageUberJarForCurrentOS
#
# This script removes Android-specific blocks in module build scripts so
# Gradle can configure without the Android SDK. It uses ast-grep with YAML
# rules for AST-aware pattern matching (no brittle regex/awk on nested braces).
#
# Prerequisites:
#   npm install -g @ast-grep/cli
#
# The companion in-code guards in build-logic convention plugins handle:
#   - Skipping Android/iOS plugin application (KmpLibraryConventionPlugin)
#   - Skipping iOS targets (configureKotlinMultiplatform)
#   - Creating a placeholder androidMain source set
#   - Excluding Android-only modules from settings.gradle.kts
#
# This script handles what can't be done in-code:
#   - `kotlin { android { ... } }` blocks in module build.gradle.kts files
#   - `androidMain.dependencies { ... }` blocks
#   - Parcelize plugin alias
#   - kspAndroid* configuration references
#   - Android source set val declarations
#
# To reverse: `git checkout -- .` or rebuild from clean source.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

RULES_DIR="$REPO_ROOT/scripts/ast-grep-rules"

# Verify ast-grep is available
if ! command -v ast-grep &>/dev/null; then
    echo "ERROR: ast-grep not found. Install with: npm install -g @ast-grep/cli"
    exit 1
fi

echo "==> Preparing desktop-only build (ast-grep)..."

# Collect target build files (exclude dirs that are Android-only or vendored)
mapfile -t BUILD_FILES < <(
    find . -name "build.gradle.kts" \
        -not -path "./.gradle/*" \
        -not -path "./build/*" \
        -not -path "./build-logic/*" \
        -not -path "./.agent_refs/*" \
        -not -path "./.agent_memory/*" \
        -not -path "./.agent_plans/*" \
        -not -path "./kable/*" \
        -not -path "./coil/*" \
        -not -path "./app/*" \
        -not -path "./core/api/*" \
        -not -path "./core/barcode/*" \
        -not -path "./feature/widget/*" \
        -not -path "./desktop/*" \
        | grep -v "^\./build\.gradle\.kts$" \
        | sort
)

if [ ${#BUILD_FILES[@]} -eq 0 ]; then
    echo "WARNING: No build.gradle.kts files found to process."
    exit 0
fi

echo "  Processing ${#BUILD_FILES[@]} build files..."

# Apply each rule file
for rule_file in "$RULES_DIR"/*.yml; do
    rule_name="$(basename "$rule_file" .yml)"
    echo "  Applying rule: $rule_name"
    ast-grep scan \
        --rule "$rule_file" \
        --update-all \
        "${BUILD_FILES[@]}" 2>/dev/null || true
done

# Remove Android imports (simple line-based, no AST needed)
echo "  Removing Android imports..."
for file in "${BUILD_FILES[@]}"; do
    sed -i '/^import com\.android\./d' "$file" 2>/dev/null || true
done

echo "==> Desktop-only prep complete."
echo "    Run: DESKTOP_ONLY=true ./gradlew :desktop:packageUberJarForCurrentOS"
