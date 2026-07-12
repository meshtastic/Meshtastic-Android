#!/usr/bin/env bash
# Wraps the jpackage app-image produced by :desktopApp:packageReleaseAppImage
# into a real Linux .AppImage.
#
# Compose Multiplatform's TargetFormat.AppImage is jpackage's "app-image": an
# unpacked application directory (bin/<App>, lib/...), NOT the single-file
# AppImage bundle format. This script builds the AppDir scaffolding (AppRun,
# .desktop entry, icon) around that directory and packs it with appimagetool.
#
# The output lands in main-release/appimage/ so the existing release upload
# and attestation globs (main-release/*/*.AppImage) pick it up unchanged.
#
# Expects to run on a Linux host of the target architecture (x86_64/aarch64)
# with APP_VERSION_NAME set (e.g. 2.8.0). appimagetool and the AppImage
# runtime are downloaded pinned by version AND sha256 — bump the four hashes
# together with the version pins below.
set -euo pipefail

VERSION="${APP_VERSION_NAME:?APP_VERSION_NAME must be set (e.g. 2.8.0)}"
BINARIES_DIR="${BINARIES_DIR:-desktopApp/build/compose/binaries/main-release}"

APPIMAGETOOL_VERSION="1.9.1"
RUNTIME_VERSION="20251108"

ARCH="$(uname -m)"
case "$ARCH" in
  x86_64)
    APPIMAGETOOL_SHA256="ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0"
    RUNTIME_SHA256="2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d"
    ;;
  aarch64)
    APPIMAGETOOL_SHA256="f0837e7448a0c1e4e650a93bb3e85802546e60654ef287576f46c71c126a9158"
    RUNTIME_SHA256="00cbdfcf917cc6c0ff6d3347d59e0ca1f7f45a6df1a428a0d6d8a78664d87444"
    ;;
  *)
    echo "::error::Unsupported architecture for AppImage: ${ARCH}" >&2
    exit 1
    ;;
esac

# --- Locate the jpackage app-image (directory name = CMP packageName; may contain spaces) ---
app_dir=""
count=0
for d in "${BINARIES_DIR}/app"/*/; do
  [ -d "$d" ] || continue
  app_dir="${d%/}"
  count=$((count + 1))
done
if [ "$count" -ne 1 ]; then
  echo "::error::Expected exactly one app-image directory under ${BINARIES_DIR}/app, found ${count}" >&2
  exit 1
fi
app_name="$(basename "$app_dir")"

launcher="${app_dir}/bin/${app_name}"
if [ ! -x "$launcher" ]; then
  echo "::error::jpackage launcher not found or not executable: ${launcher}" >&2
  exit 1
fi

icon="${app_dir}/lib/${app_name}.png"
if [ ! -f "$icon" ]; then
  # jpackage normally drops the icon at lib/<AppName>.png; fall back to the source icon.
  icon="desktopApp/src/main/resources/icon.png"
fi
if [ ! -f "$icon" ]; then
  echo "::error::No icon found for the AppImage (looked in lib/ and desktopApp resources)" >&2
  exit 1
fi

# Desktop-entry id / output-name slug: "Meshtastic Desktop" -> meshtastic-desktop
slug="$(printf '%s' "$app_name" | tr '[:upper:]' '[:lower:]' | tr ' ' '-')"

# --- Assemble the AppDir ---
out_dir="${BINARIES_DIR}/appimage"
appdir="${out_dir}/${slug}.AppDir"
rm -rf "$appdir"
mkdir -p "$appdir"
cp -a "${app_dir}/." "$appdir/"

cat > "${appdir}/AppRun" <<EOF
#!/bin/sh
HERE="\$(dirname "\$(readlink -f "\$0")")"
exec "\$HERE/bin/${app_name}" "\$@"
EOF
chmod +x "${appdir}/AppRun"

cp "$icon" "${appdir}/${slug}.png"

cat > "${appdir}/${slug}.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=${app_name}
Exec=AppRun
Icon=${slug}
Comment=Meshtastic Desktop Application
Categories=Network;
Terminal=false
EOF

# --- Fetch pinned appimagetool + runtime and pack ---
tools_dir="$(mktemp -d)"
trap 'rm -rf "$tools_dir"' EXIT

curl -fsSL --retry 5 --retry-delay 10 --connect-timeout 10 --max-time 300 -o "${tools_dir}/appimagetool" \
  "https://github.com/AppImage/appimagetool/releases/download/${APPIMAGETOOL_VERSION}/appimagetool-${ARCH}.AppImage"
echo "${APPIMAGETOOL_SHA256}  ${tools_dir}/appimagetool" | sha256sum -c -
chmod +x "${tools_dir}/appimagetool"

curl -fsSL --retry 5 --retry-delay 10 --connect-timeout 10 --max-time 300 -o "${tools_dir}/runtime" \
  "https://github.com/AppImage/type2-runtime/releases/download/${RUNTIME_VERSION}/runtime-${ARCH}"
echo "${RUNTIME_SHA256}  ${tools_dir}/runtime" | sha256sum -c -

output="${out_dir}/${app_name// /_}-${VERSION}-${ARCH}.AppImage"
# APPIMAGE_EXTRACT_AND_RUN: run appimagetool itself without FUSE.
# --runtime-file: embed the pinned runtime instead of downloading `continuous`.
# --no-appstream: we ship no AppStream metadata, skip that validation.
ARCH="$ARCH" APPIMAGE_EXTRACT_AND_RUN=1 "${tools_dir}/appimagetool" \
  --no-appstream --runtime-file "${tools_dir}/runtime" \
  "$appdir" "$output"

# Drop the AppDir so only the .AppImage remains for upload/attestation globs.
rm -rf "$appdir"

echo "Built $(du -h "$output" | cut -f1) AppImage: ${output}"
