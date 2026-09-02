#!/usr/bin/env bash
# Self-test for scripts/lib/abi-parity.sh — the classifier behind verify-abi-parity.sh.
#
# The real check only sees whatever the current dependencies happen to ship, so the two
# failure modes it exists for — a lib missing from one ABI, and a known-gap line that has
# outlived its reason — would otherwise go unexercised until they bit. Fixture APKs are
# plain zips: unzip only reads the entry names, so the libs are one-byte files.
set -euo pipefail

cd "$(dirname "$0")/.."
# shellcheck source=scripts/lib/abi-parity.sh
. scripts/lib/abi-parity.sh
# The cases below overwrite the allowlist; keep the checked-in one for the last check.
CHECKED_IN_KNOWN_GAPS=$ABI_PARITY_KNOWN_GAPS

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

FAILURES=0

# apk <name> <abi> <lib>... — builds $WORKDIR/<name>.apk with those libs under lib/<abi>/.
apk() {
  local name=$1 abi=$2 lib dir="$WORKDIR/$1-src"
  shift 2
  mkdir -p "$dir/lib/$abi"
  for lib in "$@"; do printf 'x' > "$dir/lib/$abi/$lib"; done
  (cd "$dir" && zip -q -r "$WORKDIR/$name.apk" lib)
}

# expect <label> <var> <want>
expect() {
  local label=$1 var=$2 want=$3 got
  got=$(eval "printf '%s' \"\$$var\"")
  if [ "$got" = "$want" ]; then
    echo "✅ $label"
  else
    echo "❌ $label: $var='$got', expected '$want'"
    FAILURES=$((FAILURES + 1))
  fi
}

# 1. Matching splits: nothing to report.
apk even-v7a armeabi-v7a liba.so libb.so
apk even-v8a arm64-v8a liba.so libb.so
check_abi_parity testflavor "armeabi-v7a=$WORKDIR/even-v7a.apk" "arm64-v8a=$WORKDIR/even-v8a.apk"
expect "matching splits report nothing" ABI_PARITY_MISSING ""
expect "matching splits have no stale entries" ABI_PARITY_STALE ""
expect "matching splits count no known gaps" ABI_PARITY_KNOWN 0

# 2. A lib only one ABI ships, with no known-gap line for it.
apk gap-v7a armeabi-v7a liba.so
apk gap-v8a arm64-v8a liba.so libengine.so
check_abi_parity testflavor "armeabi-v7a=$WORKDIR/gap-v7a.apk" "arm64-v8a=$WORKDIR/gap-v8a.apk"
expect "unlisted gap is reported as missing" ABI_PARITY_MISSING " armeabi-v7a/libengine.so"
expect "unlisted gap is not stale" ABI_PARITY_STALE ""

# 3. The same gap, recorded. Direction matters: the gap is on v7a, so listing it under v8a
#    must not excuse it.
ABI_PARITY_KNOWN_GAPS="
testflavor armeabi-v7a libengine.so
"
check_abi_parity testflavor "armeabi-v7a=$WORKDIR/gap-v7a.apk" "arm64-v8a=$WORKDIR/gap-v8a.apk"
expect "recorded gap is excused" ABI_PARITY_MISSING ""
expect "recorded gap is counted" ABI_PARITY_KNOWN 1
ABI_PARITY_KNOWN_GAPS="
testflavor arm64-v8a libengine.so
"
check_abi_parity testflavor "armeabi-v7a=$WORKDIR/gap-v7a.apk" "arm64-v8a=$WORKDIR/gap-v8a.apk"
expect "gap recorded against the wrong abi still fails" ABI_PARITY_MISSING " armeabi-v7a/libengine.so"
expect "wrong-abi entry is flagged stale, since v8a has the lib" ABI_PARITY_STALE " arm64-v8a/libengine.so"

# 4. A known-gap line for another flavor does not excuse this one.
ABI_PARITY_KNOWN_GAPS="
otherflavor armeabi-v7a libengine.so
"
check_abi_parity testflavor "armeabi-v7a=$WORKDIR/gap-v7a.apk" "arm64-v8a=$WORKDIR/gap-v8a.apk"
expect "other flavor's entry does not excuse this flavor" ABI_PARITY_MISSING " armeabi-v7a/libengine.so"

# 5. The gap closes upstream but the line stays: must fail so the line gets deleted.
ABI_PARITY_KNOWN_GAPS="
testflavor armeabi-v7a liba.so
"
check_abi_parity testflavor "armeabi-v7a=$WORKDIR/even-v7a.apk" "arm64-v8a=$WORKDIR/even-v8a.apk"
expect "closed gap with a leftover line is stale" ABI_PARITY_STALE " armeabi-v7a/liba.so"
expect "closed gap reports nothing missing" ABI_PARITY_MISSING ""
expect "stale line is not counted as a held gap" ABI_PARITY_KNOWN 0

# 6. The entries actually checked in must name real ABIs and real lib names.
while IFS= read -r line; do
  [ -n "$line" ] || continue
  case "$line" in
    fdroid\ armeabi-v7a\ lib*.so|fdroid\ arm64-v8a\ lib*.so|google\ armeabi-v7a\ lib*.so|google\ arm64-v8a\ lib*.so)
      echo "✅ known-gap line is well-formed: $line" ;;
    *)
      echo "❌ known-gap line is malformed: '$line'"; FAILURES=$((FAILURES + 1)) ;;
  esac
done <<< "$CHECKED_IN_KNOWN_GAPS"

echo
if [ "$FAILURES" -gt 0 ]; then
  echo "❌ $FAILURES check(s) failed"
  exit 1
fi
echo "✅ abi-parity self-test passed"
