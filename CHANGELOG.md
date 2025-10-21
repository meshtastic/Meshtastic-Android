# Changelog

All notable changes to this fork will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Location sharing button (📍) in QuickChat menu for inserting GPS coordinates as Google Maps links
- Markdown link support in AutoLinkText component for custom display text with embedded URLs
- Conversation preview text now strips markdown syntax for clean display

### Fixed
- BLE reconnection failures after connection drop - app now allows reconnection without requiring Android BLE toggle

### Changed
- QuickChatRow LazyRow key changed from `uuid` to `position` to prevent duplicate key crashes
- GPS coordinates display with 4 decimal places for readability, 7 decimal places in URL for precision

## [2.7.4-location-sharing] - 2025-10-20

### Added

#### Location Sharing Feature
- **QuickChat Location Button**: Added 📍 pin icon to QuickChat menu for quick GPS coordinate sharing
  - Displays alongside existing bell icon (🔔)
  - Only visible when connected Meshtastic node has valid GPS position
  - Inserts coordinates as clickable Google Maps link
  - Uses node's GPS coordinates, not phone location

- **Markdown Link Support**:
  - Enhanced `AutoLinkText` component with `[display](url)` markdown syntax parsing
  - Allows different precision for display vs. URL
  - Display text: 4 decimal places (~11 meter precision)
  - URL text: 7 decimal places (~1.1 cm precision)
  - Example: `[37.7749,-122.4194](https://maps.google.com/?q=37.7749295,-122.4194155)`

- **Preview Text Cleanup**:
  - Added `stripMarkdownLinks()` function in ContactsViewModel
  - Conversation previews now show shortened coordinates instead of raw markdown
  - Before: `[37.7749,-122.4194](https://maps.google.com/?q=...)`
  - After: `37.7749,-122.4194`

**Technical Details**:
- Files modified:
  - `core/ui/src/main/kotlin/org/meshtastic/core/ui/component/AutoLinkText.kt`
  - `feature/messaging/src/main/kotlin/org/meshtastic/feature/messaging/Message.kt`
  - `app/src/main/java/com/geeksville/mesh/ui/contact/ContactsViewModel.kt`
- Location action uses `position=-2` for stable ordering
- Markdown regex: `\[([^\]]+)\]\(([^)]+)\)`

**User Impact**:
- Quick way to share location in mesh network
- Clean, readable coordinate display
- One-tap access to Google Maps navigation
- Works offline - coordinates copied even without internet

### Fixed

#### BLE Reconnection Bug
- **Problem**: App refused to reconnect to Meshtastic node after BLE connection dropped
  - Error: "Ignoring setBondedDevice ...:E4, because we are already using that device"
  - Workaround required: Toggle Android Bluetooth off/on to reset state
  - Affected users experiencing intermittent connections or device sleep

- **Root Cause**:
  - `RadioInterfaceService.setBondedDeviceAddress()` checked `isStarted` flag
  - Flag remained `true` after GATT connection loss
  - Service thought it was still connected when actually disconnected
  - Connection state was `DEVICE_SLEEP` but service refused reconnection

- **Solution**:
  - Added connection state verification to setBondedDeviceAddress check
  - Now verifies actual GATT connection state (CONNECTED vs DEVICE_SLEEP/DISCONNECTED)
  - Only refuses reconnection if truly connected
  - Changed condition: `isStarted` → `isStarted && _connectionState.value == ConnectionState.CONNECTED`

**Technical Details**:
- File modified:
  - `app/src/main/java/com/geeksville/mesh/repository/radio/RadioInterfaceService.kt:318`
- Single-line fix for minimal regression risk
- Leverages existing ConnectionState enum
- No new state variables introduced

**User Impact**:
- No more BLE toggle required after connection drops
- Seamless reconnection after device sleep or range issues
- More reliable mesh network connectivity
- Better user experience with intermittent BLE

### Changed

#### QuickChat LazyRow Key
- **Changed from**: `key = { it.uuid }`
- **Changed to**: `key = { it.position }`
- **Reason**: Dynamically created QuickChatActions (bell, location) both had default `uuid=0L`
- **Impact**: Prevents "Key '0' was already used" crashes
- **Benefit**: More stable keys since positions are unique (negative for special, positive for user)

#### GPS Coordinate Formatting
- **Display precision**: 4 decimal places (was inconsistent 7-15)
- **URL precision**: 7 decimal places (was inconsistent 7-15)
- **Reason**: Kotlin's Double.toString() drops trailing zeros
- **Solution**: Explicit formatting with `"%.4f"` and `"%.7f"`
- **Benefit**: Consistent, predictable coordinate display

## Development Notes

### Branch Structure
```
main (upstream)
  └── feature/location-sharing
       └── feature/ble-reconnection-fix
```

### Build Variants
- **Google**: Includes Google Maps, Firebase Crashlytics
- **F-Droid**: Uses OSM maps, no Google services

### Testing Checklist

#### Location Sharing
- [x] Location button appears when node has GPS
- [x] Location button hidden when node lacks GPS
- [x] Coordinates inserted with correct precision
- [x] Google Maps link opens correctly
- [x] Conversation preview shows shortened coordinates
- [x] No LazyRow key conflicts
- [x] Works with multiple quick chat actions

#### BLE Reconnection
- [x] Normal connection succeeds
- [x] Duplicate connection attempt refused (when connected)
- [x] Reconnection after connection drop succeeds
- [x] Reconnection after device sleep succeeds
- [x] No BLE toggle required
- [x] Logcat shows correct state transitions

### Known Issues
None currently.

### Future Enhancements
Potential improvements not implemented in this release:
- Automatic reconnection after DEVICE_SLEEP detection
- Connection state visibility in UI
- Connection health monitoring with timeouts
- Additional markdown features (bold, italic)
- Coordinate format preferences (DMS, UTM)

---

## Release Links

**GitHub Release**: [v2.7.4-location-sharing](https://github.com/suteny0r/Meshtastic-Android/releases/tag/v2.7.4-location-sharing)

**APK Downloads**:
- `app-google-debug.apk` - Google variant with Maps API
- `app-fdroid-debug.apk` - F-Droid variant with OSM

**Source Branches**:
- Location Sharing: `feature/location-sharing`
- BLE Fix: `feature/ble-reconnection-fix`

---

**Upstream Project**: [meshtastic/Meshtastic-Android](https://github.com/meshtastic/Meshtastic-Android)
**Fork Maintainer**: suteny0r@gmail.com
**Last Updated**: 2025-10-20
