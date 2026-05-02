# Agent Session Context - Meshtastic Android

## Current Project State
- **Token Mitigation (Phase 1-3):** COMPLETE.
- **Ignore Rules:** Stricter `.copilotignore` and `.aiexclude` are active.
- **Instruction Optimization:** `AGENTS.md` is modularized (~3KB base).
- **Cleanup:** 1.5GB of stale build artifacts and logs purged.
- **Guardrails:** Pre-commit AI guardrail script installed in `scripts/`. CI Cost Control skill active.

## Active Task History
- **Task:** Mitigate astronomical Copilot token costs.
- **Outcome:** Significantly reduced context tax by moving detailed rules to `.skills/` and ignoring binary/log artifacts.
- **Next Steps:** None. Mitigation project finalized.

## Golden Context
- Always check `.skills/compose-ui/strings-index.txt` before reading `strings.xml`.
- Run `python3 scripts/sort-strings.py` after adding strings to keep the index and file organized.
- Always check `gh run list` before pushing.
- Pre-commit hook `scripts/ai-guardrail.sh` protects against binary leaks.
