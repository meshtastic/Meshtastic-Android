# Implementation Plan: Message Inline Markdown Styling (iOS Parity)

**Branch**: `claude/apple-markdown-styling-parity-be3a63` (proposed rename: `20260711-153545-message-markdown-styling`) | **Date**: 2026-07-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/20260711-153545-message-markdown-styling/spec.md`

## Summary

Render and author lightweight **inline** markdown (bold, italic, strikethrough, inline code, links) in mesh text messages, matching iOS PR #1771. Rendering extends the shared `core/ui` `AutoLinkText` `AnnotatedString` builder with an `org.jetbrains:markdown` (intellij-markdown) AST walk that emits inline spans, interleaved with the existing mention + URL/email/phone spans. Authoring adds a Compose formatting toolbar over the existing `TextFieldState`-based compose field plus live in-field styling via `OutputTransformation` (the same mechanism already rendering `@mentions`). The mesh wire payload is unchanged — Android sends the raw delimiters exactly as iOS does. All logic lives in `commonMain`, so Android and desktop share one implementation.

## Technical Context

**Language/Version**: Kotlin 2.4.0 (JDK 25 toolchain; bytecode target JVM 21)

**Primary Dependencies**: Compose Multiplatform 1.11.1 (`foundation` provides `TextFieldState`/`OutputTransformation`), Compose Multiplatform Material3 1.11.0-alpha07, **new:** `org.jetbrains:markdown` 0.7.5 (matches the version already resolved transitively via the block `multiplatform-markdown-renderer` 0.43.0 — pinning to it avoids a catalog version conflict)

**Storage**: N/A — no database or proto changes. Markdown is parsed at render time; nothing persisted. The wire payload equals `TextFieldState.text` (raw delimiters).

**Testing**: `commonTest` (kotlin-test) for the pure parser + formatting helpers; existing Robolectric/androidHostTest harness for any Compose-touching assertions; Compose screenshot tests for `MessageItem`/`MessageInput` previews via `updateDebugScreenshotTest`.

**Target Platform**: Android (all supported API levels — no OS gate, unlike iOS's iOS-18 floor) + JVM desktop (shared `commonMain`).

**Project Type**: KMP mobile + desktop app; feature-module architecture.

**Performance Goals**: Annotated-string construction stays inside the existing `remember(text, …)` cache in `AutoLinkText`; parsing is a single AST pass per message render, not per frame. No regression to message-list scroll/paging.

**Constraints**: 200-byte message limit enforced on the raw text (delimiters count, as they are on the wire); no `java.*`/`android.*` in `commonMain`; zero detekt/spotless findings.

**Scale/Scope**: Two shared components extended/added in `core/ui`; toolbar + wiring in `feature/messaging`; 4 new drawable icons + strings in `core/resources`; one new catalog entry. ~5 code units, all `commonMain`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Kotlin Multiplatform Core** — ✅ All logic in `commonMain`. Touched source sets: `core/ui/commonMain` (renderer + parser), `feature/messaging/commonMain` (toolbar, in-field styling, field wiring), `core/resources/commonMain` (icons + strings), `commonTest` (parser + helper tests). No `androidMain`/`jvmMain` code required — `TextFieldState`, `OutputTransformation`, `AnnotatedString`, and `org.jetbrains:markdown` are all multiplatform.
- **II. Zero Lint Tolerance** — ✅ Will run `./gradlew spotlessApply spotlessCheck detekt` for `:core:ui`, `:feature:messaging`, `:core:resources`. Watch: detekt `MatchingDeclarationName` (put any enum/data classes after functions in new files); no hardcoded user-facing strings.
- **III. Compose Multiplatform UI** — ✅ Toolbar uses M3 components + `MeshtasticIcons`; no float formatting involved; no navigation changes (in-screen only, so `MeshtasticNavDisplay`/`NavigationBackHandler` N/A). Live styling uses the CMP `OutputTransformation` API already in the module.
- **IV. Privacy First** — ✅ No logging of message content; no PII/location/keys; no proto edits (wire format unchanged, no new fields). Markdown is presentation-only.
- **V. Design Standards Compliance** — ⚠️ New user-facing UI (formatting toolbar). Must check the toolbar against `.skills/design-standards` and reference upstream iOS behavior (PR #1771) as the cross-platform source. New format icons must match the MeshtasticIcons visual language (Material Symbols line weight). Recorded in Phase 1 quickstart checklist.
- **VI. Documentation Freshness** — ⚠️ User-facing behavior change (message styling + toolbar). Either update the relevant `docs/` messaging page (with `last_updated` frontmatter) or apply `skip-docs-check` with justification. Flagged as a task.
- **VII. Verify Before Push** — ✅ Local: `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile` + `python3 scripts/sort-strings.py` after adding strings. Post-push: `gh pr checks`. Delegate heavy Gradle to the `gradle-runner` subagent; git-diff-verify after (it can mutate the tree).

No unjustifiable violations. The two ⚠️ items (design review, docs) are process obligations, tracked as tasks, not architectural exceptions — Complexity Tracking is empty.

## Project Structure

### Documentation (this feature)

```text
specs/20260711-153545-message-markdown-styling/
├── spec.md              # Feature specification (+ Clarifications)
├── plan.md              # This file
├── research.md          # Phase 0 output — decisions & rationale
├── data-model.md        # Phase 1 output — in-memory span/parse model (no DB)
├── quickstart.md        # Phase 1 output — build/verify/design-check steps
├── contracts/           # Phase 1 output — function/API contracts
│   ├── inline-markdown-parser.md
│   ├── autolinktext-render.md
│   ├── formatting-helpers.md
│   └── output-transformation.md
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/
├── AutoLinkText.kt              # MODIFY: interleave markdown spans into buildAnnotatedStringWithLinks
└── InlineMarkdown.kt            # NEW: pure parser — raw String -> (display, styleSpans, linkSpans)

core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/icon/
└── Actions.kt (or new Formatting.kt)  # NEW MeshtasticIcons extension vals: Bold, Italic, Strikethrough, Code (Link exists)

core/ui/src/commonTest/kotlin/org/meshtastic/core/ui/component/
└── InlineMarkdownTest.kt        # NEW: parser + render-corpus tests

core/resources/src/commonMain/composeResources/
├── drawable/ic_format_bold.xml          # NEW
├── drawable/ic_format_italic.xml        # NEW
├── drawable/ic_format_strikethrough.xml # NEW
├── drawable/ic_code.xml                 # NEW  (ic_link.xml already exists)
└── values/strings.xml                   # MODIFY: toolbar a11y labels + link dialog strings

feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/
├── Message.kt                   # MODIFY: host toolbar; add markdown OutputTransformation (compose with mention one)
└── component/FormattingToolbar.kt  # NEW: toolbar composable + link dialog
feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/
└── MessageFormatting.kt         # NEW: pure wrap/toggle/insert/link/orphan-clean helpers (mirror iOS MarkdownFormatting.swift)

feature/messaging/src/commonTest/kotlin/org/meshtastic/feature/messaging/
└── MessageFormattingTest.kt     # NEW: helper unit tests

gradle/libs.versions.toml        # MODIFY: add `markdown = "0.7.5"` version + `jetbrains-markdown` library
core/ui/build.gradle.kts         # MODIFY: add libs.jetbrains.markdown to commonMain
```

**Structure Decision**: Rendering logic goes in `core/ui` (shared, already home to `AutoLinkText`) so desktop inherits it; authoring UI + text-mutation logic goes in `feature/messaging` (owns the compose field and its `TextFieldState`). Pure functions (`InlineMarkdown`, `MessageFormatting`) carry no `android.*`/UI-framework dependency so they unit-test in `commonTest` without a UI harness — mirroring iOS's testable `MarkdownFormatting.swift`. (`MessageFormatting` uses the Compose-Multiplatform `commonMain` value type `TextRange`, which is platform-free and `commonTest`-constructible.)

## Complexity Tracking

> No constitution violations requiring justification. Section intentionally empty.

## Phase Summary

- **Phase 0 — Research** (`research.md`): parser choice & version pin, inline-only AST strategy, offset-ordering with mentions/autolinks, `OutputTransformation` styling approach, icon/font sourcing. All prior NEEDS CLARIFICATION resolved (see Clarifications in spec + research.md).
- **Phase 1 — Design & Contracts** (`data-model.md`, `contracts/`, `quickstart.md`): the in-memory parse model (no DB), the four function/API contracts, and the build/verify/design-check runbook.
- **Phase 2 — Tasks**: produced by `/speckit.tasks` (dependency-ordered, P1→P2→P3 build order within one delivery). NOT created by this command.
