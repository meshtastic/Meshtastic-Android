#!/usr/bin/env bash
# Every docs screenshot named in screenshot-tests/docs-screenshot-aliases.properties is a copy
# of a recorded reference image. copyDocsScreenshots makes the copy, and nothing else keeps it
# current, so a re-recorded reference leaves the docs showing the old UI. This fails when a copy
# and its reference differ, when an alias names a reference that does not exist, and when two
# aliases name the same reference, since the copy task then writes only one of them.
#
# Usage: check-docs-screenshots.sh [repo-root]
set -u

# Empty for the repository root, so annotations carry repository-relative paths.
P="${1:-}"
P="${P:+${P%/}/}"
ALIASES="${P}screenshot-tests/docs-screenshot-aliases.properties"
DOCS="${P}docs/assets/screenshots"
REFS=(
  "${P}screenshot-tests/src/screenshotTestDebug/reference"
  "${P}docs-screenshots/src/screenshotTestDebug/reference"
)
FIX="./gradlew :screenshot-tests:copyDocsScreenshots"

[ -f "$ALIASES" ] || { echo "::error::$ALIASES not found"; exit 2; }

status=0
checked=0
declare -A seen

while IFS='=' read -r doc ref || [ -n "$doc$ref" ]; do
  doc="${doc//[[:space:]]/}"
  ref="${ref//[[:space:]]/}"
  case "$doc" in ''|\#*) continue ;; esac
  checked=$((checked + 1))

  if [ -n "${seen[$ref]:-}" ]; then
    echo "::error file=$ALIASES::$doc and ${seen[$ref]} both name $ref. Only one alias per reference is copied."
    status=1
    continue
  fi
  seen[$ref]=$doc

  src=""
  for dir in "${REFS[@]}"; do
    [ -d "$dir" ] || continue
    found=$(find "$dir" -type f -name "$ref" -print -quit)
    if [ -n "$found" ]; then src=$found; break; fi
  done

  if [ -z "$src" ]; then
    echo "::error file=$ALIASES::$doc names $ref, which is in neither reference directory."
    status=1
  elif [ ! -f "$DOCS/$doc" ]; then
    echo "::error file=$ALIASES::$DOCS/$doc is missing. Run: $FIX"
    status=1
  elif ! cmp -s "$src" "$DOCS/$doc"; then
    echo "::error file=$DOCS/$doc::differs from its reference ${src#"$P"}. Run: $FIX"
    status=1
  fi
done < "$ALIASES"

if [ "$status" -ne 0 ]; then
  echo "FAILED: docs screenshots are out of step with their references."
  exit 1
fi
echo "PASSED: $checked docs screenshots match their references."
