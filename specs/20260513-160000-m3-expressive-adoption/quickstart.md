# Quickstart: M3 Expressive Adoption

**Branch**: `20260513-160000-m3-expressive-adoption`

## Prerequisites

```bash
# Standard bootstrap (if not already done)
git submodule update --init
[ -f local.properties ] || cp secrets.defaults.properties local.properties

# Checkout the feature branch
git checkout 20260513-160000-m3-expressive-adoption
```

## Key Files to Know

| File | Purpose |
|------|---------|
| `core/ui/src/commonMain/.../theme/Theme.kt` | Root `AppTheme` — already uses `MaterialExpressiveTheme` |
| `core/ui/src/commonMain/.../theme/Type.kt` | Typography definition — upgrade target |
| `core/ui/src/commonMain/.../component/` | 67+ shared components — upgrade targets |
| `feature/node/src/commonMain/.../list/NodeListScreen.kt` | Node list — swipe-to-action target |
| `feature/messaging/src/commonMain/...` | Message list — swipe-to-action target |
| `screenshot-tests/` | Visual regression tests |

## Build & Verify

```bash
# Full verification (run after every change)
./gradlew spotlessApply detekt assembleDebug test allTests

# Update screenshot references after visual changes
./gradlew :screenshot-tests:updateScreenshotTests

# Single module test (e.g., after changing core/ui)
./gradlew :core:ui:allTests
```

## Implementation Order

1. **Typography** (Type.kt) — Foundation for all screens
2. **Navigation** (MeshtasticNavigationSuite.kt) — Highest-traffic interaction
3. **Core components** (MainAppBar, buttons, dialogs) — Shared across all features
4. **FABs & progress** (MenuFAB, progress indicators) — Already partially expressive
5. **Swipe-to-action** (new SwipeToRevealBox) — New component + integration
6. **Feature module rollout** — Apply typography + component updates per module
7. **Accessibility** — Focus rings, reduced-motion verification

## Working with Expressive APIs

```kotlin
// Always annotate at function level in feature modules
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyScreen() {
    // Access emphasized typography
    Text(
        text = "Heading",
        style = MaterialTheme.typography.titleMediumEmphasized,
    )
}
```

## Swipe-to-Action Pattern

```kotlin
// In node list item
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NodeListItemWithSwipe(node: NodeEntity, viewModel: NodeListViewModel) {
    val state = rememberSwipeToRevealState()

    SwipeToRevealBox(
        state = state,
        startContent = { RequestPositionAction() },
        endContent = { MuteNodeAction() },
        onActionTriggered = { direction ->
            when (direction) {
                SwipeDirection.StartToEnd -> viewModel.requestPosition(node)
                SwipeDirection.EndToStart -> viewModel.muteNode(node)
            }
        },
    ) {
        NodeListItem(node)
    }
}
```

## Key Constraints (from clarification)

| Constraint | Implication |
|-----------|-------------|
| No feature flags | Direct replacement — each PR is atomic and revertible via git |
| No font bundling | Pre-Android 12 gets standard weight fallback; no APK size increase |
| No manual reimplementations | If CMP material3 doesn't provide it, skip it entirely |
| Swipe actions | Right = request position, Left = mute (node list) |
| Discoverability hint | Per-session edge-peek until first successful swipe |

## Commit Strategy

One commit per user story on a single branch:
1. Commit: Setup + Foundational (theme, typography, SwipeToRevealBox)
2. Commit: Navigation expressive indicators (US1)
3. Commit: Typography rollout across feature modules (US2)
4. Commit: Core component upgrades (US3)
5. Commit: SwipeToRevealBox + node/message list integration (US4)
6. Commit: Accessibility + focus rings (US5)
7. Commit: Screenshot test updates + polish (cross-cutting)
