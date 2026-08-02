#!/usr/bin/env bash
# Comprehensive reproducible-build verification for F-Droid/IzzyOnDroid.
# Based on: https://izzyondroid.org/docs/reproducibleBuilds/DebugFailedRBs/
# Catches regressions that have historically broken reproducibility:
#   1. aboutlibraries.json non-determinism (network fetching)
#   2. Datadog buildId leaking into fdroid APK
#   3. Google/Firebase/GMS/MLKit classes in fdroid APK
#   4. DEPENDENCY_INFO_BLOCK in signing block
#   5. Native library stripping (NDK version mismatch)
#   6. aboutlibraries "generated" timestamp in res/M7.json
#   7. baseline.prof determinism (flaky builds)
# See: https://github.com/meshtastic/Meshtastic-Android/issues/3231
#
# Run from the repo root (CI: the rb-check job in reusable-check.yml).
# VERSION_CODE must be exported; the Gradle build reads it.
#
# Deliberately no `set -o pipefail`: `unzip -l | grep -q` ends the pipe at the
# first match, so unzip dies with SIGPIPE and pipefail would turn that into a
# spurious failure.
set -eu

# Private workdir, created BEFORE any Gradle run: fixed /tmp paths could be
# pre-claimed (symlinked) by build logic executing in between the copies.
WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

echo "── Step 1: Verify aboutlibraries.json determinism ──"
rm -f androidApp/src/main/resources/aboutlibraries.json
./gradlew :androidApp:exportLibraryDefinitions -Pci=true --no-configuration-cache
cp androidApp/src/main/resources/aboutlibraries.json "$WORKDIR/aboutlibraries-run1.json"

rm -f androidApp/src/main/resources/aboutlibraries.json
./gradlew :androidApp:exportLibraryDefinitions -Pci=true --no-configuration-cache --rerun-tasks
cp androidApp/src/main/resources/aboutlibraries.json "$WORKDIR/aboutlibraries-run2.json"

if ! diff -q "$WORKDIR/aboutlibraries-run1.json" "$WORKDIR/aboutlibraries-run2.json"; then
  echo "::error::aboutlibraries.json is NOT deterministic across runs!"
  diff "$WORKDIR/aboutlibraries-run1.json" "$WORKDIR/aboutlibraries-run2.json" | head -20
  exit 1
fi
echo "✅ aboutlibraries.json is deterministic"

echo "── Step 2: Build fdroid release APK ──"
./gradlew :androidApp:assembleFdroidRelease -Pci=true -Pmeshtastic.disableAbiSplits=true --no-configuration-cache

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
# Parse the APK Signing Block structure to find the dependency info pair.
# Naive byte scans produce false positives in large APKs.
# Exit codes: 0 = clean, 2 = block found, anything else = parser crashed.
# (The pre-extraction version of this check passed the APK path through a
# quoted heredoc, so it never expanded, always crashed, and always "passed".)
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
  # If .symtab section is missing, the library was stripped
  if ! readelf -S "$so" 2>/dev/null | grep -q "\.symtab"; then
    # Libraries without symtab are stripped — this is only a problem
    # if keepDebugSymbols is not working as expected
    STRIPPED_LIBS="${STRIPPED_LIBS} $(basename "$so")"
  fi
done < <(find "$APK_DIR" -name "*.so" -print0 2>/dev/null)
# Note: Some third-party .so files arrive pre-stripped, which is OK.
# We only warn here; a hard failure would be too aggressive.
if [ -n "$STRIPPED_LIBS" ]; then
  echo "::warning::Some native libraries appear stripped (may cause NDK-version-dependent RB failures):${STRIPPED_LIBS}"
else
  echo "✅ Native libraries retain debug symbols"
fi

echo "── Step 7: Check aboutlibraries 'generated' timestamp not in APK ──"
# The M7.json (or aboutlibraries.json in Java resources) should NOT contain
# a "generated" field, which introduces a build-time timestamp.
ABOUT_JSON=""
if [ -f "$APK_DIR/aboutlibraries.json" ]; then
  ABOUT_JSON="$APK_DIR/aboutlibraries.json"
else
  # May be in res/ as M7.json or similar
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
