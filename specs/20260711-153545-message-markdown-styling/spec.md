# Feature Specification: Message Inline Markdown Styling (iOS Parity)

**Feature Branch**: `claude/message-markdown-styling-parity` (proposed)
**Created**: 2026-07-11
**Status**: Draft
**Input**: "Investigate the iOS message-input markdown styling feature and develop a parity spec for Android."
**Cross-Platform Reference**: meshtastic-apple PR [#1771 — Add message formatting toolbar (iOS 18+)](https://github.com/meshtastic/meshtastic-apple/pull/1771)

## Clarifications

### Session 2026-07-11

- Q: What scope should the first delivery cover? → A: All three — P1 render, P2 authoring toolbar, and P3 draft styling — in one effort (staged internally P1→P2→P3, but not split across PRs).
- Q: Which markdown parsing approach for the renderer? → A: JetBrains `org.jetbrains:markdown` (intellij-markdown) AST walk; add as a direct version-catalog + `core/ui` dependency.
- Q: How should the draft preview / live styling (P3) work? → A: Live in-field styling via Compose `OutputTransformation` (as the existing `mentionOutputTransformation` does); no separate preview bubble unless in-field styling proves insufficient.
- Q: How should markdown behave during active message search? → A: Suppress markdown styling while a search query is active — render plain text + query highlight only.
- Q: Should channel-URL (`meshtastic.org/e/#…`) links tapped inside a bubble be intercepted (as iOS does)? → A: No — out of scope; deferred to a separate story that also covers existing autolinks (default retained).

## Summary

iOS renders and lets users author lightweight **inline markdown** in mesh text messages — **bold**, *italic*, ~~strikethrough~~, `inline code`, and `[links](url)` — plus automatic linkification of URLs, email addresses, phone numbers, and postal addresses. Android today renders none of the styling: it auto-links URLs/email/phone (via `AutoLinkText`) but shows the raw delimiter characters (`**bold**`, `~~strike~~`) as literal text.

**Because iOS transmits the raw markdown delimiters over the mesh**, every Android user in a channel with an iOS 18+ user *already receives* messages full of literal asterisks and tildes today. Closing the **render** gap is therefore the higher-value, lower-risk half of parity. The **authoring** half (a formatting toolbar + live in-field styling over the compose field) matches iOS's editor.

This spec covers all three prioritized user stories — **P1 render, P2 authoring toolbar, P3 draft styling** — as a single delivery (per Clarifications). Priorities denote build/verification order within that effort, not separate PRs.

## How iOS Works (reference behavior)

Two independent halves:

**Rendering (all iOS versions).** Message bubbles render `AttributedString(markdown:options:.init(interpretedSyntax: .inlineOnlyPreservingWhitespace))` — inline styling only (no headers, lists, blockquotes, images), whitespace/newlines preserved. Parse failure falls back to plain text. Links are underlined and tinted. A derived, persisted field (`messagePayloadMarkdown`) additionally auto-linkifies detected URLs / `tel:` / `mailto:` / `maps.apple.com?address=` via `NSDataDetector`, skipping matches already inside an existing `[…](…)` link. Emoji-only messages skip markdown entirely.

**Authoring (iOS 18+ / macOS 15+ only — gated on the new selection-observing `TextEditor`).**
- A 5-button toolbar over the compose field: bold `**`, italic `*`, strikethrough `~~`, code `` ` ``, link `[](url)`. Shown when the field is focused and holds ≥3 characters; buttons disabled when there is no text selection.
- Toggle semantics: wrapping a selection that is *already* wrapped removes the delimiters; a collapsed cursor inserts an empty delimiter pair and places the caret between them. Wrapping trims whitespace so delimiters hug content, absorbs adjacent delimiter characters the selection cuts through, and cleans orphaned unpaired delimiters.
- Link button opens a URL-entry alert and wraps the selection as `[selected](url)`; re-invoking on an existing link unwraps it to plain display text.
- A **live preview** bubble renders above the input showing the styled result, but only when the draft contains recognizable markdown syntax.
- **The message put on the wire is the raw draft text with literal delimiters** — styling is presentation-only; there is no separate "formatted" payload transmitted.

## Goals

1. **Read parity (P1):** Android renders inline markdown (bold, italic, strikethrough, inline code, links) in message bubbles so messages authored on iOS — which arrive with literal delimiters — display as intended.
2. **Write parity (P2):** Android offers an equivalent formatting toolbar over the compose field so Android users can author the same styling.
3. **Preview parity (P3):** Android shows a live styled preview of the draft, matching the iOS compose experience.
4. Preserve every existing messaging behavior: autolinking, `@`-mention resolution, search highlighting, reply snippets, byte-limit enforcement, reactions.
5. Keep the rendering logic in shared `commonMain` so Android **and** desktop (and any future iOS-KMP) benefit from one implementation.

## Non-Goals

- **Block-level markdown** (headers, lists, blockquotes, tables, images, code fences). iOS is inline-only; Android matches that. The `com.mikepenz:multiplatform-markdown-renderer` already in the catalog is a *block* renderer used by docs/firmware/settings and is the **wrong tool** here — it does not compose with per-span links/mentions/selection inside a chat bubble.
- Changing the **wire format**. Android continues to send exactly what the user typed (raw delimiters), matching iOS. No new protobuf fields, no `messagePayloadMarkdown`-style transmitted payload.
- A stored/derived markdown column in the database. Autolinking already happens at render time in `AutoLinkText`; markdown parsing joins it there rather than being precomputed and persisted.
- Rich-text WYSIWYG editing (the compose field still shows raw delimiters as you type, exactly like iOS). The toolbar only inserts/removes delimiter characters.
- Reconciling the "non-markdown clients see literal `**`" tradeoff — this is inherent to matching iOS and to the firmware/other-client ecosystem, and is out of scope to change unilaterally.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Render inline markdown in received & sent messages (Priority: P1)

As an Android user in a channel with iOS users, I see **bold**, *italic*, ~~strikethrough~~, `code`, and links rendered with styling instead of raw `**`/`*`/`~~`/`` ` ``/`[](…)` characters.

**Why this priority**: Highest value, lowest risk, independently shippable. iOS 18+ users are *already* emitting markdown onto the mesh; this is a pure display fix in shared code with no wire/DB/compose changes. Delivers cross-client parity by itself.

**Independent Test**: Inject (or receive) a message `Meet at **noon** by the ~~old~~ new *bridge* — see \`README\` and [map](https://example.com)`. The bubble renders bold "noon", struck "old", italic "bridge", monospaced "README", and a tappable "map" link; no literal delimiter characters remain.

**Acceptance Scenarios**:

1. **Given** a message `**bold**`, **When** rendered in a bubble, **Then** "bold" is FontWeight.Bold and the asterisks are not shown.
2. **Given** `*italic*`, **Then** "italic" is italicized; **Given** `~~strike~~`, **Then** "strike" has line-through; **Given** `` `code` ``, **Then** "code" uses a monospace family.
3. **Given** `[label](https://example.com)`, **Then** "label" renders as a styled, tappable link to `https://example.com` and the bracket/paren syntax is not shown.
4. **Given** a bare URL, email, or phone number (no markdown syntax), **Then** existing autolink behavior is unchanged.
5. **Given** a message that mixes a markdown link and a bare URL and an `@!<hex>` mention, **Then** all three resolve correctly with no overlapping or mis-offset spans.
6. **Given** malformed/unpaired delimiters (`**oops`, `a * b * c`), **Then** the text renders sanely (literal fallback for that fragment) and never crashes.
7. **Given** an emoji-only message, **Then** it renders unchanged (no markdown processing).
8. **Given** search mode is active, **Then** query highlighting still applies and markdown styling is suppressed (plain text + highlight only — see FR-009).

---

### User Story 2 — Format a draft with a toolbar (Priority: P2)

As an Android user composing a message, I can select text and tap Bold / Italic / Strikethrough / Code / Link to wrap it in the corresponding markdown, or tap again to remove it.

**Why this priority**: Completes authoring parity, but is larger surface area (text mutation, selection math, edge cases) and depends on P1 to make its output legible. Android's `TextFieldState` exposes `selection: TextRange` and `edit { }` on **all** OS versions, so — unlike iOS — no version gate is required.

**Independent Test**: Type "hello world", select "world", tap Bold → field shows "hello **world**" with "world" (plus delimiters) selected; tap Bold again → back to "hello world".

**Acceptance Scenarios**:

1. **Given** a non-empty selection, **When** a style button is tapped, **Then** the selection is wrapped with that style's delimiters and remains selected (including the delimiters).
2. **Given** an already-wrapped selection, **When** the same button is tapped, **Then** the delimiters are removed (toggle off).
3. **Given** a collapsed cursor, **When** a style button is tapped, **Then** an empty delimiter pair is inserted and the caret sits between them.
4. **Given** the Link button with a selection, **Then** a URL-entry dialog appears and Insert wraps the selection as `[selection](url)`; **Given** an existing markdown link is selected, **Then** the button unwraps it.
5. **Given** no selection exists, **Then** the wrap/toggle buttons are disabled (collapsed-cursor insert still allowed, matching the collapsed-cursor path).
6. **Given** wrapping would push the message over the 200-byte limit, **Then** the same over-limit affordance that governs Send applies (see FR-014).
7. **Given** a selection whose boundaries cut through existing delimiters, **When** wrapped, **Then** no orphaned unpaired delimiter characters are left behind.

---

### User Story 3 — Live in-field styling of the draft (Priority: P3)

As an Android user typing markdown, I see my formatting styled live in the compose field, so I can confirm it before sending.

**Why this priority**: Rounds out parity; depends on P1's renderer and P2's field wiring. Built last within the single delivery.

**Independent Test**: Type `see **this**`; "this" renders bold within the text field as you type (delimiters shown per the OutputTransformation styling rules); clearing the markdown returns the field to plain text.

**Acceptance Scenarios**:

1. **Given** the draft contains recognizable markdown syntax, **When** typing, **Then** the field applies the corresponding styling live via `OutputTransformation` without mutating the stored draft.
2. **Given** the draft contains no markdown syntax, **Then** the field renders as plain text.
3. **Given** live in-field styling is applied, **Then** the underlying `TextFieldState.text` (and therefore the bytes sent) is unchanged — styling is display-only.

---

### Edge Cases

- **Unpaired / nested / adjacent delimiters** (`***bold-italic***`, `**a*b**`, `a**b`): must not crash; render best-effort, preferring literal fallback over throwing.
- **Delimiters inside code spans** (`` `a*b*c` ``): content inside inline code should not be re-interpreted as italic/bold (matches CommonMark inline precedence; iOS's parser handles this).
- **Markdown link whose URL is also a channel URL** (`meshtastic.org/e/#…`): today Android does **not** intercept channel URLs tapped inside a bubble (iOS does). This spec does **not** add that interception (see Assumptions); the link opens as a normal external URL unless a separate story adds interception.
- **Mention token adjacent to a delimiter** (`**@!abcd1234**`): mention substitution and markdown span application must both land on correct offsets.
- **Very long messages near 200 bytes** where delimiters consume budget: byte counting already counts the raw delimiters (they are on the wire), so no special handling needed beyond existing enforcement.
- **RTL / bidi text** with delimiters: rendering must not corrupt bidi runs.
- **Homoglyph optimization** on the input path must continue to operate on the raw text.

## Architecture

### Key Components

| Component | Module / File | Change |
|-----------|---------------|--------|
| Inline markdown renderer | `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/AutoLinkText.kt` | Extend `buildAnnotatedStringWithLinks` to parse inline markdown into `SpanStyle`/`LinkAnnotation` spans, stripping delimiters and tracking offset shifts so mention + URL/email/phone detection stay aligned. This is the single render extension point. |
| Markdown parsing helper | `core/ui/src/commonMain/.../component/` (new, e.g. `InlineMarkdown.kt`) | Pure, testable function: raw string → (display string, list of styled ranges, list of link ranges). No Compose deps; unit-testable in `commonTest`. Recommended engine: JetBrains `org.jetbrains:markdown` AST walk; hand-rolled tokenizer is the fallback (see Implementation Options). |
| Message bubble | `feature/messaging/.../component/MessageItem.kt` | No change if styling is folded into `AutoLinkText` (bubble already calls it). Verify `HighlightedText` search path (FR-009). |
| Formatting helpers (P2) | `feature/messaging/.../` or `core/ui` (new, e.g. `MessageFormatting.kt`) | Pure functions mirroring iOS `MarkdownFormatting.swift`: wrap/toggle, insert delimiters, wrap/unwrap link, orphan cleanup, `containsMarkdownSyntax`. Operate on `(String, TextRange)` → new `(String, TextRange)`. Unit-testable. |
| Formatting toolbar (P2) | `feature/messaging/.../component/` (new, e.g. `FormattingToolbar.kt`) | Compose row of icon buttons operating on the shared `TextFieldState` via `edit { }`; disabled when `selection.collapsed` for wrap actions; link dialog. |
| Live in-field styling (P3) | `feature/messaging/.../Message.kt` (`OutputTransformation`) | **Chosen approach for P3.** Restyle displayed draft via `TextFieldBuffer.addStyle`, following the existing `mentionOutputTransformation`; replaces the separate preview bubble. |
| Compose field wiring (P2) | `feature/messaging/.../Message.kt` (`MessageInput`, ~549–661; `MessageScreen`, ~127–170) | Host the toolbar; drive it from the existing `rememberTextFieldState`; gate visibility on focus + `text.length >= 3`. |
| ~~Live preview bubble (P3)~~ | — | Superseded by in-field styling (see row above); build only if in-field styling proves insufficient. |
| Icons | `core/ui` MeshtasticIcons | Bold/Italic/Strikethrough/Code/Link glyphs (verify availability; add if missing). |
| Strings | `core/resources/.../composeResources/values/strings.xml` | Toolbar accessibility labels, link-dialog title/placeholder/buttons. Via `stringResource(Res.string.…)` — no hardcoded UI text. |

### Rendering pipeline (P1) — offset ordering

The existing pipeline substitutes `@!<hex>` mentions into display names (shifting offsets) and then runs URL/email/phone regex over the substituted display text, tracking `usedIndices` to prevent overlap. Inline markdown must slot into this **offset-shift chain**:

```
raw wire text
  → (a) substitute mentions to display names        [existing: shifts offsets]
  → (b) parse & strip inline-markdown delimiters,     [NEW: shifts offsets, records styled + link spans]
        recording styled ranges and [text](url) links
  → (c) run URL/email/phone autolink over the         [existing, now over the stripped text]
        stripped display text, skipping usedIndices
  → build AnnotatedString: apply mention links,
        markdown style spans, markdown link spans,
        autolink spans — all in display-space offsets
```

The correctness-critical requirement is that steps (a)+(b) produce a single consistent display string with an accurate raw→display index map, so every later span lands on the right characters. Prefer one linear tokenizer pass that emits the display string plus spans, rather than layering regex replacements.

### Why not the block markdown renderer

`multiplatform-markdown-renderer` renders a whole `Markdown(...)` Composable subtree (paragraph/heading/list layout). It cannot (a) restrict to inline-only cleanly, (b) share the bubble's selection/context-menu/tapback surface, or (c) interleave `@`-mention `LinkAnnotation.Clickable` spans. The `AnnotatedString` approach in `AutoLinkText` already does links+mentions+highlight; markdown is the natural next span source there.

## Implementation Options / Libraries

Everything below is KMP-compatible (`commonMain`); the shared-code goal rules out Android-only APIs. There is **no `commonMain` system API equivalent to iOS's `AttributedString(markdown:)`** — that is platform Foundation — so a parser (library or hand-rolled) is unavoidable on the KMP side.

### Rendering — turning the raw string into styled spans (P1)

| Option | What it is | Trade-offs | Verdict |
|--------|-----------|------------|---------|
| **JetBrains `org.jetbrains:markdown` (intellij-markdown)** | Pure-Kotlin **multiplatform** CommonMark parser; emits an AST whose nodes carry **source ranges**. `commonMain` artifact `org.jetbrains:markdown:<v>` (per-platform variants exist). | Real parser → correct nesting, `**`-vs-`*` precedence, no re-interpretation inside code spans, graceful unpaired-delimiter handling (directly serves FR-005) and closest to iOS's own full parser. AST source ranges *are* the raw→display offset map the pipeline needs. **Already the engine beneath the catalog's `multiplatform-markdown-renderer`**, so it is a proven, effectively-transitive dependency — just consumed at AST altitude, not as a block Composable. Slightly heavier than regex; requires an AST walk that emits inline spans and flattens block nodes to literal text. | **✅ Chosen (per Clarifications).** Walk the AST inside `buildAnnotatedStringWithLinks`, emit inline `SpanStyle`/`LinkAnnotation` spans, interleave with existing mention + URL/email/phone spans. Add as a direct catalog + `core/ui` dependency. |
| **Hand-rolled inline tokenizer** | A single linear pass recognizing the five inline delimiters. | Zero new dependency, total control over the offset map, tiny footprint. But you re-derive every CommonMark edge case yourself and risk diverging from the iOS-authored corpus (the exact fidelity risk in FR-005/Edge Cases). | Not chosen — recorded as the fallback if the dependency is later rejected. |
| ~~`multiplatform-markdown-renderer` (block `Markdown()`)~~ | Block-level Composable renderer. | Wrong altitude (see "Why not the block markdown renderer"). | Rejected. |
| ~~`HtmlCompat.fromHtml` / `Linkify` / `AnnotatedString.fromHtml`~~ | Android-platform HTML/autolink APIs. | Android-only (breaks `commonMain`/desktop) and HTML/autolink-only — none parse markdown. | Rejected. |

### Authoring — live styling and the toolbar (P2/P3)

| Option | What it is | Trade-offs | Verdict |
|--------|-----------|------------|---------|
| **Compose `OutputTransformation`** (`BasicTextField`/`TextFieldState`, "BasicTextField2") | System API that restyles the *displayed* buffer via `TextFieldBuffer.addStyle` without mutating the stored draft. **Already used in this module** — `mentionOutputTransformation` in `Message.kt` renders `@!<hex>` as `@FriendlyName` live. | Compose Multiplatform (desktop too). The new API removes the old visual-transformation offset-mapping that "was a great source of confusion and crashes." Can render `**bold**` styled **inline in the field as you type**, which makes the separate P3 preview bubble redundant — a genuine improvement over iOS parity, not just a match. | **✅ Chosen for P3 (per Clarifications).** Follow the existing mention-transformation pattern; no separate preview bubble. |
| `TextFieldState.edit { }` + pure helper functions | Programmatic delimiter wrap/toggle/insert/link driven by toolbar buttons, mirroring iOS `MarkdownFormatting.swift`. | No library needed; pure functions are `commonTest`-friendly (FR-017). | **Recommended** for the toolbar mutation logic. |

## Requirements *(mandatory)*

### Functional Requirements — Rendering (P1)

- **FR-001**: Message bubbles MUST render `**bold**` as bold, `*italic*` (single-asterisk, not part of `**`) as italic, `~~strike~~` with line-through, and `` `code` `` in a monospace family, with the delimiter characters removed from the displayed text.
- **FR-002**: Message bubbles MUST render `[label](url)` as a styled, tappable link on "label" to `url`, with the markdown syntax removed, using the same link styling/click handling as existing autolinks.
- **FR-003**: Existing autolinking of bare URLs, emails, and phone numbers MUST continue to work, and MUST NOT double-link a URL already expressed as a markdown link.
- **FR-004**: `@!<hex>` mention resolution/styling/click MUST continue to work and MUST remain correctly positioned when combined with markdown styling.
- **FR-005**: Malformed or unpaired markdown MUST degrade gracefully to literal text for the affected fragment and MUST NOT throw.
- **FR-006**: Emoji-only messages MUST bypass markdown processing (parity with iOS `isEmoji` guard).
- **FR-007**: Newlines and internal whitespace MUST be preserved (parity with `inlineOnlyPreservingWhitespace`).
- **FR-008**: Block markdown (headers, lists, blockquotes, fenced code, images, tables) MUST NOT be interpreted; such syntax renders literally.
- **FR-009**: Search-highlight mode MUST continue to highlight query matches. Markdown styling MUST be suppressed while a search query is active — the bubble renders plain text plus query highlight only (per Clarifications), avoiding offset conflicts between highlight spans and delimiter stripping.
- **FR-010**: The renderer MUST live in `commonMain` so desktop shares it. The parsing engine MUST be the JetBrains `org.jetbrains:markdown` (intellij-markdown) multiplatform CommonMark parser (AST-walk emitting inline spans), added as a direct version-catalog + `core/ui` dependency (per Clarifications). Block Composable renderers and Android-only HTML/Linkify APIs are excluded (see Implementation Options).

### Functional Requirements — Authoring toolbar (P2)

- **FR-011**: A formatting toolbar MUST offer Bold, Italic, Strikethrough, Code, and Link actions that insert/remove the corresponding delimiters on the current `TextFieldState` selection (via `TextFieldState.edit { }` + pure helpers). Live in-field styling of the draft SHOULD use Compose `OutputTransformation` (as the existing `mentionOutputTransformation` does).
- **FR-012**: Each style action MUST toggle: wrap an unwrapped selection, unwrap an already-wrapped one; a collapsed cursor MUST insert an empty delimiter pair with the caret placed between the delimiters.
- **FR-013**: The Link action MUST prompt for a URL and wrap the selection as `[selection](url)` (or insert a `[link text](url)` placeholder when the cursor is collapsed), and MUST unwrap an already-linked selection.
- **FR-014**: Byte-limit enforcement MUST remain authoritative — a formatting action that would exceed 200 bytes is subject to the same over-limit handling that already governs Send (no silent truncation of user content mid-delimiter).
- **FR-015**: Wrap actions MUST be disabled when there is no selection; toolbar visibility SHOULD follow focus and a minimum draft length (parity: ≥3 chars) — exact trigger is a UX detail, not a hard requirement.
- **FR-016**: Wrapping MUST trim surrounding whitespace so delimiters hug content, absorb adjacent delimiter characters, and remove orphaned unpaired delimiters (parity with iOS `MarkdownFormatting`).
- **FR-017**: All formatting-logic functions MUST be pure and unit-tested in `commonTest` (no Compose/Android deps), mirroring the iOS helper's testability.

### Functional Requirements — Draft styling (P3)

- **FR-018**: The user MUST be able to see how the draft will be styled before sending, via **live in-field styling** of the compose field using Compose `OutputTransformation` (following the existing `mentionOutputTransformation`), per Clarifications. A separate preview bubble is NOT required unless in-field styling proves insufficient in practice.

### Non-Functional Requirements

- **NFR-001**: No new protobuf fields and no change to transmitted bytes — the wire payload is the user's raw text (parity with iOS).
- **NFR-002**: Accessibility — toolbar buttons MUST have content descriptions (localized); styled text MUST remain selectable/announced by TalkBack; links MUST be reachable.
- **NFR-003**: Localization — all new user-facing strings via `stringResource(Res.string.…)`; run `python3 scripts/sort-strings.py` after adding keys.
- **NFR-004**: Performance — annotated-string construction stays within the existing `remember(text, …)` cache in `AutoLinkText`; parsing is a single linear pass, no per-frame regex storms.
- **NFR-005**: Rendering MUST not regress existing messaging behaviors (reply snippets, reactions, status icons, paging).
- **NFR-006**: Design-standard alignment — toolbar uses M3 components and `MeshtasticIcons`; link/style colors reuse existing tokens (`HyperlinkBlue`, monospace via theme).

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` (`core/ui`) | Modified — inline-markdown parsing + span application in `AutoLinkText`; new pure parser helper | Shared render path used by Android and desktop |
| `commonMain` (`feature/messaging`) | Added (P2/P3) — formatting helpers, toolbar, preview, `MessageInput` wiring | Compose UI + text-mutation logic is shared |
| `commonTest` | Added — unit tests for the parser (P1) and formatting helpers (P2) | Pure functions, high-value coverage |
| `commonMain` (`core/resources`) | Added strings | Accessibility labels + link dialog |
| `androidMain` / `jvmMain` | None expected | No platform-specific code; `TextFieldState`/`AnnotatedString` are multiplatform |
| `core/proto` | None | Wire format unchanged (read-only upstream regardless) |

## Design Standards Compliance

- [ ] Toolbar reviewed against design standards — new component; verify against `.skills/design-standards` and upstream `meshtastic/design`
- [ ] M3 component selection verified — icon buttons, dialog
- [ ] Accessibility: TalkBack semantics on toolbar + styled/linked text, 44dp touch targets, color-independent affordances
- [ ] Typography: inline `code` monospace family sourced from theme, not hardcoded
- [x] No new screens — changes are within the existing messaging screen and shared text component

## Privacy Assessment

- [x] No PII, location, or cryptographic keys logged or exposed
- [x] No new network calls; no data transmitted beyond the existing message payload
- [x] `core/proto` not modified (read-only upstream)
- [x] Markdown is presentation-only; message content on the wire is unchanged from what the user typed

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A message authored on iOS with bold/italic/strikethrough/code/link renders on Android with the same styling and no visible delimiter characters (P1).
- **SC-002**: Bare URLs, emails, phone numbers, and `@`-mentions continue to render and behave exactly as before (zero regression) (P1).
- **SC-003**: Malformed markdown never crashes and degrades to readable text across a fuzz set of unpaired/nested/adjacent delimiters (P1).
- **SC-004**: An Android user can produce, via the toolbar, a message byte-for-byte identical to what iOS's toolbar would produce for the same selection and action (P2).
- **SC-005**: Formatting helpers and the inline parser have unit tests covering wrap/toggle/insert/link/orphan-cleanup and each style, all in `commonTest` (P1+P2).
- **SC-006**: Desktop renders the same inline styling as Android from the shared `commonMain` renderer (P1).
- **SC-007**: Baseline verification passes: `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests` (+ `kmpSmokeCompile` for touched KMP modules).

## Assumptions

- Inline markdown rendering belongs in `core/ui/AutoLinkText.kt` (extending `buildAnnotatedStringWithLinks`), **not** the block `multiplatform-markdown-renderer`, which is inappropriate for chat bubbles.
- If the JetBrains `org.jetbrains:markdown` parser is chosen, it must be **added as a direct version-catalog entry** and a `core/ui` dependency — it is currently only a *transitive* dependency of the block `multiplatform-markdown-renderer` (which `core/ui`/`feature/messaging` do not depend on), so it cannot be relied upon implicitly.
- Android continues to transmit the raw draft (literal delimiters), matching iOS; the "non-markdown clients see `**`" tradeoff is accepted as inherent to parity and out of scope to change here.
- No stored/derived markdown DB column is added; parsing happens at render time alongside existing autolinking.
- Search mode may suppress markdown styling to keep highlight offsets simple (FR-009) — treated as an acceptable, documented divergence from iOS.
- Channel-URL (`meshtastic.org/e/#…`) interception on links tapped *inside a bubble* is **not** added by this spec (Android doesn't do it today for autolinks either); if desired it is a separate story spanning both markdown links and existing autolinks.
- Android does not need iOS's version gate: `TextFieldState` (already used by `MessageInput`) exposes selection and `edit { }` on all supported versions, so the toolbar can ship without an OS floor.
- The inline `code` monospace family is available via the Compose Multiplatform theme; if not, a bundled monospace resource is a small add.
- Bold/Italic/Strikethrough/Code/Link icons exist in `MeshtasticIcons` or are trivially added.
- CommonMark inline precedence (e.g. no re-interpretation inside code spans, `**`-before-`*`) is the target semantics; exact parser is an implementation choice, but its behavior must match the acceptance scenarios and the iOS-authored corpus.

## Resolved Decisions

All prior open questions were resolved in the 2026-07-11 clarification session (see Clarifications):

1. **Scope** — P1 + P2 + P3 delivered together (staged internally by priority).
2. **Search + styling** — markdown styling suppressed during active search (FR-009).
3. **Parser strategy** — JetBrains `org.jetbrains:markdown` (intellij-markdown), added as a direct dependency (FR-010).
4. **Draft styling** — live in-field styling via `OutputTransformation`; no separate preview bubble unless it proves insufficient (FR-018).
5. **Channel-URL interception inside bubbles** — out of scope; deferred to a separate story covering existing autolinks too.
