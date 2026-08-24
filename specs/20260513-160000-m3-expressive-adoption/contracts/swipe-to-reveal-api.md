# Contract: SwipeToRevealBox Component API

**Module**: `core/ui` | **Source Set**: `commonMain`

## Public API

```kotlin
/**
 * A composable container that reveals action content behind the foreground
 * when the user swipes horizontally. Supports bi-directional swipe with
 * spring-physics animations.
 *
 * @param state The [SwipeToRevealState] controlling the drag position
 * @param startContent Content revealed on right-swipe (e.g., "request position")
 * @param endContent Content revealed on left-swipe (e.g., "mute")
 * @param modifier Modifier applied to the container
 * @param enableStartSwipe Whether right-swipe is enabled (default: true)
 * @param enableEndSwipe Whether left-swipe is enabled (default: true)
 * @param onActionTriggered Callback when a full-swipe threshold is crossed
 * @param content The foreground content (the list item)
 */
@ExperimentalMaterial3ExpressiveApi
@Composable
fun SwipeToRevealBox(
    state: SwipeToRevealState,
    startContent: @Composable BoxScope.() -> Unit,
    endContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    enableStartSwipe: Boolean = true,
    enableEndSwipe: Boolean = true,
    onActionTriggered: (SwipeDirection) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
)

/**
 * Creates and remembers a [SwipeToRevealState].
 *
 * @param initialAnchor Starting anchor position (default: [SwipeAnchor.Start])
 * @param confirmValueChange Optional callback to confirm anchor changes
 */
@Composable
fun rememberSwipeToRevealState(
    initialAnchor: SwipeAnchor = SwipeAnchor.Start,
    confirmValueChange: (SwipeAnchor) -> Boolean = { true },
): SwipeToRevealState

enum class SwipeAnchor {
    Start,       // Resting (centered)
    RevealEnd,   // Left-swipe: end actions visible
    RevealStart, // Right-swipe: start actions visible
}

enum class SwipeDirection {
    StartToEnd,  // Right-swipe
    EndToStart,  // Left-swipe
}
```

## Usage Contract

### Node List
```kotlin
SwipeToRevealBox(
    state = rememberSwipeToRevealState(),
    startContent = { RequestPositionAction(node) }, // Right-swipe
    endContent = { MuteNodeAction(node) },          // Left-swipe
    onActionTriggered = { direction ->
        when (direction) {
            SwipeDirection.StartToEnd -> viewModel.requestPosition(node)
            SwipeDirection.EndToStart -> viewModel.muteNode(node)
        }
    }
) {
    NodeListItem(node)
}
```

### Message List
```kotlin
SwipeToRevealBox(
    state = rememberSwipeToRevealState(),
    startContent = {},
    endContent = { DeleteMessageAction(message) },  // Left-swipe only
    enableStartSwipe = false,
    onActionTriggered = { viewModel.deleteMessage(message) }
) {
    MessageListItem(message)
}
```

## Behavioral Contracts

| Behavior | Specification |
|----------|--------------|
| Reveal threshold | 56.dp horizontal drag reveals actions |
| Full-swipe threshold | 160.dp or 50% item width (whichever is smaller) triggers `onActionTriggered` |
| Fling velocity | ≥400.dp/s snaps to nearest anchor |
| Return animation | Spring: stiffness=300f, dampingRatio=0.7f |
| Reveal animation | Spring: stiffness=400f, dampingRatio=0.8f |
| Disabled direction | Drag in disabled direction produces rubber-band resistance (max 16.dp) |
| Accessibility | Swipe actions announced via semantics `customActions` for TalkBack |
| Reduced motion | Instant snap (no spring animation) when `LocalReduceMotion.current` is true |

## Discoverability Hint Contract

```kotlin
/**
 * Modifier that applies a one-shot edge-peek animation to hint at
 * swipe availability.
 *
 * @param enabled Whether the hint should animate (false = no-op)
 * @param peekDistance The distance to peek (default: 24.dp)
 * @param onHintShown Callback after hint animation completes
 */
fun Modifier.swipeHint(
    enabled: Boolean,
    peekDistance: Dp = 24.dp,
    onHintShown: () -> Unit = {},
): Modifier
```

| Behavior | Specification |
|----------|--------------|
| Trigger | First composition when `enabled = true` |
| Animation | Offset item by `peekDistance` over 400ms spring, hold 1000ms, return over 400ms spring |
| Frequency | Once per list screen visit per session (until permanent dismissal) |
| Permanent dismissal | After first successful swipe-to-action, `hasCompletedSwipeAction` = true |
| Reduced motion | Skip animation entirely; rely on a subtle visual indicator (trailing arrow icon) |
