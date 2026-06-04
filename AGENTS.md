# Meshtastic Android - Unified Agent & Developer Guide

<role>
You are an expert Android/KMP engineer. Maintain architectural boundaries, use MAD standards, and adhere to Compose Multiplatform + Navigation 3.
</role>

<context_and_memory>
- **Project Goal:** Decouple business logic from Android for multi-platform (Android, Desktop, iOS).
- **Tech:** Kotlin 2.3+ (JDK 21), Ktor, Okio, Room KMP, Koin 4.2+.
- **Agent Memory:** Skim the top (most recent) entry of `.agent_memory/session_context.md` for current state — it is capped at ~5 entries; older handovers live in `session_context.archive.md` (read only if you need historical detail).
- **Skills Directory (CONSULT THESE FIRST):** 
  - `.skills/project-overview/` - Codebase map, namespacing, **Bootstrap Steps**.
  - `.skills/kmp-architecture/` - Expect/actual, source-sets, conventions.
  - `.skills/compose-ui/` - Adaptive UI, **String Resources (consult strings-index.txt first)**.
  - `.skills/navigation-and-di/` - Navigation 3 & Koin annotations.
  - `.skills/testing-ci/` - Validation commands, **CI Architecture**.
  - `.skills/ci-cost-control/` - **CI Budgeting & Monitoring**.
  - `.skills/implement-feature/` - Feature workflow.
  - `.skills/code-review/` - **PR & Commit Hygiene**, validation checklist.
  - `.skills/new-branch/` - Branching and rebasing recipes.
  - `.skills/speckit/` - **Spec Kit SDD workflow**, slash commands, constitution, feature specs.
</context_and_memory>

<process_essentials>
- **Think First:** Read only what you need. Consult indices (like `strings-index.txt`) before reading large files.
- **Hygiene:** Run `python3 scripts/sort-strings.py` after adding new string resources to maintain organization and update the index.
- **Memory Persistence:** Add a new entry to the TOP of `.agent_memory/session_context.md` at the end of every session or major task. Keep it capped at ~5 entries — move anything older to `session_context.archive.md`.
- **Bootstrap First:** Run the mandatory bootstrap steps in `.skills/project-overview/SKILL.md` before any build.
- **Plan Before Execution:** Use `.agent_plans/` (git-ignored) for complex refactors.
- **Baseline Verification:** Always run: `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests`
</process_essentials>

<rules>
- **Token Hygiene:** NEVER read binary files (PNG, MP3, etc.) or large non-code resources unless essential. Use file paths to reason about assets.
- **Context Discipline:** Limit your context to relevant modules. Do not "vacuum" the entire codebase for localized fixes.
- **No Lazy Coding:** DO NOT use placeholders like `// ... existing code ...`. Provide complete, valid code blocks.
- **No Framework Bleed:** NEVER import `java.*` or `android.*` in `commonMain`. Use KMP equivalents (Okio, Mutex, atomicfu).
- **CMP Over Android:** Use `compose-multiplatform` constraints. Pre-format floats with `NumberFormatter.format()`. Use `MeshtasticNavDisplay` and `NavigationBackHandler`.
- **Zero Lint Tolerance:** Task is incomplete if `detekt` or `spotlessCheck` fails.
- **Verify Before Push:** Treat any "push" as verify-then-push. CI has failed repeatedly due to skipped local checks.
- **Never Touch Protos or Secrets:** `core/proto` is an upstream submodule. Secrets are git-ignored.
- **Privacy First:** Never log or expose PII, location, or cryptographic keys.
</rules>

<documentation_sync>
`AGENTS.md` is the source of truth for rules and principles. `.github/copilot-instructions.md` provides a quick-reference subset optimized for Copilot sessions (build commands, task naming, conventions). `CLAUDE.md` and `GEMINI.md` redirect here.
</documentation_sync>

<!-- SPECKIT START -->
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->
