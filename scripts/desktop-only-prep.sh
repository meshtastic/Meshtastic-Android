#!/usr/bin/env bash
# desktop-only-prep.sh — Prepare source tree for a desktop-only (JVM) build.
#
# Usage:
#   DESKTOP_ONLY=true ./scripts/desktop-only-prep.sh
#   ./gradlew :desktop:packageUberJarForCurrentOS
#
# This script comments out Android-specific blocks in module build scripts so
# Gradle can configure without the Android SDK. It is designed for Flatpak and
# other sandboxed Linux packaging environments.
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
cd "$REPO_ROOT"

echo "==> Preparing desktop-only build..."

# --- 1. Comment out `android { ... }` blocks inside `kotlin { }` in module build scripts ---
# Uses AWK to track brace depth and comment out android blocks.
find . -name "build.gradle.kts" \
    -not -path "./build-logic/*" \
    -not -path "./.agent_refs/*" \
    -not -path "./coil/*" \
    -not -path "./kable/*" \
    -not -path "./app/*" \
    -not -path "./core/api/*" \
    -not -path "./core/barcode/*" \
    -not -path "./feature/widget/*" \
    -not -path "./desktop/*" \
    -not -path "./build/*" | while read -r file; do

    # Comment out `android { ... }` blocks (handles nested braces)
    awk '
    /^[[:space:]]*android[[:space:]]*\{/ && !in_android {
        in_android = 1
        depth = 0
        for (i = 1; i <= length($0); i++) {
            c = substr($0, i, 1)
            if (c == "{") depth++
            if (c == "}") depth--
        }
        print "// [desktop-only] " $0
        if (depth <= 0) in_android = 0
        next
    }
    in_android {
        for (i = 1; i <= length($0); i++) {
            c = substr($0, i, 1)
            if (c == "{") depth++
            if (c == "}") depth--
        }
        print "// [desktop-only] " $0
        if (depth <= 0) in_android = 0
        next
    }
    { print }
    ' "$file" > "$file.tmp" && mv "$file.tmp" "$file"

    # Comment out `androidMain.dependencies { ... }` blocks
    awk '
    /^[[:space:]]*androidMain\.dependencies[[:space:]]*\{/ && !in_block {
        in_block = 1
        depth = 0
        for (i = 1; i <= length($0); i++) {
            c = substr($0, i, 1)
            if (c == "{") depth++
            if (c == "}") depth--
        }
        print "// [desktop-only] " $0
        if (depth <= 0) in_block = 0
        next
    }
    in_block {
        for (i = 1; i <= length($0); i++) {
            c = substr($0, i, 1)
            if (c == "{") depth++
            if (c == "}") depth--
        }
        print "// [desktop-only] " $0
        if (depth <= 0) in_block = 0
        next
    }
    { print }
    ' "$file" > "$file.tmp" && mv "$file.tmp" "$file"

done

# --- 2. Comment out androidHostTest and androidDeviceTest blocks ---
find . -name "build.gradle.kts" \
    -not -path "./build-logic/*" \
    -not -path "./.agent_refs/*" \
    -not -path "./coil/*" \
    -not -path "./kable/*" \
    -not -path "./app/*" \
    -not -path "./core/api/*" \
    -not -path "./core/barcode/*" \
    -not -path "./feature/widget/*" \
    -not -path "./desktop/*" \
    -not -path "./build/*" | while read -r file; do

    awk '
    /^[[:space:]]*(val androidHostTest|val androidDeviceTest|val androidInstrumentedTest)/ && !in_block {
        in_block = 1
        depth = 0
        for (i = 1; i <= length($0); i++) {
            c = substr($0, i, 1)
            if (c == "{") depth++
            if (c == "}") depth--
        }
        print "// [desktop-only] " $0
        if (depth <= 0) in_block = 0
        next
    }
    in_block {
        for (i = 1; i <= length($0); i++) {
            c = substr($0, i, 1)
            if (c == "{") depth++
            if (c == "}") depth--
        }
        print "// [desktop-only] " $0
        if (depth <= 0) in_block = 0
        next
    }
    { print }
    ' "$file" > "$file.tmp" && mv "$file.tmp" "$file"

done

# --- 3. Comment out imports of Android types in build scripts ---
find . -name "build.gradle.kts" \
    -not -path "./build.gradle.kts" \
    -not -path "./build-logic/*" \
    -not -path "./.agent_refs/*" \
    -not -path "./coil/*" \
    -not -path "./kable/*" \
    -not -path "./app/*" \
    -not -path "./core/api/*" \
    -not -path "./core/barcode/*" \
    -not -path "./feature/widget/*" \
    -not -path "./desktop/*" \
    -not -path "./build/*" | while read -r file; do

    sed -i 's/^import com\.android\./\/\/ [desktop-only] import com.android./' "$file"
    # Comment out Parcelize plugin (requires Android components extension)
    sed -i 's/^\([[:space:]]*\)alias(libs\.plugins\.kotlin\.parcelize)/\1\/\/ [desktop-only] alias(libs.plugins.kotlin.parcelize)/' "$file"
    # Comment out kspAndroid* configuration references
    sed -i 's/^\([[:space:]]*\)"kspAndroid/\1\/\/ [desktop-only] "kspAndroid/' "$file"

done

echo "==> Desktop-only prep complete."
echo "    Run: DESKTOP_ONLY=true ./gradlew :desktop:packageUberJarForCurrentOS"
