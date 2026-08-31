#!/usr/bin/env bash
# Self-test for scripts/lib/stripped-libs.sh — verify-rb.sh Step 6.
#
# Step 6 itself only runs in the merge queue and needs two full release builds, so the
# classification it depends on would otherwise ship unexercised: a typo in the allowlist
# silently brings the noise back, and a broken dedup silently restores per-ABI repeats.
# This runs in lint-check on every PR instead.
#
# readelf is stubbed: the fixture writes the literal STRIPPED into a lib to mean "no
# .symtab", anything else means the symbol table survived.
set -euo pipefail

cd "$(dirname "$0")/.."
# shellcheck source=scripts/lib/stripped-libs.sh
. scripts/lib/stripped-libs.sh

WORKDIR=$(mktemp -d)
trap 'rm -rf "$WORKDIR"' EXIT

mkdir -p "$WORKDIR/bin"
cat > "$WORKDIR/bin/readelf" <<'STUB'
#!/usr/bin/env bash
# Stub: report a .symtab section unless the fixture marked the file STRIPPED.
for arg in "$@"; do :; done
if [ -f "$arg" ] && [ "$(cat "$arg")" = "STRIPPED" ]; then
  echo "  [ 1] .text             PROGBITS"
else
  echo "  [ 1] .text             PROGBITS"
  echo "  [ 2] .symtab           SYMTAB"
fi
STUB
chmod +x "$WORKDIR/bin/readelf"
PATH="$WORKDIR/bin:$PATH"

FAILURES=0

# lib <apk-dir> <name> <STRIPPED|SYMBOLS> — writes the lib into both ABI directories,
# mirroring how an APK ships one library per supported ABI.
lib() {
  local dir=$1 name=$2 state=$3 abi
  for abi in arm64-v8a armeabi-v7a; do
    mkdir -p "$dir/lib/$abi"
    printf '%s' "$state" > "$dir/lib/$abi/$name"
  done
}

check() {
  local name=$1 want_known=$2 want_unexpected=$3
  local got_unexpected="${STRIPPED_UNEXPECTED# }"
  if [ "$STRIPPED_KNOWN_COUNT" = "$want_known" ] && [ "$got_unexpected" = "$want_unexpected" ]; then
    echo "✅ $name"
  else
    echo "❌ $name"
    echo "     known:      want '$want_known' got '$STRIPPED_KNOWN_COUNT'"
    echo "     unexpected: want '$want_unexpected' got '$got_unexpected'"
    FAILURES=$((FAILURES + 1))
  fi
}

# 1. The regression that motivated the allowlist: known libs shipped for two ABIs collapse to
#    one entry each and stay silent, while a genuinely unexpected stripped lib still warns.
A="$WORKDIR/case-mixed"
lib "$A" libmaplibre-native-c.so STRIPPED
lib "$A" libandroidx.graphics.path.so STRIPPED
lib "$A" libmine.so STRIPPED
scan_stripped_libs "$A"
check "two-ABI known libs dedupe; unexpected lib still warns" 2 "libmine.so"

# 2. Every allowlist entry must actually match a file of that name — a typo or stray
#    whitespace in the list would surface here as an unexpected lib rather than in CI logs
#    six months from now.
B="$WORKDIR/case-allowlist"
COUNT=0
while IFS= read -r name; do
  [ -n "$name" ] || continue
  lib "$B" "$name" STRIPPED
  COUNT=$((COUNT + 1))
done <<< "$KNOWN_PRESTRIPPED_LIBS"
scan_stripped_libs "$B"
check "every allowlist entry matches its own name" "$COUNT" ""

# 3. Unstripped libraries are not classified at all, allowlisted or not.
C="$WORKDIR/case-symbols"
lib "$C" libmaplibre-native-c.so SYMBOLS
lib "$C" libmine.so SYMBOLS
scan_stripped_libs "$C"
check "libraries retaining .symtab are ignored" 0 ""

# 4. An APK with no native libraries at all is silent, not an empty-string warning.
D="$WORKDIR/case-empty"
mkdir -p "$D"
scan_stripped_libs "$D"
check "no native libraries" 0 ""

# 5. An allowlisted name that is NOT stripped does not inflate the known count — the count
#    reports what was actually skipped, so a shrinking set stays visible.
E="$WORKDIR/case-partial"
lib "$E" libmaplibre-native-c.so STRIPPED
lib "$E" libsurface_util_jni.so SYMBOLS
scan_stripped_libs "$E"
check "known count reflects libs actually skipped" 1 ""

# 6. Allowlist matching is against the whole entry, not a substring of one. Dropping the -x
#    from the grep would let any name contained in an entry — here the entry minus its "lib"
#    prefix — be silently accepted as known.
F="$WORKDIR/case-substring"
lib "$F" maplibre-native-c.so STRIPPED
scan_stripped_libs "$F"
check "allowlist matches whole entries, not substrings" 0 "maplibre-native-c.so"

if [ "$FAILURES" -ne 0 ]; then
  echo "::error::verify-rb Step 6 self-test failed ($FAILURES case(s))"
  exit 1
fi
echo "✅ verify-rb Step 6 self-test passed (6 cases)"
