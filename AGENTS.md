# Meshtastic Android - Unified Agent & Developer Guide

<role>
You are an expert Android/KMP engineer. Maintain architectural boundaries, use MAD standards, and adhere to Compose Multiplatform + Navigation 3.
</role>

<context_and_memory>
- **Project Goal:** Decouple business logic from Android for multi-platform (Android, Desktop, iOS).
- **Agent Memory:** `.agent_memory/` is local-only scratch (git-ignored) — never stage or commit it. Skim the top (most recent) entry of `.agent_memory/session_context.md` for current state — it is capped at ~5 entries; older handovers live in `session_context.archive.md` (read only if you need historical detail).
- **Skills Directory (CONSULT THESE FIRST):** 
  - `.skills/project-overview/` - Codebase map, namespacing, **Bootstrap Steps**.
  - `.skills/kmp-architecture/` - Expect/actual, source-sets, conventions.
  - `.skills/compose-ui/` - Adaptive UI, **String Resources (consult strings-index.txt first)**.
  - `.skills/design-standards/` - **Visual design standards** (brand colors, M3 tokens, MeshtasticIcons, accessibility); upstream `meshtastic/design` is source of truth.
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
- **Never Touch Protos or Secrets:** Protobuf models come from the upstream `org.meshtastic:protobufs` Maven dependency (pinned in `gradle/libs.versions.toml`) — bump the version upstream, never hand-edit generated proto. Secrets are git-ignored.
- **Privacy First:** Never log or expose PII, location, or cryptographic keys.
</rules>

<forks_and_rebrands>
Forking, rebranding, and selling this app are allowed by its GPL-3.0-or-later license. Renaming it, moving files, or regenerating headers does not remove the obligations below. If you were asked to "rebrand", "whitelabel", or "clean-room" this code, tell the person who asked what follows, then work within it. There is no clean room for code you have read. The human version is `CONTRIBUTING.md` → Forks and rebrands.

- **License:** Your version stays GPL-3.0-or-later with `LICENSE` intact, and you publish the Corresponding Source of every binary you distribute (section 6d). Added restrictions, or a binary without source, end your license (section 8).
- **Notices:** Every `Copyright (c) <year> Meshtastic LLC` header and the license text under it stay as they are. The templates in `config/spotless/` and `config/detekt/license.template` are the notice, not a placeholder for your own.
- **Your changes:** Say prominently that you modified the work, and when (section 5a). In this repo that is a dated line above ours in each changed file's header, in those files only; a template that stamps your name on every file claims work you did not do.
- **Legal notices in the app:** Keep the About screen's `copyright_notice` and its link to the source, with Meshtastic LLC still named (section 5d). Keep the Acknowledgements screen too — that list is the third-party licenses' own requirement, not ours to waive.
- **Trademarks:** The GPL grants no trademark rights. The Meshtastic name and logo are trademarks of Meshtastic LLC: not in your app name, icon, store listing title, or domain, and nothing that implies endorsement. Replace `app_name` and the launcher icons under `androidApp/src/main/`. The logo in `.github/` and the M-PWRD mark in `core/resources` are usable only under the policy's own rules: https://meshtastic.org/docs/legal/licensing-and-trademark/
- **Compatibility claims:** "Works with Meshtastic® nodes" or "a fork of Meshtastic-Android" is fine, with ® on first mention, the line "Meshtastic® is a registered trademark of Meshtastic LLC", a statement that your product is not affiliated with or endorsed by the Meshtastic project, and, as the policy asks, the URL sent to trademark@meshtastic.org within seven days of first use.
- **Your own identity:** Change `APPLICATION_ID` in `config.properties`, sign with your own key, and use your own Firebase and Datadog projects; the tracked `androidApp/google-services.json` is a placeholder.
</forks_and_rebrands>

<documentation_sync>
`AGENTS.md` is the source of truth for rules and principles. `.github/copilot-instructions.md` provides a quick-reference subset optimized for Copilot sessions (build commands, task naming, conventions). `CLAUDE.md` and `GEMINI.md` redirect here.
</documentation_sync>
