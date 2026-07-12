# Quickstart: Message Inline Markdown Styling

Build/verify/design-check runbook for implementers and reviewers.

## Bootstrap (once per shell)

```bash
[ -z "$ANDROID_HOME" ] && export ANDROID_HOME="$HOME/Library/Android/sdk"
[ -f local.properties ] || cp secrets.defaults.properties local.properties
```

## Dependency wiring

1. `gradle/libs.versions.toml`:
   - `[versions]` add `markdown = "0.7.5"`
   - `[libraries]` add `jetbrains-markdown = { module = "org.jetbrains:markdown", version.ref = "markdown" }`
2. `core/ui/build.gradle.kts` → `commonMain.dependencies { implementation(libs.jetbrains.markdown) }`

## Build order within the single delivery (P1 → P2 → P3)

- **P1 render**: `InlineMarkdown.kt` parser + `AutoLinkText` extension + `InlineMarkdownTest`. Verify received/injected markdown renders styled.
- **P2 toolbar**: `MessageFormatting.kt` helpers + `MessageFormattingTest` + `FormattingToolbar.kt` + `Message.kt` wiring + 4 icons + strings.
- **P3 in-field styling**: markdown `OutputTransformation` composed with the mention one in `Message.kt`.

## Strings & icons

- Add toolbar a11y labels + link-dialog strings to `core/resources/src/commonMain/composeResources/values/strings.xml` (via `stringResource(Res.string.*)`; no hardcoded UI text).
- Run `python3 scripts/sort-strings.py` after adding strings.
- Add drawables `ic_format_bold`, `ic_format_italic`, `ic_format_strikethrough`, `ic_code` (Material Symbols line style, matching existing `ic_*`); `ic_link` already exists. Add `MeshtasticIcons.{Bold,Italic,Strikethrough,Code}` extension vals.

## Verification

Delegate heavy Gradle to the `gradle-runner` subagent; **git-diff-verify after** (it can mutate the tree). Baseline:

```bash
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile
```

Single-module fast loops:

```bash
./gradlew :core:ui:allTests --tests "*InlineMarkdown*"
./gradlew :feature:messaging:allTests --tests "*MessageFormatting*"
```

### Live verification (mandatory — /verify skill)

Drive the real flow, not just tests:
- Receive/inject a mixed message: `Meet at **noon** by the ~~old~~ new *bridge* — \`README\` and [map](https://example.com)`; confirm all five styles + tappable link, no delimiters, mention + bare-URL still work.
- Search a conversation → confirm markdown is suppressed and highlight still works (FR-009).
- Compose: select text → each toolbar button wraps/toggles; collapsed cursor inserts a pair; Link dialog wraps/unwraps; live in-field styling shows as you type (O-1..O-5).
- Stress the `OutputTransformation` composition: markdown + mention together, then send, quick-chat append, and type past 200 bytes — watch for offset crashes.
- Desktop: confirm the same rendering (shared `commonMain`).

## Design & docs gates (Constitution V/VI)

- [ ] Toolbar checked against `.skills/design-standards`; new icons match MeshtasticIcons weight; touch targets ≥ 44dp; TalkBack labels present.
- [ ] Reference iOS PR #1771 as the cross-platform behavior source.
- [ ] Update the messaging `docs/` page (`last_updated` frontmatter) OR apply `skip-docs-check` with justification.

## Screenshot tests

Regenerate Compose preview screenshots for `MessageItem`/`MessageInput` via `updateDebugScreenshotTest`; note `allTests` may dirty tracked `docs/assets/screenshots/*.png` on this Mac — `git checkout --` unintended changes before committing.
