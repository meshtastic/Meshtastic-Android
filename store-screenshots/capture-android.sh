#!/usr/bin/env bash
#
# Copyright (c) 2026 Meshtastic LLC
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#
# Captures one flavor's store screenshots on the connected device from APKs already built
# by `:androidApp:assemble<Flavor>Debug :store-screenshots:assemble<Flavor>Debug`.
#
#   capture-android.sh <google|fdroid> <output dir>
#
# The PNGs land in <output dir>/<flavor>. The instrumentation output and the device logcat
# land in <output dir>/test-results/<flavor>, since no Gradle connected task writes a report.
set -euo pipefail

FLAVOR=${1:?usage: capture-android.sh <google|fdroid> <output dir>}
OUT=${2:?usage: capture-android.sh <google|fdroid> <output dir>}
SHOTS="$OUT/$FLAVOR"
RESULTS="$OUT/test-results/$FLAVOR"
DEVICE_OUTPUT=/data/local/tmp/store-screenshots/$FLAVOR
mkdir -p "$SHOTS" "$RESULTS"

app_apk=$(find "androidApp/build/outputs/apk/$FLAVOR/debug" -name '*-universal-debug.apk' | head -1)
test_apk=$(find "store-screenshots/build/outputs/apk/$FLAVOR/debug" -name '*.apk' | head -1)
[ -n "$app_apk" ] && [ -n "$test_apk" ] || { echo "::error::$FLAVOR APKs not built"; exit 1; }

adb install -r -t "$app_apk"
adb install -r -t "$test_apk"
runner=$(adb shell pm list instrumentation | tr -d '\r' | sed -n 's/^instrumentation:\(org\.meshtastic\.storescreenshots\/[^ ]*\).*/\1/p' | head -1)
[ -n "$runner" ] || { echo "::error::no store-screenshots instrumentation on the device"; exit 1; }

adb logcat -c || true
# am instrument exits 0 whatever the test does; the verdict is its "OK (1 test)" line.
adb shell am instrument -w -r \
  -e targetAppId "com.geeksville.mesh.$FLAVOR.debug" \
  -e flavor "$FLAVOR" \
  "$runner" | tr -d '\r' | tee "$RESULTS/instrument.txt"
adb logcat -d > "$RESULTS/logcat.txt"

adb pull "$DEVICE_OUTPUT/." "$SHOTS/" || true
grep -q '^OK (1 test)' "$RESULTS/instrument.txt"
