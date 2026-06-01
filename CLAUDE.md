# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**[AGENTS.md](AGENTS.md)** is the source of truth for rules, architecture, and workflow; the `.skills/` modules hold the detail. This file does not duplicate them — it adds Claude-specific workflow notes and the few commands a fresh session needs immediately.

@AGENTS.md

## Claude-Specific Instructions

- **Think First:** Outline step-by-step reasoning inside `<thinking>` tags before writing code or shell commands.
- **Skills:** Load only the `.skills/` module relevant to the current task — don't read them all. Start with `.skills/project-overview/SKILL.md` (codebase map, bootstrap, troubleshooting).
- **Plan Mode:** Use it for changes spanning multiple modules; write plans to `.agent_plans/` (git-ignored). Skip the Copilot-CLI `<copilot_cli_workflow>` guidance in AGENTS.md — it doesn't apply to Claude Code.
- **Delegate to keep context lean** (this is a 20+ module KMP repo):
  - **Broad searches** ("where is X used", "find all implementers of Y") → dispatch the `Explore` subagent so file dumps stay out of the main context; you get back the conclusion.
  - **Gradle builds/tests/lint** → dispatch the `gradle-runner` subagent. A full `assembleDebug`/`allTests` log is thousands of lines; the subagent returns only pass/fail + failing tests. Don't run heavy `./gradlew` tasks inline.
  - **Symbol navigation** ("where is this defined", "who calls this", "find implementers") → use the `LSP` tool (`goToDefinition` / `findReferences` / `goToImplementation`) instead of reading whole files. `kotlin-language-server` is configured via `.github/lsp.json`.
- **Big files are guarded, not free:** `.claude/settings.json` denies the Crowdin locale `strings.xml` files and prompts before reading the base `strings.xml`, `firmware_releases.json`, `emoji-data.json`, and `flatpak-sources.json`. For strings, consult `.skills/compose-ui/strings-index.txt` instead of the raw file.

## Quick Reference

JDK 21 is required. **Bootstrap before any Gradle task** (don't wait to be told) — full details in `.skills/project-overview/SKILL.md`:
```bash
[ -z "$ANDROID_HOME" ] && export ANDROID_HOME="$HOME/Library/Android/sdk"  # often unset in agent workspaces
git submodule update --init                                                # proto submodule; builds fail without it
[ -f local.properties ] || cp secrets.defaults.properties local.properties # google flavor fails without it
```

**Baseline verification — run before every push** (CI has failed on skipped local checks):
```bash
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests
```
Both `test` and `allTests` are required: `allTests` covers KMP modules (where the bare `test` task is ambiguous and silently skips), `test` covers pure-Android/JVM modules. Add `kmpSmokeCompile` when touching a KMP module. After adding string resources, run `python3 scripts/sort-strings.py`. Change-type matrix and CI architecture: `.skills/testing-ci/SKILL.md`.

**Single test:**
```bash
./gradlew :feature:messaging:allTests                              # one KMP module
./gradlew :androidApp:testFdroidDebugUnitTest                      # one Android/JVM module
./gradlew :core:data:allTests --tests "*PacketHandlerTest*"        # filter to one class/method
```
