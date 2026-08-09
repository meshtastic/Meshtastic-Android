#!/usr/bin/env bash
#
# PostToolUse hook (Task|Agent) for Meshtastic-Android.
#
# Tripwire for subagent side effects: gradle-runner has silently edited/reverted
# files to make builds pass AND made git commits (once bundling stray screenshot
# PNGs). After every subagent returns, surface HEAD + dirty files to the main
# loop as additionalContext — but only when there is something to see: a dirty
# tree, or a HEAD commit younger than 15 minutes (possibly made by the subagent
# that just finished).
#
# FAILS OPEN: any error -> exit 0 with no output.

input=$(cat)
command -v jq >/dev/null 2>&1 || exit 0

cwd=$(printf '%s' "$input" | jq -r '.cwd // empty' 2>/dev/null)
[ -z "$cwd" ] && cwd="$PWD"
repo_root=$(git -C "$cwd" rev-parse --show-toplevel 2>/dev/null) || exit 0

dirty=$(git -C "$repo_root" status --porcelain 2>/dev/null | head -20)
head_line=$(git -C "$repo_root" log -1 --format='%h %s (%cr)' 2>/dev/null)
head_ct=$(git -C "$repo_root" log -1 --format=%ct 2>/dev/null)
[ -n "$head_ct" ] || head_ct=0
head_age=$(( $(date +%s) - head_ct ))

fresh_commit=""
[ "$head_age" -lt 900 ] && fresh_commit="yes"

[ -z "$dirty" ] && [ -z "$fresh_commit" ] && exit 0

note="Subagent-audit (.claude/hooks/subagent-audit.sh) — post-subagent tree check:
HEAD: $head_line"
if [ -n "$fresh_commit" ]; then
  note="$note
^ HEAD is under 15 min old. If YOU did not make this commit, the subagent did (gradle-runner has done this before) — inspect with 'git show --stat' before building on it."
fi
if [ -n "$dirty" ]; then
  note="$note
Dirty files (first 20):
$dirty
Expected if these are your own in-progress edits. If the subagent was only supposed to BUILD/TEST, verify it didn't edit or revert files to force a pass (git diff)."
fi

jq -n --arg c "$note" '{hookSpecificOutput:{hookEventName:"PostToolUse",additionalContext:$c}}'
exit 0
