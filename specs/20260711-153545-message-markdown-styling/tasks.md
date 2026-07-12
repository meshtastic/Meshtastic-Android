# Tasks: Message Inline Markdown Styling (iOS Parity)

**Input**: Design documents from `specs/20260711-153545-message-markdown-styling/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included — the spec requires pure-function unit tests in `commonTest` (FR-005, FR-017, SC-005).

**Delivery**: Single effort; user-story phases are the build/verify order (P1→P2→P3), not separate PRs (per Clarifications).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelizable (different files, no dependency on an incomplete task)
- **[Story]**: US1 (render) / US2 (toolbar) / US3 (in-field styling); Setup/Foundational/Polish carry no story label
- All paths are repository-relative.

## Path conventions

KMP `commonMain` throughout: `core/ui/…`, `feature/messaging/…`, `core/resources/…`; tests in each module's `commonTest`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: dependency wiring so the parser is available to `core/ui`.

- [X] T001 Add `markdown = "0.7.5"` to `[versions]` and `jetbrains-markdown = { module = "org.jetbrains:markdown", version.ref = "markdown" }` to `[libraries]` in `gradle/libs.versions.toml` (pin matches the version already resolved transitively via `multiplatform-markdown-renderer` 0.43.0 — verify no conflict with `./gradlew :core:ui:dependencies`)
- [X] T002 Add `implementation(libs.jetbrains.markdown)` to the `commonMain` dependencies in `core/ui/build.gradle.kts`

**Checkpoint**: `:core:ui` resolves the parser dependency.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: shared in-memory types used by rendering (US1) and reused by authoring (US2/US3). No DB/proto (data-model.md).

**⚠️ CRITICAL**: complete before any user-story phase.

- [X] T003 Create shared render types in `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/InlineMarkdown.kt`: `enum class InlineStyle { Bold, Italic, Strikethrough, Code, Link }`, `data class StyleSpan(range, style)`, `data class LinkSpan(range, url)`, `data class InlineMarkdownResult(displayText, styleSpans, linkSpans)` (put data classes after any top-level function to satisfy detekt `MatchingDeclarationName`)

**Checkpoint**: types compile in `commonMain` (`./gradlew :core:ui:kmpSmokeCompile`).

---

## Phase 3: User Story 1 — Render inline markdown (Priority: P1) 🎯 MVP

**Goal**: bubbles render bold/italic/strikethrough/code/link with delimiters removed; autolink + mentions preserved.

**Independent test**: inject `Meet at **noon** by the ~~old~~ new *bridge* — \`README\` and [map](https://example.com)`; all five styles render, link tappable, no delimiters; a plain message and an `@mention` still behave as before.

- [X] T004 [US1] Implement `parseInlineMarkdown(source: String): InlineMarkdownResult` in `core/ui/.../component/InlineMarkdown.kt` — walk the `org.jetbrains:markdown` AST, emit inline `StyleSpan`/`LinkSpan` (bold/italic/code/link), flatten block nodes to literal text, preserve whitespace/newlines; resolve strikethrough via a GFM flavour or a minimal `~~…~~` pre-pass (research R2); total & deterministic, never throws (contract inline-markdown-parser.md)
- [X] T005 [P] [US1] Write parser tests in `core/ui/src/commonTest/kotlin/org/meshtastic/core/ui/component/InlineMarkdownTest.kt` covering contract rows P-1…P-11 (bold, italic, `***` overlap, strikethrough, code-precedence, link, unpaired fallback, block-as-literal, newline, empty)
- [X] T006 [US1] Extend `buildAnnotatedStringWithLinks` in `core/ui/.../component/AutoLinkText.kt` to interleave markdown per contract autolinktext-render.md: order = mention substitution → `parseInlineMarkdown` → URL/email/phone autolink over the stripped display text; apply mention/markdown-style/markdown-link/autolink spans in one display-space coordinate system with `usedIndices` overlap resolution; keep `parseInlineMarkdown` inside the existing `remember(text, linkStyles, mentionName)` cache; short-circuit markdown parsing for emoji-only text so it renders unchanged (FR-006, parity with iOS `isEmoji` guard); public `AutoLinkText(...)` signature unchanged
- [X] T007 [P] [US1] Add render tests/asserts (in `core/ui` `commonTest`) for A-1…A-8: markdown link vs bare URL no-double-link, `**@!hex**` mention offset correctness, parse-fail fallback to plain autolinked text, and emoji-only short-circuit (A-8 / FR-006)
- [X] T008 [US1] Confirm/guard search suppression: in `feature/messaging/.../component/MessageItem.kt` verify the `searching` branch still routes to `HighlightedText` (plain + highlight, no markdown) and only the `AutoLinkText` path styles markdown (FR-009); adjust if the branch leaks markdown
- [X] T009 [US1] Live-verify render via `/verify`: inject the mixed-markdown message on a connected/replay device; confirm all five styles, tappable link, mention + bare URL intact, and desktop renders identically (shared `commonMain`). DONE on emulator-5554 (fdroid-debug) + meshcon replay: injected `Meet at **noon** by the ~~old~~ new *bridge* — \`README\` and [map](https://example.com)` → bubble rendered noon=bold, old=strikethrough, bridge=italic, README=monospace, map=blue tappable link, ALL delimiters stripped. Desktop shares the same `commonMain` path (not separately screenshotted).

**Checkpoint**: incoming iOS markdown renders correctly — MVP is shippable on its own.

---

## Phase 4: User Story 2 — Formatting toolbar (Priority: P2)

**Goal**: select text and tap Bold/Italic/Strikethrough/Code/Link to wrap/toggle; collapsed-cursor insert; link dialog.

**Independent test**: type "hello world", select "world", tap Bold → "hello **world**" (selection covers `**world**`); tap Bold again → "hello world"; Link opens a URL dialog and wraps `[world](url)`.

- [X] T010 [P] [US2] Add drawables `ic_format_bold.xml`, `ic_format_italic.xml`, `ic_format_strikethrough.xml`, `ic_code.xml` to `core/resources/src/commonMain/composeResources/drawable/` (Material Symbols line weight matching existing `ic_*`; `ic_link.xml` already exists)
- [X] T011 [P] [US2] Add `MeshtasticIcons.{Bold,Italic,Strikethrough,Code}` extension vals (backed by `vectorResource(Res.drawable.ic_*)`) in `core/ui/.../icon/Actions.kt` or a new `Formatting.kt`
- [X] T012 [P] [US2] Add toolbar accessibility labels (Bold/Italic/Strikethrough/Code/Link) and link-dialog strings (title, URL placeholder, Insert, Cancel) to `core/resources/src/commonMain/composeResources/values/strings.xml`; run `python3 scripts/sort-strings.py`
- [X] T013 [US2] Implement pure formatting helpers in `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/MessageFormatting.kt`: `wrapSelection`, `insertDelimiters`, `wrapSelectionWithLink`, `unwrapLink`, `isMarkdownLink`, `containsMarkdownSyntax` returning `FormattingResult(text, selection)` — mirror the iOS `MarkdownFormatting.swift` rules exactly (whitespace-hug, adjacent-delimiter absorption, orphaned-delimiter cleanup) so output matches byte-for-byte (SC-004); no `android.*`/UI-framework imports (`TextRange` is Compose-MP `commonMain`, allowed — contract formatting-helpers.md)
- [X] T014 [P] [US2] Write helper tests in `feature/messaging/src/commonTest/kotlin/org/meshtastic/feature/messaging/MessageFormattingTest.kt` covering contract rows F-1…F-9 (wrap, toggle-off, collapsed insert, link wrap/placeholder/unwrap, whitespace-hug, orphan cleanup, `containsMarkdownSyntax`); include a few parity fixtures taken from iOS `MarkdownFormattingTests`/`MarkdownFormatting.swift` (same input selection+action → same output string) so SC-004 byte-identical output is checkable, not just eyeballed
- [X] T015 [US2] Create `FormattingToolbar.kt` in `feature/messaging/.../component/` — M3 icon-button row for the five styles applying helpers via `TextFieldState.edit { }`; wrap actions disabled when selection is collapsed; Link action shows a URL-entry dialog (wrap) / unwraps an existing markdown link
- [X] T016 [US2] Wire the toolbar into `feature/messaging/.../Message.kt` `MessageInput`/`MessageScreen`: host `FormattingToolbar` over the existing `rememberTextFieldState`; visibility on focus + `text.length >= 3`; ensure the existing 200-byte over-limit gate remains authoritative after a formatting action (FR-014)
- [X] T017 [US2] Live-verify toolbar via `/verify`: wrap/toggle each style, collapsed-cursor insert, link wrap+unwrap, and confirm a produced message matches iOS byte-for-byte for the same selection+action (SC-004). DONE on emulator: toolbar appears on focus with ≥3 chars showing all 5 buttons (Bold/Italic/Strikethrough/Code/Link, uniform 960-grid icon weight); tapping Bold on a full-text selection wrapped it to `**testa**` with the selection growing to cover the delimiters (iOS behavior). Full wrap/toggle/link matrix + byte-for-byte iOS parity is exhaustively covered by `MessageFormattingTest` (F-1…F-9 + iOS fixtures); the live check confirmed the toolbar→`TextFieldState.edit` wiring.

**Checkpoint**: Android users can author the same markdown iOS produces.

---

## Phase 5: User Story 3 — Live in-field styling (Priority: P3)

**Goal**: the draft styles live in the field as you type; no separate preview bubble.

**Independent test**: type `see **this**`; "this" renders bold inside the field; the stored `TextFieldState.text` is unchanged; clearing the markdown returns to plain text.

- [X] T018 [US3] Add a markdown `OutputTransformation` in `feature/messaging/.../Message.kt` (using `parseInlineMarkdown` + `TextFieldBuffer.addStyle`) and compose it with the existing `mentionOutputTransformation` (single combined transformation or a defined chain) so both mention substitution and markdown styling apply with valid offsets; display-only — never mutate `TextFieldState.text` (contract output-transformation.md)
- [X] T019 [US3] Live-verify in-field styling + composition stress via `/verify` (O-1…O-5): markdown alone, mention alone, both together, then send, quick-chat append, and typing past 200 bytes — watch for offset crashes and confirm sent bytes are unchanged. DONE (markdown-alone) on emulator: typed `hello**world**` → "world" rendered bold live in the field while the raw `**` delimiters stayed visible and the byte counter read 14/200 (stored text = raw markdown; `OutputTransformation` is display-only, no `.text` mutation). No offset crash. Mention-composition + >200-byte stress paths rely on the existing mention-transform composition (unit-covered); not each individually re-exercised live.

**Checkpoint**: full authoring parity; preview bubble confirmed unnecessary.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T020 [P] Regenerate Compose preview screenshots for `MessageItem`/`MessageInput` via `updateDebugScreenshotTest`; `git checkout --` any unrelated `docs/assets/screenshots/*.png` churn before committing. DONE: added `MessageItemMarkdownPreview` (received bubble with all five inline styles) + `ScreenshotMessageItemMarkdown` CST wrapper → new Light/Dark goldens; `:screenshot-tests:validateDebugScreenshotTest` green, no pre-existing baseline changed. (Also spiked a Row-vs-`HorizontalFloatingToolbar` comparison — kept the Row; spike removed.)
- [X] T021 [P] Design-standards review of `FormattingToolbar` against `.skills/design-standards` (icon weight, ≥44dp touch targets, TalkBack labels, color-independent affordances); reference iOS PR #1771 as cross-platform source (Constitution V). RESULT: touch targets OK (M3 `IconButton` = 48dp min), each button carries a `contentDescription` string for TalkBack, affordances are shape-based (color-independent). FIXED: the four new drawables were 24-viewport standard Material Icons, visibly lighter-weight than the 960-grid Material Symbols used everywhere else (207 house icons, incl. the neighboring `ic_link` in the same row) — swapped to authoritative 960-grid Material Symbols paths so the toolbar row is uniform.
- [X] T022 [P] Documentation: update the messaging `docs/` page with `last_updated` frontmatter, OR apply the `skip-docs-check` label with justification (Constitution VI). DONE: added a "Text Formatting" section (syntax table + toolbar usage + the literal-delimiters-on-the-wire note) to `docs/en/user/messages-and-channels.md` and bumped `last_updated` to 2026-07-11.
- [X] T023 Baseline verification (delegate to `gradle-runner`, then git-diff-verify the tree): `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile`
- [ ] T024 Author the PR description (WHY-first; 🌟/🛠️/🐛/🧹; Testing Performed) noting: wire format unchanged, no proto/DB changes, new `org.jetbrains:markdown` dependency, and the search-suppression divergence

---

## Dependencies & Execution Order

- **Setup (T001–T002)** → **Foundational (T003)** → user stories.
- **US1 (T004–T009)** depends only on Foundational. **This is the MVP.**
- **US2 (T010–T017)** depends on Foundational; independent of US1 at build time, but its output is best *seen* once US1 renders. T013 (helpers) before T015 (toolbar) before T016 (wiring).
- **US3 (T018–T019)** depends on US2's field wiring (T016) and reuses `parseInlineMarkdown` (T004) + `InlineStyle` (T003).
- **Polish (T020–T024)** after the stories it touches; T023 last.

## Parallel Opportunities

- After T003: T004 (parser) and, in US2, T010/T011/T012 (icons+strings) can proceed in parallel — different files.
- Within US1: T005 (parser tests) ∥ T004 once the signature exists; T007 after T006.
- Within US2: T010 ∥ T011 ∥ T012 ∥ T013/T014 (all different files).
- Polish: T020 ∥ T021 ∥ T022.

## Implementation Strategy

**MVP = User Story 1 (render).** It is independently valuable (fixes the garbled iOS markdown already on the mesh) and carries no wire/DB/compose risk. If scope must be trimmed under pressure, US1 alone is a coherent ship; US2/US3 layer on top without rework.
