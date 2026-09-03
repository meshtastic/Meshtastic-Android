#!/usr/bin/env bash
# Fails when an ABI split ships fewer native libraries than its siblings (see #7001).
# Run from repo root after assembling the split APKs (CI: android-check); takes the
# outputs directory as an optional argument, default androidApp/build/outputs/apk.
set -euo pipefail

cd "$(dirname "$0")/.."
# shellcheck source=scripts/lib/abi-parity.sh
. scripts/lib/abi-parity.sh

OUT=${1:-androidApp/build/outputs/apk}
FAILURES=0
CHECKED=0

for dir in "$OUT"/*/debug "$OUT"/*/release; do
  [ -d "$dir" ] || continue
  flavor=$(basename "$(dirname "$dir")")
  specs=()
  for apk in "$dir"/androidApp-"$flavor"-*-*.apk; do
    [ -f "$apk" ] || continue
    abi=$(basename "$apk" | sed -E "s/^androidApp-$flavor-(.*)-(debug|release)\.apk$/\1/")
    [ "$abi" = universal ] && continue
    specs+=("$abi=$apk")
  done
  # One split means splits are disabled for this build; there is nothing to compare.
  [ "${#specs[@]}" -ge 2 ] || continue

  check_abi_parity "$flavor" "${specs[@]}"
  CHECKED=$((CHECKED + 1))
  flavor_ok=1
  if [ -n "$ABI_PARITY_MISSING" ]; then
    echo "::error::$flavor: native libraries missing from a split:$ABI_PARITY_MISSING"
    echo "  Every ABI in abiFilters must ship every library, or the app installs and then crashes"
    echo "  with UnsatisfiedLinkError on that ABI. Fix the dependency, drop the ABI, or record a"
    echo "  known gap in scripts/lib/abi-parity.sh with the reason and what removes it."
    FAILURES=$((FAILURES + 1)); flavor_ok=0
  fi
  if [ -n "$ABI_PARITY_STALE" ]; then
    echo "::error::$flavor: known-gap entries whose library is now present:$ABI_PARITY_STALE"
    echo "  The gap has closed. Delete those lines from ABI_PARITY_KNOWN_GAPS in"
    echo "  scripts/lib/abi-parity.sh in this same change."
    FAILURES=$((FAILURES + 1)); flavor_ok=0
  fi
  if [ "$flavor_ok" -eq 1 ]; then
    if [ "$ABI_PARITY_KNOWN" -gt 0 ]; then
      echo "✅ $flavor: splits match apart from $ABI_PARITY_KNOWN recorded known gap(s)"
    else
      echo "✅ $flavor: all splits ship the same native libraries"
    fi
  fi
done

if [ "$CHECKED" -eq 0 ]; then
  echo "::error::no split APKs found under $OUT — assemble with ABI splits enabled first"
  exit 1
fi
exit "$FAILURES"
