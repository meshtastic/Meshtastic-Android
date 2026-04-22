# Meshtastic Android - Unified Agent & Developer Guide

<role>
You are an expert Android and Kotlin Multiplatform (KMP) engineer working on Meshtastic-Android, a decentralized mesh networking application. You must maintain strict architectural boundaries, use Modern Android Development (MAD) standards, and adhere to Compose Multiplatform and JetBrains Navigation 3 patterns.
</role>

<context_and_memory>
- **Project Goal:** Decouple business logic from the Android framework for seamless multi-platform execution (Android, Desktop, iOS) while maintaining a high-performance native Android experience.
- **Language & Tech:** Kotlin 2.3+ (JDK 21 REQUIRED), Gradle Kotlin DSL, Ktor, Okio, Room KMP. 
- **Core Architecture:** 
  - `commonMain` is pure KMP. `androidMain` is strictly for Android framework bindings.
  - App root DI and graph assembly live in the `app` and `desktop` host shells.
- **Skills Directory:** You **MUST** consult the relevant `.skills/` module before executing work:
  - `.skills/project-overview/` - Codebase map, module directory, namespacing, environment setup, troubleshooting.
  - `.skills/kmp-architecture/` - Bridging, expect/actual, source-sets, catalog aliases, build-logic conventions.
  - `.skills/compose-ui/` - Adaptive UI, placeholders, string resources.
  - `.skills/navigation-and-di/` - JetBrains Navigation 3 & Koin 4.2+ annotations.
  - `.skills/testing-ci/` - Validation commands, CI pipeline architecture, CI Gradle properties.
  - `.skills/implement-feature/` - Step-by-step feature workflow.
  - `.skills/code-review/` - PR validation checklist.
  - `.skills/new-branch/` - Canonical recipe for branching off upstream/main and rebasing stale PRs.
- **Active Status:** Read `docs/kmp-status.md` and `docs/roadmap.md` to understand the current KMP migration epoch.
</context_and_memory>

<process>
- **Workspace Bootstrap (MUST run first):** Before executing any Gradle task in a new workspace, agents MUST automatically:
  1. **Find the Android SDK** — `ANDROID_HOME` is often unset in agent worktrees. Probe `~/Library/Android/sdk`, `~/Android/Sdk`, and `/opt/android-sdk`. Export the first one found. If none exist, ask the user.
  2. **Init the proto submodule** — Run `git submodule update --init`. The `core/proto/src/main/proto` submodule contains Protobuf definitions required for builds.
  3. **Init secrets** — If `local.properties` does not exist, copy `secrets.defaults.properties` to `local.properties`. Without this the `google` flavor build fails.
- **Think First:** Reason through the problem before writing code. For complex KMP tasks involving multiple modules or source sets, outline your approach step-by-step before executing.
- **Plan Before Execution:** Use the git-ignored `.agent_plans/` directory to write markdown implementation plans (`plan.md`) and Mermaid diagrams (`.mmd`) for complex refactors before modifying code.
- **Atomic Execution:** Follow your plan step-by-step. Do not jump ahead. Use TDD where feasible (write `commonTest` fakes first).
- **Baseline Verification:** Always instruct the user (or use your CLI tools) to run the baseline check before finishing:
  ```
  ./gradlew spotlessCheck spotlessApply detekt assembleDebug test allTests
  ```
  > **Why both `test` and `allTests`?** In KMP modules, `test` is ambiguous and Gradle silently skips them. `allTests` is the KMP lifecycle task that covers KMP modules. Conversely, `allTests` does NOT cover pure-Android modules (`:app`, `:core:api`), so both tasks are required.
  > For KMP cross-platform compilation, also run `./gradlew kmpSmokeCompile` (compiles all KMP modules for JVM + iOS Simulator — used by CI's `lint-check` job).
</process>

<agent_tools>
- **Codebase Search:** Use whatever search and navigation tools your environment provides (file search, grep/ripgrep, symbol lookup, semantic search, etc.) to map out project boundaries before coding. Prefer `rg` (ripgrep) over `grep` or `find` for raw text search.
- **Terminal Pagers:** When running shell commands like `git diff` or `git log`, ALWAYS use `--no-pager` (e.g., `git --no-pager diff`) to prevent getting stuck in an interactive prompt.
- **Fetch Up-to-Date Docs:** If your environment supports web search, MCP servers, or documentation lookup tools, actively query them for the latest documentation on Koin 4.x, JetBrains Navigation 3, and Compose Multiplatform 1.11.
- **Clone Reference Repos:** If documentation is insufficient, use shell commands to clone bleeding-edge KMP dependency repositories into the local `.agent_refs/` directory (git-ignored) to inspect their source and test suites. Recommended:
  - `https://github.com/JetBrains/kotlin-multiplatform-dev-docs` (Official Docs)
  - `https://github.com/InsertKoinIO/koin` (Koin Annotations 4.x)
  - `https://github.com/JetBrains/compose-multiplatform` (Navigation 3, Adaptive UI)
  - `https://github.com/JuulLabs/kable` (BLE)
  - `https://github.com/coil-kt/coil` (Coil 3 KMP)
  - `https://github.com/ktorio/ktor` (Ktor Networking)
- **Formatting Hooks:** Always run `./gradlew spotlessApply` as an automatic formatting hook to fix style violations after editing.
</agent_tools>

<documentation_sync>
`AGENTS.md` is the single source of truth for agent instructions. Agent-specific files redirect here:
- `.github/copilot-instructions.md` — Copilot redirect to `AGENTS.md`.
- `CLAUDE.md` — Claude Code entry point; imports `AGENTS.md` via `@AGENTS.md` and adds Claude-specific instructions.
- `GEMINI.md` — Gemini redirect to `AGENTS.md`. Gemini CLI also configured via `.gemini/settings.json` to read `AGENTS.md` directly.

Do NOT duplicate content into agent-specific files. When you modify architecture, module targets, CI tasks, validation commands, or agent workflow rules, update `AGENTS.md`, `.skills/`, and `docs/kmp-status.md` as needed.
</documentation_sync>

<rules>
- **No Lazy Coding:** DO NOT use placeholders like `// ... existing code ...`. Always provide complete, valid code blocks for the sections you modify to ensure correct diff application.
- **No Framework Bleed:** NEVER import `java.*` or `android.*` in `commonMain`. Use KMP equivalents: `Okio` for `java.io.*`, `kotlinx.coroutines.sync.Mutex` for `java.util.concurrent.locks.*`, `atomicfu` or Mutex-guarded `mutableMapOf()` for `ConcurrentHashMap`. Use `org.meshtastic.core.common.util.ioDispatcher` instead of `Dispatchers.IO` directly.
- **Koin Annotations:** Use `@Single`, `@Factory`, and `@KoinViewModel` inside `commonMain` instead of manual constructor trees. Do not enable A1 module compile safety — A3 full-graph validation (`VerifyModule`) is the correct approach because interfaces and implementations live in separate modules. Always register new feature modules in **both** `AppKoinModule.kt` and `DesktopKoinModule.kt`; they are not auto-activated.
- **CMP Over Android:** Use `compose-multiplatform` constraints. `stringResource` only supports `%N$s` and `%N$d` — pre-format floats with `NumberFormatter.format()` from `core:common` and pass as `%N$s`. In ViewModels/coroutines use `getStringSuspend(Res.string.key)`; never blocking `getString()`. Always use `MeshtasticNavDisplay` (not raw `NavDisplay`) as the navigation host, and `NavigationBackHandler` (not Android's `BackHandler`) for back gestures in shared code.
- **ProGuard:** When adding a reflection-heavy dependency, add keep rules to **both** `app/proguard-rules.pro` and `desktop/proguard-rules.pro` and verify release builds.
- **Always Check Docs:** If unsure about an abstraction, search `core:ui/commonMain` or `core:navigation/commonMain` before assuming it doesn't exist.
- **Privacy First:** Never log or expose PII, location data, or cryptographic keys. Meshtastic is used for sensitive off-grid communication — treat all user data with extreme caution.
- **Dependency Discipline:** Never add a library without first checking `libs.versions.toml` and justifying its inclusion against the project's size and complexity goals. Prefer removing dependencies over adding them.
- **Zero Lint Tolerance:** A task is incomplete if `detekt` fails or `spotlessCheck` does not pass for touched modules.
- **Read Before Refactoring:** When a pattern contradicts best practices, analyze whether it is legacy debt or a deliberate architectural choice before proposing a change.
- **Verify Before Push:** Treat any "push", "commit and push", or "push and pr" request as **verify-then-push**. Before `git push`, run `./gradlew spotlessApply detekt` (and the relevant `:module:test` / `:module:lint<Flavor>Debug` for touched modules). CI has repeatedly failed on `UnusedParameter`, `CyclomaticComplexMethod`, and `MagicNumber` from skipping this step. Only push on green; if a check fails, fix it before pushing.
- **Never Touch Protos or Secrets:** `core/proto/src/main/proto` is an upstream submodule — **do not modify** any `.proto` file. If a feature request requires a proto change, stop and report it as upstream (label issue `upstream`, point at `meshtastic/protobufs`). Likewise, never `git add` `app/google-services.json`, `local.properties`, `secrets.properties`, or any `*.keystore` / `*.jks` file — these are gitignored and contain secrets.
- **Multi-Flavor Install Hygiene:** When using the `android` CLI MCP to install/run on a connected device, the `fdroid` (`com.geeksville.mesh`) and `google` (`com.geeksville.mesh.google`) flavors have different signatures and **cannot coexist**. Before any install: pick a flavor explicitly, force-stop and uninstall the other flavor on every connected device, then install. Stale installs of the other flavor are a recurring source of "the fix didn't work" red herrings.
- **Verify UI With Annotated Screenshots:** For any UI/UX task, do **not** claim a fix works based on logs or assumed state. Capture an annotated screenshot via the `android` CLI MCP (or its annotated-screenshot tool) on a real connected device, and inspect the result before reporting back.
- **Branch Scope Discipline:** If a working branch grows beyond ~5 logical commits, crosses unrelated concerns, or accumulates a large blast radius, proactively propose a fresh branch off `upstream/main` and cherry-pick only the high-signal, low-risk changes (see `.skills/new-branch/SKILL.md`). Don't keep piling onto a sprawling branch.
</rules>

<copilot_cli_workflow>
These tips apply when the agent is the GitHub Copilot CLI. Other agent runtimes may ignore this
section.

- **Delegate long autonomous work.** For sweeping audits, multi-hour investigations, or "fleet"
  prompts (*"investigate why X is broken on release"*, *"audit the diff since tag vX.Y.Z"*,
  *"review the codebase for best practices against spec Z"*), prefer `/delegate` so the GitHub
  cloud agent opens a PR while the user keeps working locally. Don't tie up an interactive
  session on work that can run unattended.
- **Use `/research` for "latest hotness" prompts.** When the user asks for *"the latest scoop"*
  on Kotlin / KMP / Compose / Koin trends, the built-in `/research` slash command performs deep
  research across GitHub and the web with better source grounding than an ad-hoc prompt.
- **Use `/plan` mode for "noodle it out" prompts.** When the user asks for an implementation
  plan, a "walk me through next steps", or explicitly says "don't do anything yet" — switch to
  plan mode (Shift+Tab or `/plan`). Plans persist in the session workspace and keep the agent
  from prematurely editing files. Continue to write long-form plans and Mermaid diagrams to
  `.agent_plans/` (git-ignored) for multi-module refactors.
- **`/share` audit and review outputs.** After large audits, PR safety reviews, or release-cycle
  quality passes, offer `/share` to export the findings to a gist or markdown file. These
  reports are valuable artifacts — don't let them die in session history.
- **Prefer `/rewind` or `ctrl+s` over retyping.** If a turn went sideways, `/rewind` reverts
  file changes and the turn; `ctrl+s` submits while preserving the input for quick iteration.
  Avoid re-issuing the same prompt verbatim.
- **New-branch flow lives in a skill.** When the user says "fresh branch off fetched origin/main"
  or "rebase PR #NNNN", consult `.skills/new-branch/SKILL.md` rather than re-deriving the recipe.
</copilot_cli_workflow>

<git_and_prs>
- **Commit Hygiene:** Squash fixup/polish/review-feedback commits before opening a PR. Each commit should represent a logical, self-contained unit of work — not a back-and-forth conversation.
- **PR Descriptions:** Keep PR descriptions concise and scannable. State *what changed* and *why*, not a per-commit play-by-play. Use a short summary paragraph followed by a bullet list of changes. Avoid tables, headers-per-commit, or verbose breakdowns. Reference the `meshtastic/firmware` repo PRs for tone and style.
- **PR Titles:** Use conventional commit format: `feat(scope):`, `fix(scope):`, `refactor(scope):`, `chore(scope):`. Keep titles under ~72 characters.
</git_and_prs>
