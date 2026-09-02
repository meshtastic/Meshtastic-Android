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

# 7. The wrapper itself: directory scanning, split-name parsing, the universal APK, the
#    single-split and no-APK cases, and the exit status across flavors. It sources the
#    checked-in allowlist, so the recorded fdroid gap is exercised for real here.
# tree <name> <flavor> <buildType> <abi>=<lib,lib,...>... — writes split APKs into a
# fixture outputs directory shaped like androidApp/build/outputs/apk.
tree() {
  local name=$1 flavor=$2 type=$3 spec dir="$WORKDIR/tree-$1/$2/$3" src
  shift 3
  mkdir -p "$dir"
  for spec in "$@"; do
    src="$WORKDIR/tree-$name-$flavor-${spec%%=*}-src"
    mkdir -p "$src/lib/${spec%%=*}"
    for lib in $(printf '%s' "${spec#*=}" | tr ',' ' '); do printf 'x' > "$src/lib/${spec%%=*}/$lib"; done
    (cd "$src" && zip -q -r "$dir/androidApp-$flavor-${spec%%=*}-$type.apk" lib)
  done
}
# wrapper <label> <tree> <want-exit> <want-substring>
wrapper() {
  local label=$1 tree=$2 want=$3 needle=$4 out rc=0
  out=$(scripts/verify-abi-parity.sh "$WORKDIR/tree-$tree" 2>&1) || rc=$?
  if [ "$rc" = "$want" ] && printf '%s' "$out" | grep -qF -- "$needle"; then
    echo "✅ $label"
  else
    echo "❌ $label: exit $rc (wanted $want), output:"; printf '%s\n' "$out" | sed 's/^/     /'
    FAILURES=$((FAILURES + 1))
  fi
}

tree even google debug armeabi-v7a=liba.so,libb.so arm64-v8a=liba.so,libb.so universal=liba.so,libb.so
wrapper "matching splits pass, universal ignored" even 0 "google: all splits ship the same native libraries"

tree gap google debug armeabi-v7a=liba.so arm64-v8a=liba.so,libengine.so
wrapper "unrecorded gap fails with the lib named" gap 1 "armeabi-v7a/libengine.so"

MAPLIBRE="libjniMaplibreNativeC.so,libmaplibre-native-c.so"
tree known fdroid release armeabi-v7a=liba.so "arm64-v8a=liba.so,$MAPLIBRE"
wrapper "the recorded fdroid gap passes and is counted" known 0 "fdroid: splits match apart from 2 recorded known gap(s)"

tree stale fdroid release "armeabi-v7a=liba.so,$MAPLIBRE" "arm64-v8a=liba.so,$MAPLIBRE"
wrapper "the recorded gap closing fails until the lines go" stale 1 "known-gap entries whose library is now present"

tree single google debug arm64-v8a=liba.so
wrapper "a lone split is nothing to compare, so no APKs were checked" single 1 "no split APKs found"

mkdir -p "$WORKDIR/tree-empty"
wrapper "an empty outputs directory fails loudly" empty 1 "no split APKs found"

tree both google debug armeabi-v7a=liba.so arm64-v8a=liba.so,libx.so
tree both fdroid debug armeabi-v7a=liba.so arm64-v8a=liba.so,liby.so
wrapper "one failure per flavor adds up in the exit status" both 2 "armeabi-v7a/liby.so"

echo
if [ "$FAILURES" -gt 0 ]; then
  echo "❌ $FAILURES check(s) failed"
  exit 1
fi
echo "✅ abi-parity self-test passed"
