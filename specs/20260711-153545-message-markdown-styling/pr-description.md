# Message inline markdown styling (iOS parity)

## Why

Because iOS transmits **raw markdown delimiters over the mesh** (styling is presentation-only — there is no separate formatted payload), every Android user in a channel with an iOS 18+ user *already receives* messages full of literal `**asterisks**` and `~~tildes~~` today, and renders them as garbled literal text. Closing the **render** gap is the higher-value, lower-risk half of parity; the **authoring** half (a formatting toolbar + live in-field styling) matches the iOS compose editor.

Cross-platform reference: meshtastic-apple PR [#1771](https://github.com/meshtastic/meshtastic-apple/pull/1771).

## What

Inline markdown — **bold**, *italic*, ~~strikethrough~~, `inline code`, and `[links](url)` — rendered in bubbles and authorable from the compose field. All logic lives in shared `commonMain`, so Android **and** desktop get one implementation. Delivered as three internally-staged user stories (single PR):

### 🌟 New
- **Render (US1):** message bubbles now render the five inline styles with delimiters removed; existing autolinking, `@`-mention resolution, and reply/search behavior are preserved. Parse failures fall back to plain autolinked text; emoji-only messages short-circuit parsing unchanged.
- **Toolbar (US2):** a 5-button formatting row (bold/italic/strikethrough/code/link) over the compose field — shown on focus with ≥3 characters. Selection wrap/toggle, collapsed-cursor insert, and a link-entry dialog (wrap / unwrap) mirror the iOS `MarkdownFormatting` rules byte-for-byte.
- **Live in-field styling (US3):** the draft styles live in the field via a markdown `OutputTransformation` composed with the existing mention transformation — display-only, never mutating the stored `TextFieldState.text`.

### 🛠️ Changes
- New direct dependency `org.jetbrains:markdown` (0.7.5) in `core/ui` — pins the version already resolved transitively via `multiplatform-markdown-renderer` 0.43.0, so no new resolved artifact.
- `AutoLinkText.buildAnnotatedStringWithLinks` extended to interleave mention → markdown-style → markdown-link → bare-autolink spans in one display-space coordinate system.
- Four new `ic_format_*`/`ic_code` drawables on the house 960-grid Material Symbols convention (matching the neighboring `ic_link`).

## Notes / scope boundaries
- **Wire format unchanged** — Android continues to send exactly what the user typed (raw delimiters), matching iOS. **No protobuf and no database changes.**
- **Search divergence (intentional):** while a message search is active, bubbles render plain text + query highlight only (no markdown), per the clarified spec.
- Block-level markdown (headers, lists, tables, code fences) is explicitly out of scope — iOS is inline-only; the catalog's `multiplatform-markdown-renderer` is a *block* renderer and the wrong tool for chat bubbles.
- Channel-URL tap interception is deferred to a separate story.

## Testing Performed
- `:core:ui` `commonTest` — `InlineMarkdownTest` (parser contract, P-1…P-11) and `AutoLinkTextRenderTest` (render contract, A-1…A-8).
- `:feature:messaging` `commonTest` — `MessageFormattingTest` (formatting-helper contract F-1…F-9, including iOS parity fixtures for byte-identical output).
- Baseline: `spotlessApply spotlessCheck detekt assembleDebug kmpSmokeCompile` — green.
- **Live on-device verify** (fdroid-debug on an emulator + meshcon replay):
  - **US1 render** — injected `Meet at **noon** by the ~~old~~ new *bridge* — \`README\` and [map](https://example.com)`; the bubble rendered noon=bold, old=strikethrough, bridge=italic, README=monospace, map=blue tappable link, with **all delimiters stripped**.
  - **US2 toolbar** — appears on focus (≥3 chars) with all five buttons; tapping Bold on a selection wrapped it to `**testa**` with the selection growing to cover the delimiters (iOS behavior).
  - **US3 live styling** — typing `hello**world**` styled "world" bold live in the field while the raw `**` stayed visible and the byte counter tracked the raw text (transform is display-only).
