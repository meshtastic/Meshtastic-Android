# Contract: Formatting helpers (toolbar mutation logic)

**Location**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/MessageFormatting.kt`
**Kind**: pure functions, `commonTest`-covered. Mirror of iOS `MarkdownFormatting.swift`. No `android.*` or UI-framework imports; `TextRange` (`androidx.compose.ui.text.TextRange`) is a Compose **Multiplatform** `commonMain` value type and is allowed — it carries no Android/desktop platform dependency and is fully constructible in `commonTest`. (If a strictly framework-free surface is preferred, substitute a plain `IntRange`/`Int` pair, closer to iOS's `String.Index` ranges.)
**Consumed by**: `FormattingToolbar` via `TextFieldState.edit { }`.

## Signatures (illustrative)

```kotlin
fun wrapSelection(text: String, range: TextRange, style: InlineStyle): FormattingResult
fun insertDelimiters(text: String, at: Int, style: InlineStyle): FormattingResult
fun wrapSelectionWithLink(text: String, range: TextRange, url: String): FormattingResult
fun unwrapLink(text: String, range: TextRange): FormattingResult?   // null if not a link
fun isMarkdownLink(text: String): Boolean
fun containsMarkdownSyntax(text: String): Boolean
```

`FormattingResult(text, selection)` — new raw text + new `TextRange` selection.

## Behavioral contract

| ID | Given | Then |
|----|-------|------|
| F-1 | selection `world` in `hello world`, Bold | `hello **world**`, selection covers `**world**` (FR-011/012) |
| F-2 | selection `**world**` already wrapped, Bold | toggles off → `hello world` (FR-012) |
| F-3 | collapsed cursor, Italic | inserts `**`→ `*|*` with caret between (FR-012) |
| F-4 | selection `docs`, Link, url `https://e.com` | `[docs](https://e.com)` (FR-013) |
| F-5 | collapsed cursor, Link | inserts `[link text](url)` placeholder (FR-013) |
| F-6 | selection is `[docs](url)`, Link | unwraps to `docs` (FR-013) |
| F-7 | selection ` world ` (padded) | delimiters hug content: `**world**` with outer spaces preserved (FR-016) |
| F-8 | selection cuts through existing `*` | no orphaned unpaired delimiter left (FR-016) |
| F-9 | `containsMarkdownSyntax("a **b**")` | true; `containsMarkdownSyntax("a b")` false |

## Invariants

- Every returned `selection` is a valid `TextRange` within the returned `text`.
- Functions are total and deterministic; never throw on valid `(text, range)` pairs.
- Byte-limit is NOT enforced here — the caller (compose field) owns the 200-byte gate (FR-014); helpers only transform text/selection.
- No `android.*` or UI-framework imports; `TextRange` (Compose MP, `commonMain`) is permitted so they still run in `commonTest`.
