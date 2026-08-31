# shellcheck shell=bash
# Classifies the native libraries in an unpacked APK as known-pre-stripped or unexpected.
# Sourced by verify-rb.sh (Step 6); exercised directly by verify-rb-selftest.sh.

# These arrive pre-stripped from their upstream AARs and always will. Listing them every run
# made the warning 100% noise, so it never read as a signal; only a .so outside this set is
# worth a warning. Add an entry when a new dependency's prebuilt lib shows up — never to
# silence one we build ourselves.
KNOWN_PRESTRIPPED_LIBS="
libandroidx.graphics.path.so
libimage_processing_util_jni.so
libjniMaplibreNativeC.so
libmaplibre-native-c.so
libsurface_util_jni.so
"

# scan_stripped_libs <unpacked-apk-dir>
# Sets STRIPPED_UNEXPECTED (space-prefixed basenames) and STRIPPED_KNOWN_COUNT.
#
# Matching is by basename: inside one APK every library lives at lib/<abi>/<name>.so, so a
# basename is unique per ABI by construction and the same basename across ABIs is the same
# library — which is the duplication `sort -u` collapses.
scan_stripped_libs() {
  local dir=$1 lib
  STRIPPED_UNEXPECTED=""
  STRIPPED_KNOWN_COUNT=0
  while IFS= read -r lib; do
    [ -n "$lib" ] || continue
    if printf '%s\n' "$KNOWN_PRESTRIPPED_LIBS" | grep -qxF "$lib"; then
      STRIPPED_KNOWN_COUNT=$((STRIPPED_KNOWN_COUNT + 1))
    else
      STRIPPED_UNEXPECTED="${STRIPPED_UNEXPECTED} ${lib}"
    fi
  done < <(
    while IFS= read -r -d '' so; do
      # no .symtab = stripped
      if ! readelf -S "$so" 2>/dev/null | grep -q "\.symtab"; then
        basename "$so"
      fi
    done < <(find "$dir" -name "*.so" -print0 2>/dev/null) | sort -u
  )
}
