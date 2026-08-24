#!/usr/bin/env bash
#
# PreToolUse hook (Bash) for Meshtastic-Android. Three jobs:
#
# 1. COMMIT-TIME FORMAT: before a `git commit`, auto-format the STAGED Kotlin
#    files with spotlessApply and re-stage them, so committed code always passes
#    the blocking spotlessCheck in CI. Directly addresses the recurring
#    "forgot to run spotless -> CI fail" loss.
#      - Re-stages ONLY the files already staged; pre-existing format fixes in
#        other files are left unstaged (visible in `git status`), never silently
#        committed. (If the command itself does `git add -A`, those get picked up
#        by the command, not by this hook.)
#      - Fails open: a gradle/tooling hiccup warns and ALLOWS the commit.
#
# 2. DESTRUCTIVE-OP CONFIRM: surface a confirmation (permissionDecision "ask")
#    before an irreversible git op — force-push or `reset --hard`. Flag-order
#    robust, unlike a settings.json prefix pattern. Asks, never hard-denies.
#
# 3. PRE-PUSH DETEKT GATE: before `git push`, run detekt and BLOCK on violation.
#    detekt is a blocking CI job but nothing else runs it locally (the commit
#    hook only does spotlessApply). Closes the recurring "skipped local check ->
#    CI fail" loss for lint. Test baseline (test/allTests) is NOT gated here —
#    too slow to run on every push; that one stays on the developer.
#
# FAILS OPEN throughout: missing jq / parse errors / non-git commands -> exit 0.

input=$(cat)
command -v jq >/dev/null 2>&1 || exit 0

cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty' 2>/dev/null)
[ -z "$cmd" ] && exit 0

ask() {  # $1 = reason; prompt the user to confirm
  jq -n --arg r "$1" \
    '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"ask",permissionDecisionReason:$r}}'
  exit 0
}

# Gradle gates apply ONLY to this project. Without this, pushes/commits in
# OTHER repos got blocked by a failing ./gradlew (bit us: had to evade with
# `git -C <path> push`). $1 = repo root; false -> caller should fail open.
is_this_repo() {
  [ -x "$1/gradlew" ] && grep -qi meshtastic "$1/settings.gradle.kts" 2>/dev/null
}

# --- 2. Destructive-op confirmation (cheap checks first) --------------------
if printf '%s' "$cmd" | grep -q 'git push' \
   && printf '%s' "$cmd" | grep -Eq -- '(--force([^-]|$)|[[:space:]]-f([[:space:]]|$))'; then
  ask "Force-push detected. This can overwrite remote history irreversibly. Confirm you intend to force-push (consider --force-with-lease instead). Flagged by .claude/hooks/pre-bash-guard.sh"
fi
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+reset[[:space:]]+--hard'; then
  ask "'git reset --hard' discards uncommitted work irreversibly. Confirm. Flagged by .claude/hooks/pre-bash-guard.sh"
fi

# --- 3. Pre-push detekt gate ------------------------------------------------
# Force-push already returned via ask() above; this only runs for a plain push.
if printf '%s' "$cmd" | grep -q 'git push'; then
  cwd=$(printf '%s' "$input" | jq -r '.cwd // empty' 2>/dev/null)
  [ -z "$cwd" ] && cwd="$PWD"
  repo_root=$(git -C "$cwd" rev-parse --show-toplevel 2>/dev/null) || exit 0
  is_this_repo "$repo_root" || exit 0
  export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
  out=$( (cd "$repo_root" && ./gradlew detekt --console=plain -q) 2>&1 )
  if [ $? -ne 0 ]; then
    {
      printf '%s\n' "detekt failed — blocking the push (it's a blocking CI gate). Last lines:"
      printf '%s\n' "$out" | tail -n 30
    } >&2
    exit 2
  fi
  exit 0
fi

# --- 1. Commit-time spotlessApply on staged Kotlin --------------------------
case "$cmd" in
  *"git commit"*) : ;;
  *) exit 0 ;;
esac

cwd=$(printf '%s' "$input" | jq -r '.cwd // empty' 2>/dev/null)
[ -z "$cwd" ] && cwd="$PWD"
repo_root=$(git -C "$cwd" rev-parse --show-toplevel 2>/dev/null) || exit 0
[ -n "$repo_root" ] || exit 0
is_this_repo "$repo_root" || exit 0

# Staged screenshot PNGs: allTests regenerates docs/assets/screenshots/*.png on
# this machine (host-render diff), and gradle-runner once auto-committed strays.
# Confirm they are intentional UI-change screenshots before they ride along.
shots=$(git -C "$repo_root" diff --cached --name-only -- 'docs/assets/screenshots/*.png' 2>/dev/null)
if [ -n "$shots" ]; then
  ask "Staged screenshot PNGs detected:
$shots
allTests regenerates these on this machine — if they are NOT intentional UI-change screenshots, unstage and restore them (git restore --staged --worktree -- docs/assets/screenshots) before committing. Flagged by .claude/hooks/pre-bash-guard.sh"
fi

# Staged Kotlin files (added/copied/modified/renamed). Nothing staged -> no-op.
staged=$(git -C "$repo_root" diff --cached --name-only --diff-filter=ACMR -- '*.kt' '*.kts' 2>/dev/null)
[ -z "$staged" ] && exit 0

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
out=$( (cd "$repo_root" && ./gradlew spotlessApply --console=plain -q) 2>&1 )
if [ $? -ne 0 ]; then
  {
    printf '%s\n' "spotless-precommit: spotlessApply failed — allowing the commit anyway (fail-open)."
    printf '%s\n' "Run the baseline check before pushing. First lines of output:"
    printf '%s\n' "$out" | head -n 20
  } >&2
  exit 0
fi

# Re-stage only the originally-staged Kotlin files (preserve staging intent).
while IFS= read -r f; do
  [ -n "$f" ] && git -C "$repo_root" add -- "$f" 2>/dev/null
done <<< "$staged"

exit 0
