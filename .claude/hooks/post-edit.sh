#!/usr/bin/env bash
#
# PostToolUse hook (Edit|Write|MultiEdit) for Meshtastic-Android.
#
# Front-runs four of this repo's own CI/governance gates locally, so the
# failure surfaces at edit time instead of in CI. Dispatches by edited path:
#
#   - base strings.xml      -> run scripts/sort-strings.py (keeps the file sorted
#                              and regenerates .skills/compose-ui/strings-index.txt;
#                              AGENTS.md mandates this but no CI job enforces it)
#   - fastlane/metadata/**  -> run scripts/check-store-metadata.py and BLOCK on
#                              store-rule violations (the pull-request.yml
#                              check-metadata job is blocking; F-Droid #4262)
#   - settings.gradle.kts   -> remind about the pull-request.yml paths-filter and
#                              ALL_MODULES_FULL drift guards for NEW top-level modules
#   - commonMain/commonTest -> BLOCK on java.*/android.* imports in .kt files (the
#                              KMP boundary, otherwise first caught by the iOS compile
#                              in kmpSmokeCompile for main sources or allTests for tests)
#
# Kotlin edits outside tests and previews also get warn-only Compose-pitfall notes.
#
# FAILS OPEN: any tooling/parse error allows the edit to stand (exit 0). Notes are
# surfaced to Claude via PostToolUse additionalContext; only the store metadata and
# KMP-boundary checks block (exit 2), because each front-runs a failing CI job.

input=$(cat)

# jq parses the hook payload; without it, fail open.
command -v jq >/dev/null 2>&1 || exit 0

file_path=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null)
[ -z "$file_path" ] && exit 0

cwd=$(printf '%s' "$input" | jq -r '.cwd // empty' 2>/dev/null)
[ -z "$cwd" ] && cwd="$PWD"

repo_root=$(git -C "$cwd" rev-parse --show-toplevel 2>/dev/null) || exit 0
[ -n "$repo_root" ] || exit 0

# Emit a non-blocking note back to Claude, then allow the edit.
emit_context() {
  jq -n --arg c "$1" \
    '{hookSpecificOutput:{hookEventName:"PostToolUse",additionalContext:$c}}'
  exit 0
}

case "$file_path" in
  *core/resources/src/commonMain/composeResources/values/strings.xml)
    if out=$( (cd "$repo_root" && python3 scripts/sort-strings.py) 2>&1 ); then
      emit_context "Auto-ran scripts/sort-strings.py: base strings.xml re-sorted and .skills/compose-ui/strings-index.txt regenerated. Line positions changed — re-read the file before any further edits to it."
    else
      emit_context "Tried to auto-run scripts/sort-strings.py after your strings.xml edit but it failed (likely malformed XML in what was just written — please check):
$out"
    fi
    ;;

  *fastlane/metadata/android/*)
    out=$( (cd "$repo_root" && python3 scripts/check-store-metadata.py) 2>&1 )
    rc=$?
    # 1 is a store-rule violation; any other failure is tooling and fails open.
    if [ "$rc" -eq 1 ]; then
      {
        printf '%s\n' "Store-listing metadata breaks a store rule (scripts/check-store-metadata.py)."
        printf '%s\n' "Fix this before it lands: the pull-request.yml check-metadata job is blocking (F-Droid #4262; limits count Unicode code points, not bytes). Details:"
        printf '%s\n' "$out"
      } >&2
      exit 2
    fi
    exit 0
    ;;

  *settings.gradle.kts)
    emit_context "You edited settings.gradle.kts. If you added a NEW TOP-LEVEL module directory, add its '<root>/**' line to the 'android:' paths-filter in .github/workflows/pull-request.yml (case-sensitive) or scripts/check-changes-filter.py will fail the PR, and add the module to ALL_MODULES_FULL in RootConventionPlugin.kt or scripts/check-module-list.py will. New sub-modules under an already-listed root (core/**, feature/**, etc.) need no filter change."
    ;;

  */src/commonMain/*.kt|*/src/commonTest/*.kt)
    # KMP No-Framework-Bleed (AGENTS.md): common source sets compile to iOS/JS too,
    # so java.*/android.* imports are illegal there. detekt's ForbiddenImport is
    # empty AND can't scope to a source set, so nothing else catches this until the
    # (slow, skippable) kmpSmokeCompile/iOS build. Cheap grep, blocks at edit time.
    bleed=$(grep -nE '^[[:space:]]*import[[:space:]]+(java|android)\.' "$file_path" 2>/dev/null)
    if [ -n "$bleed" ]; then
      {
        printf '%s\n' "KMP boundary violation — $file_path is a common source set but imports java.*/android.*:"
        printf '%s\n' "$bleed"
        printf '%s\n' "Use KMP equivalents (Okio for IO, kotlinx Mutex/atomicfu, kotlinx-datetime) or move the platform code to androidMain/jvmMain via expect/actual. (AGENTS.md No-Framework-Bleed; not caught until kmpSmokeCompile.)"
      } >&2
      exit 2
    fi
    ;;
esac

# --- Advisory Compose-pitfall checks for Kotlin edits (warn-only, never block) ---
case "$file_path" in
  *.kt) : ;;
  *) exit 0 ;;
esac
case "$file_path" in
  *Preview*|*commonTest*|*androidUnitTest*|*/test/*|*/androidTest/*) exit 0 ;;
esac

notes=""

# Lazy-list duplicate-key crash — shipped TWICE (bare telemetry.time; bare
# node.num). Flag key lambdas built on those exact fields.
risky=$(grep -nE 'key[[:space:]]*=[[:space:]]*\{[[:space:]]*([A-Za-z_][A-Za-z0-9_]*[[:space:]]*->[[:space:]]*)?[A-Za-z_][A-Za-z0-9_.]*\.(num|time)[[:space:]]*\}' "$file_path" 2>/dev/null)
if [ -n "$risky" ]; then
  notes="Lazy-list key built on .num/.time — this exact pattern shipped two production dup-key crashes (bare telemetry.time, fixed with \"\${time}_\$index\"; bare node.num, fixed with distinctBy since _\$index breaks animateItem). Keys must be unique across the submitted list — dedupe the source list or compose the key:
$risky"
fi

# Hardcoded user-facing strings — Crowdin never sees literals (caught on PR
# #6143). Main source sets only; matching surrounding hardcoded code is not
# an excuse (that's a latent bug, not a pattern).
case "$file_path" in
  */commonMain/*|*/androidMain/*)
    # Two shapes: inline Text("...") / Text(text = "..."), and the multiline
    # form where 'text = "..."' sits on its own line inside a formatted call.
    hardcoded=$(grep -nE '(Text\(|text[[:space:]]*=)[[:space:]]*"[A-Za-z]' "$file_path" 2>/dev/null)
    if [ -n "$hardcoded" ]; then
      [ -n "$notes" ] && notes="$notes

"
      notes="${notes}Possible hardcoded user-facing string(s) — user-facing text must use stringResource(Res.string.x) or Crowdin never sees it (PR #6143). Check .skills/compose-ui/strings-index.txt for an existing string first:
$hardcoded"
    fi
    ;;
esac

[ -n "$notes" ] && emit_context "$notes"

exit 0
