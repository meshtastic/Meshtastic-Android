---
name: baseline
description: Run the mandatory pre-push baseline verification for Meshtastic-Android — bootstrap, then spotlessApply/spotlessCheck/detekt/assembleDebug/test/allTests (plus kmpSmokeCompile and sort-strings when relevant) via the gradle-runner subagent, restore the host-render screenshot diff, and report a single pass/fail. Use before every push. This is the check CI fails on when skipped.
disable-model-invocation: true
---

# baseline

The repo's verify-before-push gate, codified. CLAUDE.md/AGENTS.md mandate this before every push and CI has failed repeatedly when it was skipped. Run it, don't paraphrase it.

## 1. Bootstrap (don't skip — agent workspaces often lack these)
```bash
[ -z "$ANDROID_HOME" ] && export ANDROID_HOME="$HOME/Library/Android/sdk"
[ -f local.properties ] || cp secrets.defaults.properties local.properties
```

## 2. Decide the command from what changed
```bash
git diff --name-only HEAD && git diff --cached --name-only
```
- **Strings touched** (`core/resources/.../values/strings.xml`): prepend `python3 scripts/sort-strings.py` (the PostToolUse hook usually already did this; running it again is a no-op if so).
- **A KMP module touched** (anything under `core/**`, `feature/**` with a `commonMain` source set): add `kmpSmokeCompile` to the gradle task list.
- **New top-level module**: confirm its `<root>/**` line is in `.github/workflows/pull-request.yml` `android:` filter (else the drift guard fails the PR — #5735).

## 3. Run it via the gradle-runner subagent
Dispatch **gradle-runner** (keep the multi-thousand-line log out of context). The baseline is:
```
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests
```
Add `kmpSmokeCompile` to that line if step 2 flagged a KMP module. Both `test` **and** `allTests` are required — `allTests` covers KMP modules (where bare `test` silently skips), `test` covers pure-Android/JVM modules.

## 4. Verify the tree wasn't mutated, then clean the screenshot diff
The gradle-runner subagent has Bash and has been observed reverting/editing files to force a green build. **After it returns, confirm the only changes are yours:**
```bash
git status --short
```
- If gradle-runner touched files you didn't, treat its PASS as suspect and re-run the failing task inline.
- The full baseline regenerates tracked screenshots as host-render noise on this machine — drop them so they don't pollute the PR:
```bash
git checkout -- docs/assets/screenshots/
```

## 5. Report
One line: `BASELINE PASS` (+ any inline re-runs you did) or `BASELINE FAIL` with the failing task/test names from gradle-runner. Do not push on FAIL.
