# Contract: AutoLinkText render extension

**Location**: `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/AutoLinkText.kt`
**Change**: extend the existing private `buildAnnotatedStringWithLinks(...)`; the public `AutoLinkText(...)` composable signature is UNCHANGED (callers in `feature/messaging/MessageItem.kt` need no edits).

## Existing public surface (unchanged)

```kotlin
@Composable
fun AutoLinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    linkStyles: TextLinkStyles = DefaultTextLinkStyles,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    mentionName: ((String) -> String?)? = null,
    onMentionClick: ((String) -> Unit)? = null,
)
```

## Behavioral contract for the extended builder

Processing order (research R3), producing ONE display string + spans in one coordinate system:

1. **Mention substitution** (existing): `@!<hex>` → `@DisplayName`; record display-space mention ranges.
2. **Markdown parse** (new): call `parseInlineMarkdown` on the mention-substituted text → `displayText`, `styleSpans`, `linkSpans`.
3. **Autolink** (existing): run URL/email/phone regex over `displayText`, skipping `usedIndices`.
4. **Build** `AnnotatedString(displayText)` applying, in order: mention `LinkAnnotation.Clickable`, markdown `StyleSpan`s (`SpanStyle`), markdown `LinkSpan`s (`LinkAnnotation.Url`, styled via `linkStyles`), autolink `LinkAnnotation.Url`. Mark each applied range in `usedIndices`.

| ID | Given | Then |
|----|-------|------|
| A-1 | `**bold**` | "bold" bold, no asterisks shown (FR-001) |
| A-2 | `[label](https://e.com)` | "label" tappable link, syntax removed (FR-002) |
| A-3 | `see https://e.com` (bare) | autolinked as today (FR-003) |
| A-4 | `[x](https://e.com) and https://e.com` | markdown link on `x`; bare URL also linked; no double-link inside the markdown link (FR-003) |
| A-5 | `**@!abcd1234**` | mention resolves to bold display name, correct offsets (FR-004) |
| A-6 | markdown parse fails/none | falls back to plain autolinked text (no regression) |
| A-8 | emoji-only text | markdown parsing short-circuited; renders unchanged (FR-006) |
| A-7 | `remember(text, linkStyles, mentionName)` cache key | unchanged caching behavior; parse runs inside the existing `remember` (NFR-004) |

## Invariants

- Public composable signature and all existing call sites remain source-compatible.
- No span range exceeds `displayText.indices`; overlapping ranges resolved via `usedIndices` (markdown links and mentions win over bare autolinks on conflict).
- Desktop and Android render identically (shared `commonMain`).
