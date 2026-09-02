# shellcheck shell=bash
# Checks that every ABI split of a flavor ships the same set of native libraries.
# Sourced by verify-abi-parity.sh; exercised directly by verify-abi-parity-selftest.sh.
#
# A dependency published for only some of our ABIs installs cleanly on the others and dies
# with UnsatisfiedLinkError the first time it is touched — nothing at build time says a word.
# That is #7001: maplibre-native-ffi 0.202608.3 ships arm64-v8a and x86_64 only, so the
# F-Droid armeabi-v7a split had no map engine and the map tab crashed the app.

# Gaps we know about and have decided to ship. One per line: <flavor> <abi> <lib>.
# An entry is checked both ways — the lib must be absent from that split, and the check
# fails once it turns up, so an entry cannot outlive its reason: the dependency bump that
# closes the gap goes red until the line is deleted with it.
#
# fdroid armeabi-v7a: no 32-bit ARM build of MapLibre exists yet. Upstream merged it on
# 2026-08-24 (maplibre-native-ffi #658/#659/#660); the release carrying it, and the
# maplibre-compose bump onto it, are what remove these two lines. Until then the app shows
# a message instead of a map on those devices (#7005).
ABI_PARITY_KNOWN_GAPS="
fdroid armeabi-v7a libjniMaplibreNativeC.so
fdroid armeabi-v7a libmaplibre-native-c.so
"

# apk_libs <apk> <abi> — basenames of the libs under lib/<abi>/ in the APK, one per line.
apk_libs() {
  unzip -Z1 "$1" "lib/$2/*.so" 2>/dev/null | sed 's|.*/||' | sort -u
}

# check_abi_parity <flavor> <abi>=<apk> [<abi>=<apk> ...]
# Sets ABI_PARITY_MISSING and ABI_PARITY_STALE (space-prefixed "<abi>/<lib>" entries) and
# ABI_PARITY_KNOWN, the number of known-gap lines that held.
#
# The reference set is the union across splits: a lib present in any ABI is one the app
# expects to load, so every other ABI needs it too unless a known-gap line says otherwise.
check_abi_parity() {
  local flavor=$1 spec abi apk lib union="" libs
  shift
  ABI_PARITY_MISSING=""
  ABI_PARITY_STALE=""
  ABI_PARITY_KNOWN=0

  for spec in "$@"; do
    union="$union
$(apk_libs "${spec#*=}" "${spec%%=*}")"
  done
  union=$(printf '%s\n' "$union" | sed '/^$/d' | sort -u)

  for spec in "$@"; do
    abi=${spec%%=*}
    apk=${spec#*=}
    libs=$(apk_libs "$apk" "$abi")
    while IFS= read -r lib; do
      [ -n "$lib" ] || continue
      if printf '%s\n' "$ABI_PARITY_KNOWN_GAPS" | grep -qxF "$flavor $abi $lib"; then
        if printf '%s\n' "$libs" | grep -qxF "$lib"; then
          ABI_PARITY_STALE="${ABI_PARITY_STALE} ${abi}/${lib}"
        else
          ABI_PARITY_KNOWN=$((ABI_PARITY_KNOWN + 1))
        fi
      elif ! printf '%s\n' "$libs" | grep -qxF "$lib"; then
        ABI_PARITY_MISSING="${ABI_PARITY_MISSING} ${abi}/${lib}"
      fi
    done <<< "$union"
  done
}
