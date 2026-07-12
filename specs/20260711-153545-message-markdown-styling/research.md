# Phase 0 Research: Message Inline Markdown Styling

All decisions below resolve the spec's Clarifications and the plan's Technical Context. No NEEDS CLARIFICATION remain.

## R1 — Markdown parser

- **Decision**: Use `org.jetbrains:markdown` (intellij-markdown), pinned to **0.7.5**, added as a direct `commonMain` dependency of `core/ui` and a version-catalog entry.
- **Rationale**: Pure-Kotlin multiplatform CommonMark parser producing an AST whose nodes carry source ranges — those ranges are exactly the raw→display offset map the render pipeline needs. A real parser gets nesting, `**`-vs-`*` precedence, and "no re-interpretation inside code spans" correct for free (spec FR-005, Edge Cases). **0.7.5 is the version already resolved transitively** by the catalog's `multiplatform-markdown-renderer` 0.43.0, so pinning to it avoids introducing a second, conflicting version.
- **Alternatives considered**:
  - *Hand-rolled inline tokenizer* — dependency-free and tiny, but re-derives every CommonMark edge case and risks diverging from the iOS-authored corpus. Recorded as the fallback if the dependency is ever rejected.
  - *`multiplatform-markdown-renderer` block Composable* — wrong altitude for chat bubbles (can't interleave mention/link spans, brings block layout). Rejected in spec.
  - *Android `HtmlCompat.fromHtml` / `Linkify` / `AnnotatedString.fromHtml`* — Android-only + HTML/autolink-only; break the `commonMain`/desktop goal. Rejected.

## R2 — Inline-only rendering from a block-capable AST

- **Decision**: Parse with the CommonMark flavour, then walk the AST emitting spans **only** for inline node types (`STRONG`→bold, `EMPH`→italic, `CODE_SPAN`→monospace, `INLINE_LINK`/link destination→`LinkAnnotation.Url`, plus GFM `~~`strikethrough if the flavour exposes it; otherwise handle `~~` as a small pre-pass). Block structure (paragraphs, and any heading/list/quote syntax) is flattened to literal text with whitespace/newlines preserved (spec FR-007, FR-008).
- **Rationale**: Matches iOS's `.inlineOnlyPreservingWhitespace`. Walking the tree and appending display text as we go naturally produces the stripped display string plus a correct offset map, satisfying the pipeline's core correctness requirement.
- **Note**: CommonMark flavour does not include `~~`strikethrough; GFM flavour does. Decision: use the flavour that yields strikethrough, or add a minimal `~~…~~` span pass over the display text if staying on CommonMark. Confirmed as an implementation detail for the parser task, not a blocker.
- **Alternatives considered**: Emitting block spacing/structure — rejected (not parity; disrupts bubble layout).

## R3 — Offset ordering with mentions + autolinks

- **Decision**: Extend `buildAnnotatedStringWithLinks` to run, in order: (a) existing mention-token → display-name substitution (records display-space mention ranges), (b) **markdown parse + delimiter strip** over the mention-substituted text (records inline style ranges and markdown-link ranges, produces the final display string), (c) existing URL/email/phone autolink regex over the stripped display text, skipping `usedIndices`. Then build the `AnnotatedString` applying mention links, markdown style spans, markdown link spans, and autolink spans — all in the single display-space coordinate system.
- **Rationale**: There must be one consistent display string with one accurate index map so every span lands correctly (spec Rendering pipeline section, FR-003/FR-004). Running markdown between mention substitution and autolinking keeps autolink offsets valid after delimiter removal and prevents double-linking a URL already expressed as a markdown link.
- **Alternatives considered**: Layering independent regex replacements — rejected; compounding offset shifts is the documented source of span misplacement.

## R4 — Search-mode behavior

- **Decision**: When a search query is active, `MessageItem` continues to use `HighlightedText` (plain text + highlight) and does **not** apply markdown styling (spec FR-009).
- **Rationale**: Avoids reconciling delimiter-stripping offsets against highlight ranges; keeps the existing search path untouched. Accepted, documented divergence from iOS.
- **Alternatives considered**: Combined markdown + highlight — deferred; higher offset-reconciliation cost for marginal benefit while searching.

## R5 — Authoring: live in-field styling

- **Decision**: Style the draft live via Compose `OutputTransformation` on the existing `TextFieldState`, composed with the module's existing `mentionOutputTransformation`. No separate preview bubble (spec FR-018).
- **Rationale**: The API is already present in Compose Multiplatform 1.11.1 and already used in `Message.kt` for mentions; `TextFieldBuffer.addStyle` restyles the displayed buffer without mutating stored text (so the bytes sent are unchanged), and the new API removes the legacy visual-transformation offset-mapping crash class. Works on desktop.
- **Alternatives considered**: iOS-style separate preview bubble — superseded (redundant once the field itself styles); build only if in-field styling proves insufficient.

## R6 — Toolbar mutation logic

- **Decision**: Pure functions in `feature/messaging/MessageFormatting.kt` operating on `(String, TextRange) -> (String, TextRange)` for wrap/toggle, collapsed-cursor insert, link wrap/unwrap, whitespace-hug trimming, and orphaned-delimiter cleanup — mirroring iOS `MarkdownFormatting.swift`. The toolbar applies them through `TextFieldState.edit { }`. Unit-tested in `commonTest` (spec FR-017).
- **Rationale**: Isolating mutation logic from Compose gives high-value, harness-free tests and 1:1 traceability to the iOS reference behavior.
- **Alternatives considered**: Mutating inside composables — rejected (untestable without a UI harness).

## R7 — Icons and monospace font

- **Decision**: `FontFamily.Monospace` (Compose built-in, already used in `feature/settings` Debug/Logcat) for inline `code`. Link icon reuses the existing `ic_link` drawable; add four new drawables — `ic_format_bold`, `ic_format_italic`, `ic_format_strikethrough`, `ic_code` — to `core/resources` plus `MeshtasticIcons.{Bold,Italic,Strikethrough,Code}` extension vals (following the `vectorResource(Res.drawable.ic_*)` pattern in `icon/Actions.kt`).
- **Rationale**: No `material-icons-extended` dependency exists; the project sources icons as bundled Material Symbols XML drawables. Reusing that path keeps visual consistency (Constitution V) and avoids a new dependency.
- **Alternatives considered**: Adding `material-icons-extended` — rejected (heavier dependency; breaks the established bundled-drawable convention).

## R8 — Byte limit & wire format

- **Decision**: No change. The 200-byte limit already counts the raw text including delimiters (they travel on the wire); `sendMessage` continues to send `TextFieldState.text` verbatim. In-field styling and toolbar wrapping mutate only the raw text the user would have typed anyway.
- **Rationale**: Parity — iOS transmits raw delimiters; Android already does and must continue (spec NFR-001). No proto/DB fields (Constitution IV).

## Open risks (carried to tasks, not blockers)

- **Strikethrough on CommonMark flavour** (R2 note) — resolve during the parser task (GFM flavour vs. `~~` pre-pass).
- **`OutputTransformation` composition** — verify markdown styling and mention rendering compose cleanly in one transformation (or chain) without offset conflicts; covered by a manual verify step in quickstart.
- **Design review + docs** (Constitution V/VI) — process tasks.
