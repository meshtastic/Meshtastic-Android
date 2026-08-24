# Phase 1 Data Model: Message Inline Markdown Styling

**No persisted entities, no database migration, no protobuf changes.** Markdown is parsed at render time and styled at display time; nothing is stored. This document defines only the **in-memory** types the parser and renderer pass around.

## Coordinate systems

- **Raw text** — the exact bytes the user typed / that arrived on the mesh, delimiters included (`"a **b** c"`). This is what `TextFieldState.text` holds and what `sendMessage` transmits.
- **Display text** — delimiters stripped, mentions substituted to display names (`"a b c"`). All spans are expressed in display-space offsets.
- The parser is responsible for producing the display string **and** the spans in one pass so offsets are internally consistent (research R3).

## Types (in-memory, `commonMain`, no Compose dependency)

### `InlineStyle` (enum)
The five inline styles, parity with iOS `MarkdownStyle`.

| Value | Delimiter(s) | Rendered as |
|-------|--------------|-------------|
| `Bold` | `**` | `FontWeight.Bold` |
| `Italic` | `*` | `FontStyle.Italic` |
| `Strikethrough` | `~~` | `TextDecoration.LineThrough` |
| `Code` | `` ` `` | `FontFamily.Monospace` |
| `Link` | `[text](url)` | `LinkAnnotation.Url` (styled + tappable) |

### `StyleSpan`
An inline style applied over a display-space range.
- `range: IntRange` — half-open in practice (`start until end`), display-space.
- `style: InlineStyle` — one of the non-link styles (`Bold`/`Italic`/`Strikethrough`/`Code`). Multiple `StyleSpan`s may overlap (e.g. bold+italic).

### `LinkSpan`
A markdown link over a display-space range.
- `range: IntRange` — covers the link's display text (the label), display-space.
- `url: String` — the resolved destination.

### `InlineMarkdownResult`
The parser's return value — the single source of truth for a render.
- `displayText: String` — delimiter-stripped text (mentions already substituted by the caller before parsing, per R3).
- `styleSpans: List<StyleSpan>`
- `linkSpans: List<LinkSpan>`

> The renderer (`AutoLinkText`) consumes `InlineMarkdownResult`, then layers mention `LinkAnnotation.Clickable` spans and URL/email/phone autolink spans over the same `displayText`, using `usedIndices` to prevent overlap with markdown links.

## Authoring types (transient, `feature/messaging`)

### `FormattingResult`
Return of each pure formatting helper (parity with iOS `FormattingResult`).
- `text: String` — new raw text after the edit.
- `selection: TextRange` — new selection to apply to the `TextFieldState`. `TextRange` is a Compose **Multiplatform** `commonMain` value type (no Android/desktop platform dependency); the helpers therefore stay `commonTest`-runnable. A plain `IntRange`/`Int` pair is an acceptable framework-free substitute if preferred.

No other state is introduced. Toolbar visibility (`focus && text.length >= 3`), the link-dialog URL string, and the pending link range are ordinary composable `remember`/state, not model entities.

## What is explicitly NOT added

- No new column on the message entity, no `messagePayloadMarkdown`-style stored/derived field (Android parses at render time, unlike iOS which persists a derived autolinked payload). Confirmed against `feature/messaging` + `core/database`.
- No protobuf field; wire payload = raw `TextFieldState.text`.
- No new Room migration.
