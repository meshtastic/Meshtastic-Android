---
name: pr
description: Push the current branch and open a draft PR for Meshtastic-Android the repo way — baseline verified first, body drafted per CONTRIBUTING.md (WHY-first, categorized changes, screenshots for UI work). Use whenever work is ready to go up as a PR.
disable-model-invocation: true
---

# pr

Opens a PR the way this repo expects. The SOP lives in `CONTRIBUTING.md` → **Pull Requests** — **read it now and follow it**; this skill only adds the steps around it.

## 1. Pre-flight
- Target branch is `main` unless told otherwise.
- On the default branch? Create a feature branch first.
- Baseline must be green **this session** (`spotlessApply spotlessCheck detekt detektTypeResolved assembleDebug test allTests`). If it hasn't run since the last code change, run `/baseline` first. The push hook in `.claude/hooks/pre-bash-guard.sh` runs only `detekt`.
- `git status`: nothing unintended staged. Never commit `.agent_memory/`. If `docs/assets/screenshots/*.png` are dirty and the change is not an intentional UI change, restore them: `git restore --source=HEAD --staged --worktree -- docs/assets/screenshots`.

## 2. Body
Draft per `CONTRIBUTING.md` → Pull Requests → **Writing the description** (WHY-first summary, then changes under the 🌟 New Features / 🛠️ Refactoring & Architecture / 🐛 Bug Fixes / 🧹 Chores categories that apply, **Testing Performed** section when tests were added/changed).

**Screenshots** (UI changes want them): `CONTRIBUTING.md` → Pull Requests → Screenshots owns the policy, and it allows either a GitHub-hosted upload or a committed image. When committing one, push it first and embed it by commit-pinned raw URL so it renders immediately:
```
https://raw.githubusercontent.com/<owner>/<repo>/<full-commit-sha>/<path/to/img.png>
```
Pin to the SHA that contains the image, not the branch name.

## 3. Push and open
```bash
git push -u origin HEAD
gh pr create --draft --base <target> --title "<type>: <summary>" --body-file <(printf '%s' "$BODY")
```
- `--base` is the target branch chosen in pre-flight (`main` unless told otherwise) — always pass it explicitly; omitting it silently uses the repo default.
- Draft by default; only `--ready` if explicitly asked.
- Report the PR URL as a markdown link.
