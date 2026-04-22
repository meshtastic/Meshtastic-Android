# Meshtastic Android - GitHub Copilot Guide

> **Note:** The canonical instructions for all AI Agents have been deduplicated. 

You MUST immediately read and internalize the unified instructions located at the root of the repository in `AGENTS.md`. 
After reading `AGENTS.md`, consult the `.skills/` directory for task-specific playbooks.

## Critical reminders (do not skip)

These rules live in `AGENTS.md` but are inlined here because past sessions repeatedly violated them:

- **Never modify `core/proto/src/main/proto/*.proto`** — it's an upstream submodule. If a feature needs a proto change, stop and label the issue `upstream` pointing at `meshtastic/protobufs`.
- **Verify-then-push, never "should be green".** Before any `git push`, run `./gradlew spotlessApply detekt` plus relevant `:module:test` for touched modules. After pushing, do **not** claim CI is green based on assumption — fetch the actual status with `gh pr checks <PR>` (or `gh run list --branch <branch> --limit 5`) and only report success once the checks return ✅. Phrases like "CI should be green now" are banned.

## Tooling conventions

- **Engage the `android-cli` skill automatically.** Whenever the user mentions adb, emulator, device install/run, screenshots, or named devices (e.g. `1c10`, `Pixel 6a`), invoke the `android-cli` skill *before* running raw `adb` or `gradle install*` commands. Don't wait for the user to paste the skill-context block.
- **Screenshots and ad-hoc artifacts go to `/tmp/`.** Never write annotated screenshots, log dumps, or scratch files into the repo working tree — they pollute `git status` and risk accidental commits. Use `/tmp/` or the session workspace (`~/.copilot/session-state/<id>/files/`).
