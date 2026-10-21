---
name: baseline
description: Run the mandatory pre-push baseline verification for Meshtastic-Android — bootstrap, then spotlessApply/spotlessCheck/detekt/detektTypeResolved/assembleDebug/test/allTests (plus kmpSmokeCompile and sort-strings when relevant) via the gradle-runner subagent, restore the host-render screenshot diff, and report a single pass/fail. Use before every push. This is the check CI fails on when skipped.
disable-model-invocation: true
---

# baseline

The repo's verify-before-push gate, codified. CLAUDE.md/AGENTS.md mandate this before every push and CI has failed repeatedly when it was skipped. Run it, don't paraphrase it.

## 1. Bootstrap (don't skip — agent workspaces often lack these)
```bash
[ -n "$ANDROID_HOME" ] || for d in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" /opt/android-sdk; do [ -d "$d" ] && export ANDROID_HOME="$d" && break; done
```
No secrets copy is needed — `secrets.defaults.properties` is the plugin's own fallback.

## 2. Decide the command from what changed
```bash
git diff --name-only HEAD && git diff --cached --name-only
```
- **Strings touched** (`core/resources/.../values/strings.xml`): prepend `python3 scripts/sort-strings.py` (the PostToolUse hook usually already did this; running it again is a no-op if so).
- **A KMP module touched** (anything under `core/**`, `feature/**` with a `commonMain` source set): add `kmpSmokeCompile` to the gradle task list.
- **New top-level module**: confirm its `<root>/**` line is in `.github/workflows/pull-request.yml` `android:` filter, it is in `RootConventionPlugin`'s `ALL_MODULES_FULL`, and its tests are in a `reusable-check.yml` shard; the three `check-changes` drift guards fail the PR otherwise (`python3 scripts/check-changes-filter.py`, `check-module-list.py`, `check-test-shards.py`).

## 3. Run it via the gradle-runner subagent
Dispatch **gradle-runner** (keep the multi-thousand-line log out of context). The baseline is:
```
./gradlew spotlessApply spotlessCheck detekt detektTypeResolved assembleDebug test allTests
```
Add `kmpSmokeCompile` to that line if step 2 flagged a KMP module. Add `-PwarningsAsErrors=true` to match CI, which fails on any Kotlin compiler warning. Both `test` **and** `allTests` are required — `allTests` covers KMP modules (where bare `test` silently skips), `test` covers pure-Android/JVM modules.

## 4. Verify the tree wasn't mutated, then clean the screenshot diff
The gradle-runner subagent has Bash and has been observed reverting/editing files to force a green build. **After it returns, confirm the only changes are yours:**
```bash
git status --short
```
- If gradle-runner touched files you didn't, treat its PASS as suspect and re-run the failing task inline.
- If `docs/assets/screenshots/` shows changes and the work is not a UI change (you did not run `copyDocsScreenshots` on purpose), restore that directory so host-render noise does not ride into the PR:
```bash
git restore --source=HEAD --staged --worktree -- docs/assets/screenshots/
```

## 5. Report
One line: `BASELINE PASS` (+ any inline re-runs you did) or `BASELINE FAIL` with the failing task/test names from gradle-runner. Do not push on FAIL.
