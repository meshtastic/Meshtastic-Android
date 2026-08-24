#!/usr/bin/env bash
# Reproducible-build verification for F-Droid/IzzyOnDroid.
# https://izzyondroid.org/docs/reproducibleBuilds/DebugFailedRBs/
# https://github.com/meshtastic/Meshtastic-Android/issues/3231
# Run from repo root with VERSION_CODE exported (CI: rb-check).
# No pipefail on purpose: `unzip -l | grep -q` SIGPIPEs unzip on first match.
set -eu

# Created BEFORE any Gradle run — fixed /tmp paths could be symlink-squatted by build logic.
WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

echo "── Step 1: Verify aboutlibraries.json determinism ──"
rm -f androidApp/src/main/resources/aboutlibraries.json
./gradlew :androidApp:exportLibraryDefinitions -Pci=true -Dorg.gradle.isolated-projects=false --no-configuration-cache
cp androidApp/src/main/resources/aboutlibraries.json "$WORKDIR/aboutlibraries-run1.json"

rm -f androidApp/src/main/resources/aboutlibraries.json
./gradlew :androidApp:exportLibraryDefinitions -Pci=true -Dorg.gradle.isolated-projects=false --no-configuration-cache --rerun-tasks
cp androidApp/src/main/resources/aboutlibraries.json "$WORKDIR/aboutlibraries-run2.json"

if ! diff -q "$WORKDIR/aboutlibraries-run1.json" "$WORKDIR/aboutlibraries-run2.json"; then
  echo "::error::aboutlibraries.json is NOT deterministic across runs!"
  diff "$WORKDIR/aboutlibraries-run1.json" "$WORKDIR/aboutlibraries-run2.json" | head -20
  exit 1
fi
echo "✅ aboutlibraries.json is deterministic"

echo "── Step 2: Build fdroid release APK ──"
./gradlew :androidApp:assembleFdroidRelease -Pci=true -Pmeshtastic.disableAbiSplits=true -Dorg.gradle.isolated-projects=false --no-configuration-cache

APK=$(find androidApp/build/outputs/apk/fdroid/release -name "*.apk" | head -1)
if [ -z "$APK" ]; then
  echo "::error::No fdroid release APK found"
  exit 1
fi
echo "Checking APK: $APK"

echo "── Step 3: Check for datadog.buildId ──"
if unzip -l "$APK" | grep -q "datadog.buildId"; then
  echo "::error::fdroid APK contains assets/datadog.buildId — breaks RB!"
  exit 1
fi
echo "✅ No datadog.buildId in fdroid APK"

echo "── Step 4: Check for proprietary libraries (dex scan) ──"
APK_DIR="$WORKDIR/apk"
mkdir "$APK_DIR"
unzip -q "$APK" -d "$APK_DIR"
OFFENDERS=""
for pattern in "com/google/firebase" "com/google/android/gms" "com/crashlytics" "com/google/mlkit" "com/google/android/datatransport" "androidx/privacysandbox/ads"; do
  for dex in "$APK_DIR"/classes*.dex; do
    if [ -f "$dex" ] && strings "$dex" | grep -q "L${pattern}/"; then
      OFFENDERS="${OFFENDERS}\n  - $pattern"
      break
    fi
  done
done

if [ -n "$OFFENDERS" ]; then
  echo -e "::error::fdroid APK contains proprietary libraries:${OFFENDERS}"
  exit 1
fi
echo "✅ No proprietary libraries in fdroid APK"

echo "── Step 5: Check for DEPENDENCY_INFO_BLOCK (signing block blob) ──"
# Parse the signing block properly; naive byte scans false-positive.
# Exit codes: 0 clean, 2 found, else parser crash.
rc=0
python3 - "$APK" <<'PYEOF' || rc=$?
import struct, sys

with open(sys.argv[1], "rb") as f:
    data = f.read()

magic = b"APK Sig Block 42"
idx = data.rfind(magic)
if idx < 0:
    sys.exit(0)

block_size = struct.unpack_from("<Q", data, idx - 8)[0]
block_start = idx + 16 - 8 - block_size
pos = int(block_start)
end = idx - 8

while pos + 12 <= end:
    pair_size = struct.unpack_from("<Q", data, pos)[0]
    pair_id = struct.unpack_from("<I", data, pos + 8)[0]
    if pair_id == 0x504b4453:
        print(f"DEPENDENCY_INFO_BLOCK found (id=0x{pair_id:08x})")
        sys.exit(2)
    pos += 8 + int(pair_size)

sys.exit(0)
PYEOF
if [ "$rc" -eq 2 ]; then
  echo "::error::fdroid APK contains DEPENDENCY_INFO_BLOCK — remove with dependenciesInfo { includeInApk = false }"
  exit 1
elif [ "$rc" -ne 0 ]; then
  echo "::error::DEPENDENCY_INFO_BLOCK check failed to parse the APK signing block (exit $rc)"
  exit 1
fi
echo "✅ No DEPENDENCY_INFO_BLOCK in signing block"

echo "── Step 6: Check native libraries have debug symbols (not stripped) ──"
STRIPPED_LIBS=""
while IFS= read -r -d '' so; do
  # no .symtab = stripped
  if ! readelf -S "$so" 2>/dev/null | grep -q "\.symtab"; then
    STRIPPED_LIBS="${STRIPPED_LIBS} $(basename "$so")"
  fi
done < <(find "$APK_DIR" -name "*.so" -print0 2>/dev/null)
# some third-party .so arrive pre-stripped — warn only
if [ -n "$STRIPPED_LIBS" ]; then
  echo "::warning::Some native libraries appear stripped (may cause NDK-version-dependent RB failures):${STRIPPED_LIBS}"
else
  echo "✅ Native libraries retain debug symbols"
fi

echo "── Step 7: Check aboutlibraries 'generated' timestamp not in APK ──"
# "generated" field = build-time timestamp
ABOUT_JSON=""
if [ -f "$APK_DIR/aboutlibraries.json" ]; then
  ABOUT_JSON="$APK_DIR/aboutlibraries.json"
else
  ABOUT_JSON=$(find "$APK_DIR/res" -name "*.json" -exec grep -l "aboutLibraries" {} \; 2>/dev/null | head -1)
fi
if [ -n "$ABOUT_JSON" ] && grep -q '"generated"' "$ABOUT_JSON"; then
  echo "::error::aboutlibraries contains 'generated' timestamp field — add excludeFields = listOf(\"generated\") to build config"
  exit 1
fi
echo "✅ No 'generated' timestamp in aboutlibraries data"

echo "── Step 8: Verify build from clean tree (version-control-info) ──"
if [ -f "$APK_DIR/META-INF/version-control-info.textproto" ]; then
  if grep -q "modified: true" "$APK_DIR/META-INF/version-control-info.textproto"; then
    echo "::warning::APK built from dirty tree (version-control-info shows modified:true). Release builds must use a clean tree."
  else
    echo "✅ Built from clean tree"
  fi
else
  echo "ℹ️ No version-control-info.textproto (AGP may not embed it for debug-signed builds)"
fi

echo ""
echo "🎉 All RB checks passed"
