# Contract: Inline Markdown Parser

**Location**: `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/InlineMarkdown.kt`
**Kind**: pure function, no Compose/Android deps, `commonTest`-covered.
**Backing library**: `org.jetbrains:markdown` 0.7.5 (AST).

## Signature (illustrative)

```kotlin
internal fun parseInlineMarkdown(source: String): InlineMarkdownResult
```

- `source` — the text to parse, AFTER mention substitution (display-space input; see AutoLinkText contract for ordering).
- Returns `InlineMarkdownResult(displayText, styleSpans, linkSpans)` (see data-model.md).

## Behavioral contract

| ID | Given | Then |
|----|-------|------|
| P-1 | `a **b** c` | displayText `a b c`; one `StyleSpan(Bold)` over `b` |
| P-2 | `a *b* c` | italic span over `b`; single `*` not treated as bold |
| P-3 | `a ***b*** c` | bold+italic spans both covering `b` (overlap allowed) |
| P-4 | `a ~~b~~ c` | strikethrough span over `b` |
| P-5 | `` a `b*c` d `` | code span over `b*c`; the `*` inside is NOT italic (code precedence) |
| P-6 | `[x](https://e.com)` | displayText `x`; `LinkSpan(range=x, url=https://e.com)` |
| P-7 | `**oops` (unpaired) | displayText contains literal `**oops`; no span; no throw (FR-005) |
| P-8 | `# heading\n- item` (block syntax) | rendered literally as text; no heading/list structure (FR-008) |
| P-9 | `line1\nline2` | newline preserved in displayText (FR-007) |
| P-10 | empty / blank | returns empty displayText, empty span lists |
| P-11 | emoji-only (caller guards) | caller skips parser; parser itself still safe if called |

## Invariants

- `displayText` never contains a delimiter that was consumed to produce a span.
- Every `StyleSpan.range` / `LinkSpan.range` is within `displayText.indices`.
- Deterministic: same input → same output (no time/randomness).
- Total: never throws on any `String` input; malformed markdown degrades to literal text.

## Notes

- Strikethrough (`~~`) requires a GFM-capable flavour or a minimal pre-pass (research R2); either satisfies P-4.
- The parser does not resolve mentions or bare URLs/emails/phones — those are layered by `AutoLinkText` (separation of concerns).
