---
name: pr
description: Push the current branch and open a draft PR for Meshtastic-Android the repo way — baseline verified first, body drafted per .github/copilot-pull-request-instructions.md (WHY-first, categorized changes), screenshots embedded via commit-pinned raw URLs. Use whenever work is ready to go up as a PR.
disable-model-invocation: true
---

# pr

Opens a PR the way this repo expects. The SOP lives in `.github/copilot-pull-request-instructions.md` — **read it now and follow it**; this skill only adds the steps around it.

## 1. Pre-flight
- Target branch is `main` unless told otherwise.
- On the default branch? Create a feature branch first.
- Baseline must be green **this session** (`spotlessApply spotlessCheck detekt assembleDebug test allTests`). If it hasn't run since the last code change, run `/baseline` first — CI has failed repeatedly on skipped local checks. (The pre-push hook only gates detekt.)
- `git status`: nothing unintended staged. Never commit `.agent_memory/`. If `docs/assets/screenshots/*.png` are dirty from a test run (not an intentional UI change), restore them: `git checkout -- docs/assets/screenshots`.

## 2. Body
Draft per the SOP file (WHY-first summary, then changes under the 🌟 Features / 🛠️ Improvements / 🐛 Bug Fixes / 🧹 Chores categories that apply, **Testing Performed** section when tests were added/changed).

**Screenshots** (UI changes want them): commit the PNGs on the branch, push, then embed with commit-pinned raw URLs so they render in the PR body immediately:
```
https://raw.githubusercontent.com/<owner>/<repo>/<full-commit-sha>/<path/to/img.png>
```
Pin to the SHA that contains the image, not the branch name.

## 3. Push and open
```bash
git push -u origin HEAD
gh pr create --draft --title "<type>: <summary>" --body-file <(printf '%s' "$BODY")
```
- Draft by default; only `--ready` if explicitly asked.
- End the body with: `🤖 Generated with [Claude Code](https://claude.com/claude-code)`
- Report the PR URL as a markdown link.
