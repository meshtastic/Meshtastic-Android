# Feature Documentation

This document provides detailed technical documentation for custom features added to this fork of Meshtastic-Android.

## Table of Contents
- [Location Sharing Feature](#location-sharing-feature)
- [BLE Reconnection Fix](#ble-reconnection-fix)

---

## Location Sharing Feature

### Overview
Added a location sharing button (📍) to the QuickChat menu that allows users to share their current GPS coordinates as a clickable Google Maps link in conversations.

### User Story
When a user taps the 📍 pin icon in the QuickChat menu, their current GPS coordinates (from the connected Meshtastic node) are inserted into the message as a markdown-formatted link. The displayed text shows coordinates with 4 decimal places for readability, while the embedded URL contains 7 decimal places for precise navigation.

### Implementation Journey

#### Initial Requirements
- Add a tappable map pin icon to the messaging interface
- Insert GPS coordinates as a Google Maps link when tapped
- Use coordinates from the Meshtastic node's GPS (not the phone's GPS)
- Display the link in a clean, user-friendly format

#### Iteration 1: Direct Button Approach (Failed)
**Attempt**: Added a location `IconButton` directly above the `MessageInput` TextField.

**Result**: Button was not visible in the UI.

**Reason for Failure**: The button placement conflicted with the existing UI layout and wasn't integrated with the established message input patterns.

**Lesson Learned**: Need to follow existing UI patterns rather than creating new UI elements.

#### Iteration 2: QuickChat Integration (Partially Successful)
**Attempt**: Added location pin to `QuickChatRow` alongside the bell icon (🔔) using the `QuickChatAction` system.

**Implementation**:
- Created a `QuickChatAction` with:
  - `name = "📍"`
  - `message = "https://maps.google.com/?q={lat},{lon}"`
  - `mode = QuickChatAction.Mode.Append`
  - `position = -2` (special position for built-in actions)

**Result**: App crashed on opening conversation.

**Error**:
```
java.lang.IllegalArgumentException: Key "0" was already used
```

**Root Cause Analysis**:
- The `QuickChatAction` data class has a default `uuid = 0L`
- Both the bell action and location action had `uuid = 0L`
- LazyRow was using `key = { it.uuid }`, causing duplicate key conflict
- The issue occurred because both special actions (bell and location) were dynamically created with the same default UUID

**Fix**: Changed LazyRow key from `it.uuid` to `it.position`:
```kotlin
items(allActions, key = { it.position }) { action ->
```

**Files Modified**:
- `feature/messaging/src/main/kotlin/org/meshtastic/feature/messaging/Message.kt:key = { it.position }`

#### Iteration 3: Smart Cast Issue
**Problem**: Kotlin compilation error:
```
Smart cast to 'Node' is impossible, because 'ourNode' is a property that has open or custom getter
```

**Root Cause**: `ourNode` is a delegated property created with `by collectAsStateWithLifecycle()`, which Kotlin cannot smart cast due to its dynamic nature.

**Fix**: Changed from nullable check to `takeIf`:
```kotlin
// Before (didn't compile):
ourNode?.validPosition?.let { ourNode.latitude }

// After (works):
ourNode?.takeIf { it.validPosition != null }?.latitude
```

#### Iteration 4: Coordinate Precision Consistency
**Problem**: GPS coordinates varied from 7-15 decimal places in the displayed link.

**Root Cause**:
- GPS coordinates are stored as integers in the Meshtastic protocol
- They're multiplied by 1e-7 to convert to degrees
- Kotlin's `Double.toString()` drops trailing zeros
- Example: 37.7749000 becomes "37.7749", but 37.7749295 stays "37.7749295"

**Fix**: Applied consistent formatting:
```kotlin
val formattedLat = "%.7f".format(userLatitude)
val formattedLon = "%.7f".format(userLongitude)
```

**Precision Rationale**:
- 7 decimal places = ~1.1 cm precision (sufficient for GPS accuracy)
- Consistent with Meshtastic protocol's 1e-7 multiplier

#### Iteration 5: Markdown Link Support
**Problem**: User wanted displayed coordinates to be shorter (4 decimals) but URL to maintain full precision (7 decimals).

**Challenge**: Standard auto-linkification doesn't support different display text vs. URL.

**Solution**: Implemented markdown link syntax `[display](url)` support in `AutoLinkText` component.

**Implementation**:

1. **Added `processMarkdownAndLinks()` function** to parse markdown before auto-linkification:
```kotlin
private fun processMarkdownAndLinks(text: String, linkStyles: TextLinkStyles): AnnotatedString {
    val markdownLinks = mutableListOf<MarkdownLink>()
    val markdownPattern = "\\[([^\\]]+)\\]\\(([^)]+)\\)".toRegex()
    var processedText = text
    var offsetAdjustment = 0

    // Extract markdown links
    markdownPattern.findAll(text).forEach { match ->
        val displayText = match.groupValues[1]
        val url = match.groupValues[2]
        val originalStart = match.range.first - offsetAdjustment
        markdownLinks.add(
            MarkdownLink(
                start = originalStart,
                end = originalStart + displayText.length,
                displayText = displayText,
                url = url
            )
        )
        // Replace [text](url) with just text for linkify processing
        processedText = processedText.replaceFirst(match.value, displayText)
        offsetAdjustment += match.value.length - displayText.length
    }

    // Linkify remaining URLs
    val spannable = linkify(processedText)

    // Combine markdown and auto-detected links
    return spannable.toAnnotatedStringWithMarkdown(linkStyles, markdownLinks)
}
```

2. **Updated location action** to use markdown format:
```kotlin
val displayLat = "%.4f".format(userLatitude)
val displayLon = "%.4f".format(userLongitude)
val urlLat = "%.7f".format(userLatitude)
val urlLon = "%.7f".format(userLongitude)

QuickChatAction(
    name = "📍",
    message = "[$displayLat,$displayLon](https://maps.google.com/?q=$urlLat,$urlLon)",
    mode = QuickChatAction.Mode.Append,
    position = -2,
)
```

**Result**:
- Unsent message shows raw markdown: `[37.7749,-122.4194](https://maps.google.com/?q=37.7749295,-122.4194155)`
- Sent message displays clean link: `37.7749,-122.4194` (clickable)
- Click opens Google Maps with full precision coordinates

**Files Modified**:
- `core/ui/src/main/kotlin/org/meshtastic/core/ui/component/AutoLinkText.kt`: Added markdown parsing
- `feature/messaging/src/main/kotlin/org/meshtastic/feature/messaging/Message.kt`: Updated location action

#### Iteration 6: Conversation Preview Cleanup
**Problem**: Conversation list showed full markdown syntax instead of just the shortened coordinates.

**Example**:
```
Conversation Preview:
[37.7749,-122.4194](https://maps.google.com/?q=37.7749295,-122.4194155)
```

**Expected**:
```
Conversation Preview:
37.7749,-122.4194
```

**Root Cause**: The conversation preview text (`lastMessageText`) wasn't processing markdown - it was just showing the raw message text.

**Solution**: Added `stripMarkdownLinks()` function to `ContactsViewModel.kt`:

```kotlin
/**
 * Strips markdown link syntax [text](url) from a string, keeping only the display text.
 * This is used for message previews to show clean text instead of raw markdown.
 */
private fun stripMarkdownLinks(text: String?): String? {
    return text?.replace("\\[([^\\]]+)\\]\\([^)]+\\)".toRegex(), "$1")
}

// Applied to preview text:
Contact(
    lastMessageText = stripMarkdownLinks(
        if (fromLocal) data.text else "$shortName: ${data.text}"
    ),
    // ...
)
```

**Files Modified**:
- `app/src/main/java/com/geeksville/mesh/ui/contact/ContactsViewModel.kt`: Added markdown stripping for previews

### Final Implementation

#### Files Modified

1. **core/ui/src/main/kotlin/org/meshtastic/core/ui/component/AutoLinkText.kt**
   - Added markdown link parsing functionality
   - Supports `[display](url)` syntax
   - Maintains backward compatibility with auto-linkification

2. **feature/messaging/src/main/kotlin/org/meshtastic/feature/messaging/Message.kt**
   - Added `userLatitude` and `userLongitude` parameters to `QuickChatRow`
   - Created dynamic location `QuickChatAction` with position=-2
   - Formats coordinates: 4 decimals for display, 7 for URL
   - Changed LazyRow key to `it.position` to avoid UUID conflicts

3. **app/src/main/java/com/geeksville/mesh/ui/contact/ContactsViewModel.kt**
   - Added `stripMarkdownLinks()` function for conversation previews
   - Applied to `lastMessageText` to show clean text

#### Architecture Decisions

**Why QuickChat instead of dedicated button?**
- Consistent with existing UI patterns
- Users already familiar with QuickChat for inserting pre-defined content
- Leverages existing `QuickChatAction` infrastructure
- Appears alongside other communication shortcuts (bell icon)

**Why markdown links?**
- Allows different display precision vs. URL precision
- Clean visual presentation in sent messages
- Standard, well-understood format
- Easy to strip for previews

**Why position-based LazyRow keys?**
- Positions are guaranteed unique for special actions (negative) and user actions (positive)
- More stable than UUIDs which default to 0L for dynamically created actions
- Prevents duplicate key crashes

**Why 4 vs 7 decimal places?**
- 4 decimals = ~11 meters precision (readable, not overwhelming)
- 7 decimals = ~1.1 cm precision (maximum GPS accuracy)
- Display optimizes for readability, URL optimizes for precision

### Testing Notes

**Tested Scenarios**:
1. ✅ Tapping location pin inserts coordinates
2. ✅ Coordinates display with 4 decimals in unsent message
3. ✅ After sending, message shows clean clickable link
4. ✅ Clicking link opens Google Maps with precise coordinates
5. ✅ Conversation preview shows shortened coordinates only
6. ✅ Works when node has valid GPS position
7. ✅ Location pin hidden when node lacks GPS position
8. ✅ No crashes with multiple special QuickChat actions

**Edge Cases Handled**:
- Node without GPS: Location action not created (`takeIf { it.validPosition != null }`)
- Multiple actions with same UUID: Fixed with position-based keys
- Smart cast issues: Resolved with `takeIf` pattern
- Preview text showing markdown: Stripped with regex replacement

### Code References

**Location action creation**: `feature/messaging/Message.kt:locationAction`
**QuickChatRow invocation**: `feature/messaging/Message.kt:QuickChatRow`
**Markdown parsing**: `core/ui/AutoLinkText.kt:processMarkdownAndLinks`
**Preview stripping**: `app/ContactsViewModel.kt:stripMarkdownLinks`

---

## BLE Reconnection Fix

### Overview
Fixed a critical bug where the app would fail to reconnect to the Meshtastic node via Bluetooth Low Energy (BLE) after a connection drop, requiring users to toggle Android's BLE settings to recover.

### User Story
Users experienced connection failures when the BLE connection to their Meshtastic node was interrupted. After disconnection, attempts to reconnect would fail with a warning message, and the only workaround was to disable and re-enable Bluetooth in Android settings.

### Problem Discovery

#### Initial Report
User reported: "This app was able to connect to BLE on node for a while, and then started failing to connect. Repeated attempts continue to fail. Disabling and re-enabling the BLE device in Android resolves the issue."

#### Log Analysis Process

**Step 1: Locate Error Pattern**
- Examined large logcat file (2MB, ~172,000 lines)
- Searched from end of file backward (recent events)
- Found suspicious warning at line 171861, timestamp 21:37:10.779:

```
W RadioInterfaceService: Ignoring setBondedDevice ...:E4, because we are already using that device
D MeshService: SetDeviceAddress: Device address is unchanged, ignoring.
```

**Step 2: Trace GATT Connection History**
Reconstructed connection timeline from GATT callbacks:

```
21:19:36.956 - Connected (state=2)
21:19:58.987 - Disconnected (state=0, status=8, "Lost connection")
21:25:56.190 - Reconnected (state=2)
21:26:47.226 - Disconnected (state=0, status=19)
21:34:23.885 - Reconnected (state=2)
21:34:53.xxx - [Connection lost - no explicit disconnect logged]
21:37:10.779 - Connection attempt REFUSED: "Ignoring setBondedDevice"
```

**Key Observation**: Multiple successful reconnections followed by eventual failure. The pattern showed the app could reconnect initially but eventually entered a state where it refused new connections.

**Step 3: Analyze Service State Management**

Searched for the warning message in source code:
```bash
$ grep -r "Ignoring setBondedDevice" --include="*.kt"
```

Found in `RadioInterfaceService.kt:319`:
```kotlin
private fun setBondedDeviceAddress(address: String?): Boolean =
    if (getBondedDeviceAddress() == address && isStarted) {
        Timber.w("Ignoring setBondedDevice ${address.anonymize}, because we are already using that device")
        false
    } else {
        // ... proceed with connection
    }
```

### Root Cause Analysis

#### The Bug
The `setBondedDeviceAddress()` function checks if it should allow a new connection by verifying:
1. Is the device address the same? (`getBondedDeviceAddress() == address`)
2. Is the interface started? (`isStarted`)

**Critical Flaw**: The check doesn't verify if the device is **actually connected**.

#### State Management Flow

**Normal Connection Flow**:
1. User selects device → `setDeviceAddress()` called
2. `setBondedDeviceAddress()` checks: address != current OR not started → Proceed
3. `startInterface()` sets `isStarted = true`
4. BluetoothInterface connects via GATT
5. `onConnect()` called → `_connectionState = CONNECTED`

**Connection Loss Flow**:
1. GATT connection drops (device sleep, out of range, etc.)
2. BluetoothInterface detects via `lostConnectCb` callback
3. Calls `scheduleReconnect("connection dropped")`
4. Eventually calls `service.onDisconnect(false)`
5. `onDisconnect()` updates `_connectionState = DEVICE_SLEEP`
6. **BUT**: `isStarted` remains `true` ⚠️

**Reconnection Attempt Flow**:
1. User tries to reconnect → `setDeviceAddress()` called
2. `setBondedDeviceAddress()` checks:
   - Same address? ✅ Yes
   - `isStarted`? ✅ Yes (still true from previous connection)
3. **Result**: "Ignoring setBondedDevice" → Refuses connection
4. User stuck in disconnected state

**Why BLE Toggle Fixes It**:
When user toggles BLE off/on:
1. Android forcefully disconnects all BLE connections
2. App receives BLE state change → `bluetoothRepository.state.enabled = false`
3. Triggers `stopInterface()` in `RadioInterfaceService`
4. `stopInterface()` sets `isStarted = false`
5. Next connection attempt passes the check

### The Fix

#### Solution Design
Instead of only checking `isStarted`, also verify the actual connection state:

```kotlin
private fun setBondedDeviceAddress(address: String?): Boolean =
    if (getBondedDeviceAddress() == address &&
        isStarted &&
        _connectionState.value == ConnectionState.CONNECTED) {  // ← Added this check
        Timber.w("Ignoring setBondedDevice ${address.anonymize}, because we are already using that device")
        false
    } else {
        // ... proceed with connection
    }
```

#### Why This Works

**Three-way Check**:
1. **Same address**: Prevents switching to wrong device
2. **`isStarted`**: Interface infrastructure is running
3. **`_connectionState == CONNECTED`**: **Actually connected via GATT**

**Connection States** (from `ConnectionState` enum):
- `CONNECTED`: Active GATT connection established
- `DEVICE_SLEEP`: Was connected, device went to sleep
- `DISCONNECTED`: No connection, interface stopped

**New Behavior**:
- CONNECTED state → Refuse duplicate connection (correct)
- DEVICE_SLEEP state → Allow reconnection (fixes the bug)
- DISCONNECTED state → Allow reconnection (correct)

### Implementation Details

#### File Modified
**`app/src/main/java/com/geeksville/mesh/repository/radio/RadioInterfaceService.kt`**

Line 318:
```kotlin
// Before:
if (getBondedDeviceAddress() == address && isStarted) {

// After:
if (getBondedDeviceAddress() == address && isStarted && _connectionState.value == ConnectionState.CONNECTED) {
```

#### Related Code Sections

**Connection State Updates**:
```kotlin
// RadioInterfaceService.kt:243
fun onConnect() {
    if (_connectionState.value != ConnectionState.CONNECTED) {
        broadcastConnectionChanged(ConnectionState.CONNECTED)
    }
}

// RadioInterfaceService.kt:249
fun onDisconnect(isPermanent: Boolean) {
    val newTargetState = if (isPermanent) ConnectionState.DISCONNECTED else ConnectionState.DEVICE_SLEEP
    if (_connectionState.value != newTargetState) {
        broadcastConnectionChanged(newTargetState)
    }
}
```

**BluetoothInterface Callbacks**:
```kotlin
// BluetoothInterface.kt:533
safe!!.asyncConnect(
    true,
    cb = ::onConnect,
    lostConnectCb = { scheduleReconnect("connection dropped") }
)

// BluetoothInterface.kt:381
reconnectJob = service.serviceScope.handledLaunch {
    retryDueToException()
}

private suspend fun retryDueToException() {
    // ...
    service.onDisconnect(false) // Sets DEVICE_SLEEP, not DISCONNECTED
    delay(backoffMillis)
    // ...
}
```

### Testing Strategy

**Test Cases**:
1. ✅ Normal connection attempt when disconnected → Should connect
2. ✅ Duplicate connection attempt when already connected → Should refuse (warning)
3. ✅ Reconnection after connection drop → Should connect (bug fix)
4. ✅ Reconnection after device sleep → Should connect (bug fix)
5. ✅ Connection to different device → Should disconnect old, connect new

**Expected Logcat Patterns**:

**Before Fix**:
```
D RadioInterfaceService: SetDeviceAddress: ...
W RadioInterfaceService: Ignoring setBondedDevice ...:E4, because we are already using that device
D MeshService: SetDeviceAddress: Device address is unchanged, ignoring.
[No reconnection happens]
```

**After Fix**:
```
D RadioInterfaceService: SetDeviceAddress: ...
D RadioInterfaceService: Setting bonded device to ...
I RadioInterfaceService: Starting radio ...
I BluetoothInterface: Creating radio interface service
[Connection proceeds normally]
```

### Architecture Impact

#### Minimal Change Philosophy
- Changed only one condition in one function
- No new state variables introduced
- Leverages existing `_connectionState` already tracked by service
- No changes to connection/disconnection callbacks
- No changes to BluetoothInterface

#### Why This Approach?
1. **Correctness**: Uses actual GATT state instead of inferred state
2. **Simplicity**: Single-line change minimizes regression risk
3. **Maintainability**: Obvious intent ("only refuse if truly connected")
4. **Testability**: Easy to verify with state assertions

### Related Components

**RadioInterfaceService** (`app/.../repository/radio/RadioInterfaceService.kt`):
- Central service managing radio interface lifecycle
- Maintains `_connectionState` StateFlow
- Controls interface start/stop
- Handles device address changes

**BluetoothInterface** (`app/.../repository/radio/BluetoothInterface.kt`):
- Implements BLE GATT communication
- Manages connection lifecycle
- Handles reconnection attempts with exponential backoff
- Calls `onConnect()` and `onDisconnect()` callbacks

**ConnectionState** enum:
- `DISCONNECTED`: No active connection
- `DEVICE_SLEEP`: Connection lost temporarily (device sleeping)
- `CONNECTED`: Active GATT connection established

### Lessons Learned

1. **State Management**: Boolean flags (`isStarted`) can become stale; prefer using actual state enums (`ConnectionState`)

2. **Log Analysis**: Connection issues require reconstructing timeline from GATT callbacks, not just looking at errors

3. **Minimal Fixes**: Sometimes the best fix is adding one condition rather than refactoring state management

4. **User Workarounds**: When users find workarounds (BLE toggle), it reveals which state needs resetting

5. **Testing Reconnection**: Connection bugs often only appear after connection drops, not on initial connect

### Future Improvements

**Potential Enhancements** (not implemented):
1. Add connection health monitoring (timeout-based state verification)
2. Implement automatic reconnection when DEVICE_SLEEP detected
3. Add telemetry for connection state transitions
4. Surface connection state to UI for user visibility
5. Add exponential backoff for manual reconnection attempts

**Why Not Implemented Now**:
- Current fix resolves user-reported issue completely
- Additional features increase complexity and testing surface
- Better to validate minimal fix before adding features
- User can manually retry if automatic retry isn't desired

### References

**Primary Fix**: `RadioInterfaceService.kt:318`
**Connection State Enum**: `core/service/.../ConnectionState.kt`
**GATT Callbacks**: `BluetoothInterface.kt:533`
**State Management**: `RadioInterfaceService.kt:243-254`

---

## Commit History

### Feature: Location Sharing
**Branch**: `feature/location-sharing`

**Commit Message**:
```
feat: Add location sharing via QuickChat menu

Implements GPS coordinate sharing with clickable Google Maps links.

Changes:
- Add markdown link support to AutoLinkText component
- Add location pin (📍) to QuickChat menu with position=-2
- Format coordinates: 4 decimals display, 7 decimals URL
- Strip markdown from conversation preview text
- Fix LazyRow key conflict using position instead of uuid

Technical Details:
- Parses [text](url) markdown before auto-linkification
- Retrieves coordinates from Meshtastic node GPS (not phone)
- Only shows location button when node has valid position
- Precision: 4 decimals = ~11m, 7 decimals = ~1.1cm

Files Modified:
- core/ui/src/main/kotlin/org/meshtastic/core/ui/component/AutoLinkText.kt
- feature/messaging/src/main/kotlin/org/meshtastic/feature/messaging/Message.kt
- app/src/main/java/com/geeksville/mesh/ui/contact/ContactsViewModel.kt
```

### Feature: BLE Reconnection Fix
**Branch**: `feature/ble-reconnection-fix`

**Commit Message**:
```
fix: Allow BLE reconnection after connection drop

Fixes bug where app refused to reconnect to Meshtastic node after
BLE connection loss, requiring Android BLE toggle to recover.

Root Cause:
RadioInterfaceService.setBondedDeviceAddress() only checked isStarted
flag, which remained true even after GATT disconnection. This caused
the service to refuse reconnection attempts thinking it was still
connected.

Solution:
Add connection state check to setBondedDeviceAddress() condition:
- Only refuse connection if ACTUALLY connected (CONNECTED state)
- Allow reconnection if DEVICE_SLEEP or DISCONNECTED

Impact:
Users can now reconnect after connection drops without toggling BLE.

Technical Details:
- Changed condition from: isStarted
- To: isStarted && _connectionState.value == ConnectionState.CONNECTED
- Uses existing ConnectionState enum (CONNECTED/DEVICE_SLEEP/DISCONNECTED)
- No new state variables, minimal change for correctness

Files Modified:
- app/src/main/java/com/geeksville/mesh/repository/radio/RadioInterfaceService.kt:318
```

---

## Build Information

**Version**: 2.7.4
**Build Date**: 2025-10-20
**Build Variants**:
- Google (with Google Maps, Firebase)
- F-Droid (OSM maps, no Google services)

**Tested On**:
- Android SDK: 26-36
- Build Tools: Gradle 8.x, Kotlin 2.x
- JDK: 21 (Android Studio JBR)

---

## Contributing

These features were developed in separate branches and can be submitted as independent pull requests:

1. **Location Sharing**: `feature/location-sharing` - Ready for PR
2. **BLE Reconnection**: `feature/ble-reconnection-fix` - Depends on location sharing branch

Both features have been built and are available in the GitHub release:
https://github.com/suteny0r/Meshtastic-Android/releases/tag/v2.7.4-location-sharing

---

**Last Updated**: 2025-10-20
**Fork Maintainer**: suteny0r@gmail.com
**Upstream Project**: https://github.com/meshtastic/Meshtastic-Android
