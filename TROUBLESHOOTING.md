# Troubleshooting Guide

This guide documents debugging techniques and solutions for common issues encountered during development of Meshtastic-Android.

## Table of Contents
- [BLE Connection Issues](#ble-connection-issues)
- [UI Compose Issues](#ui-compose-issues)
- [GPS and Location Issues](#gps-and-location-issues)
- [Build Issues](#build-issues)
- [Log Analysis Techniques](#log-analysis-techniques)

---

## BLE Connection Issues

### Problem: App Refuses to Reconnect After Connection Drop

**Symptoms**:
- App connects initially but fails to reconnect after connection loss
- Logcat shows: "Ignoring setBondedDevice ..., because we are already using that device"
- Toggling Android Bluetooth off/on temporarily fixes the issue

**Diagnosis Steps**:

1. **Capture Logcat During Failure**:
```bash
adb logcat -v time > logcat.txt
# Reproduce the issue
# Stop logcat with Ctrl+C
```

2. **Search for Key Error Messages**:
```bash
grep "Ignoring setBondedDevice" logcat.txt
grep "SetDeviceAddress" logcat.txt
```

3. **Reconstruct GATT Connection Timeline**:
```bash
grep "onConnectionStateChange" logcat.txt
grep "state=" logcat.txt | grep "BluetoothGatt"
```

Look for state transitions:
- `state=2`: Connected
- `state=0`: Disconnected
- `status=8`: Lost connection
- `status=19`: Disconnection initiated by peer

4. **Analyze Service State**:
```bash
grep "RadioInterfaceService" logcat.txt | grep -E "(Starting|stopping|onConnect|onDisconnect)"
```

**Common Root Causes**:

1. **Stale Boolean Flags**:
   - Service maintains `isStarted` flag that doesn't reset on connection loss
   - Check: Does state management rely on boolean flags vs. actual connection state?
   - Solution: Use connection state enums instead of boolean flags

2. **Missing Disconnect Callback Handling**:
   - GATT callbacks not updating service state
   - Check: Are `onConnectionStateChange` callbacks properly propagated?
   - Solution: Ensure all disconnect paths call `service.onDisconnect()`

3. **Race Conditions in Reconnection Logic**:
   - Multiple reconnection attempts fighting each other
   - Check: Is `reconnectJob` properly synchronized?
   - Solution: Use mutex or null-check before creating new reconnect jobs

**Fix Pattern**:
```kotlin
// Bad: Only checks boolean flag
if (getBondedDeviceAddress() == address && isStarted) {
    // Refuse connection
}

// Good: Checks actual connection state
if (getBondedDeviceAddress() == address &&
    isStarted &&
    _connectionState.value == ConnectionState.CONNECTED) {
    // Refuse connection
}
```

**Prevention**:
- Always verify actual state, not inferred state
- Add connection state logging at all transitions
- Test reconnection scenarios, not just initial connection
- Use state machines for complex connection logic

---

## UI Compose Issues

### Problem: LazyRow/LazyColumn Crashes with "Key Already Used"

**Symptoms**:
```
java.lang.IllegalArgumentException: Key "0" was already used
```

**Diagnosis Steps**:

1. **Identify the LazyRow/LazyColumn**:
```bash
grep -n "LazyRow\|LazyColumn" --include="*.kt" -r .
# Look for the file/line in stack trace
```

2. **Check Key Lambda**:
```kotlin
LazyRow {
    items(allActions, key = { it.uuid }) { action ->  // ← Check this
        // ...
    }
}
```

3. **Inspect Data Source**:
```kotlin
// Print UUIDs to verify uniqueness
allActions.forEach { println("UUID: ${it.uuid}, Position: ${it.position}") }
```

**Common Root Causes**:

1. **Default Values in Data Classes**:
```kotlin
data class QuickChatAction(
    val uuid: Long = 0L,  // ← Multiple instances will have uuid=0
    val position: Int,
)
```

2. **Dynamically Created Items**:
```kotlin
val dynamicAction = QuickChatAction(
    name = "📍",
    message = "...",
    // uuid not specified → defaults to 0L
)
```

**Solutions**:

1. **Use Alternative Stable Key**:
```kotlin
// Instead of UUID:
items(allActions, key = { it.uuid }) { ... }

// Use position (guaranteed unique):
items(allActions, key = { it.position }) { ... }
```

2. **Generate Unique Keys**:
```kotlin
val locationAction = QuickChatAction(
    uuid = System.currentTimeMillis(),  // Generate unique ID
    // ...
)
```

3. **Use Index as Fallback** (not recommended for dynamic lists):
```kotlin
items(allActions.size) { index ->
    val action = allActions[index]
    // ...
}
```

**Prevention**:
- Avoid default values that could duplicate
- Document key requirements in data classes
- Add `require()` checks for uniqueness in collections
- Test with multiple dynamic items

### Problem: Smart Cast Impossible for Compose State

**Symptoms**:
```
Smart cast to 'Node' is impossible, because 'ourNode' is a property that has open or custom getter
```

**Example**:
```kotlin
val ourNode by viewModel.ourNode.collectAsStateWithLifecycle()

// Later:
ourNode?.validPosition?.let {
    ourNode.latitude  // ← Error: Smart cast impossible
}
```

**Root Cause**:
- Delegated properties (`by`) have custom getters
- Kotlin cannot guarantee the value won't change between checks
- Even though we're in a `let` block, compiler sees potential race condition

**Solutions**:

1. **Use `takeIf` Pattern** (Recommended):
```kotlin
ourNode?.takeIf { it.validPosition != null }?.latitude
```

2. **Capture to Local Variable**:
```kotlin
val node = ourNode
node?.validPosition?.let {
    node.latitude  // ← Smart cast works now
}
```

3. **Use Safe Calls Throughout**:
```kotlin
ourNode?.validPosition?.let {
    ourNode?.latitude  // ← Safe call, not smart cast
}
```

**Prevention**:
- Prefer `takeIf`/`takeUnless` for conditional state access
- Capture state to local variables for complex logic
- Avoid relying on smart casts with reactive state

---

## GPS and Location Issues

### Problem: Coordinate Precision Inconsistent

**Symptoms**:
- Coordinates show 7 decimal places sometimes, 15 decimal places other times
- Example: "37.7749" vs "37.7749295000000"

**Root Cause**:
```kotlin
val lat = position.latitudeI * 1e-7  // 377749295 * 1e-7 = 37.7749295
println(lat)  // Kotlin's toString() drops trailing zeros
```

**Diagnosis**:
```kotlin
// Test with various coordinates
val coords = listOf(377749000, 377749295)
coords.forEach {
    val degrees = it * 1e-7
    println("$it → $degrees")  // See inconsistent precision
}
```

**Solution**:
```kotlin
// Bad: Inconsistent precision
val coordString = (latitudeI * 1e-7).toString()

// Good: Explicit precision
val coordString = "%.7f".format(latitudeI * 1e-7)
```

**Precision Guidelines**:
- **7 decimal places**: ~1.1 cm precision (GPS maximum)
- **4 decimal places**: ~11 meter precision (readable for users)
- **2 decimal places**: ~1.1 km precision (city-level)

### Problem: Location Button Not Visible

**Symptoms**:
- Added location button but it doesn't appear in UI
- No crashes or errors

**Diagnosis Steps**:

1. **Check Conditional Rendering**:
```kotlin
// Is the condition ever true?
if (userLatitude != null && userLongitude != null) {
    // Button should appear here
}
```

2. **Add Debug Logging**:
```kotlin
Timber.d("GPS Debug - lat: $userLatitude, lon: $userLongitude, validPos: ${ourNode?.validPosition}")
```

3. **Verify Data Flow**:
```kotlin
// Trace from source to UI
ourNode?.position?.latitudeI  // Source
  → ourNode?.latitude          // Computed property
  → userLatitude parameter     // Passed to composable
  → if (userLatitude != null)  // Conditional check
```

**Common Root Causes**:

1. **GPS Data Not Loaded**:
```kotlin
// Node exists but position not yet received
ourNode != null  // ✓
ourNode.position != null  // ✓
ourNode.validPosition != null  // ✗ (requires valid lat/lon)
```

2. **Null Propagation Issue**:
```kotlin
// Bad: Smart cast issue
ourNode?.validPosition?.let { ourNode.latitude }  // Returns null

// Good: Proper propagation
ourNode?.takeIf { it.validPosition != null }?.latitude  // Returns lat
```

3. **Parameter Not Passed**:
```kotlin
// Button defined with parameters:
fun LocationButton(latitude: Double?, longitude: Double?)

// But called without them:
LocationButton()  // Missing required parameters
```

**Solution Checklist**:
- ✓ GPS data available from node
- ✓ `validPosition` check passes
- ✓ Parameters passed to composable
- ✓ Conditional rendering logic correct
- ✓ No layout constraints hiding button

---

## Build Issues

### Problem: Gradle Requires JVM 17+

**Symptoms**:
```
Gradle requires JVM 17 or later to run. Your build is currently configured to use JVM 8.
```

**Solution**:
```bash
# Set JAVA_HOME to Android Studio's bundled JDK
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
./gradlew assembleGoogleDebug
```

**Permanent Fix (Linux/Mac)**:
```bash
# Add to ~/.bashrc or ~/.zshrc
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

**Permanent Fix (Windows)**:
```powershell
# Add to PowerShell profile
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

### Problem: Google Maps Not Loading

**Symptoms**:
- Map screen shows only legend, no map tiles
- Logcat: "API Key: DEFAULT_API_KEY"
- Error: "INVALID_ARGUMENT"

**Diagnosis**:
```bash
grep "Maps Android API" logcat.txt
grep "API Key" logcat.txt
```

**Solution**:

1. **Create `secrets.properties`**:
```properties
# In project root
MAPS_API_KEY=AIzaSy...your-actual-key...
```

2. **Verify `.gitignore`**:
```bash
grep "secrets.properties" .gitignore
# Should be present to prevent committing API key
```

3. **Rebuild**:
```bash
./gradlew clean assembleGoogleDebug
```

4. **Verify Key in BuildConfig**:
```kotlin
// Check in generated code
BuildConfig.MAPS_API_KEY  // Should not be "DEFAULT_API_KEY"
```

---

## Log Analysis Techniques

### Analyzing Large Logcat Files

**Challenge**: 2MB+ logcat files with 100k+ lines

**Technique 1: Work Backward from Recent Events**
```bash
# Get last 1000 lines (most recent)
tail -1000 logcat.txt > recent.txt

# Search for errors in recent events
grep -E "ERROR|FATAL|Exception" recent.txt
```

**Technique 2: Extract Timeline for Specific Component**
```bash
# Get all RadioInterfaceService logs
grep "RadioInterfaceService" logcat.txt > radio_timeline.txt

# Get all GATT connection events
grep "onConnectionStateChange" logcat.txt > gatt_timeline.txt
```

**Technique 3: Search for Error Patterns**
```bash
# Find all warnings and errors
grep -E "^W |^E " logcat.txt > warnings_errors.txt

# Find specific error message
grep -A 10 -B 5 "Ignoring setBondedDevice" logcat.txt
# -A 10: Show 10 lines after match
# -B 5: Show 5 lines before match
```

**Technique 4: Reconstruct State Machine**
```bash
# Extract state transitions
grep "ConnectionState" logcat.txt | \
  grep -E "CONNECTED|DISCONNECTED|DEVICE_SLEEP" | \
  cut -d' ' -f1,2,6- > connection_states.txt
```

### Effective Logging Practices

**Add Contextual Logging**:
```kotlin
// Bad: Minimal context
Timber.d("Connected")

// Good: Rich context
Timber.d("BLE connected to ${address.anonymize}, state=$connectionState, attempts=$reconnectAttempts")
```

**Log State Transitions**:
```kotlin
fun setConnectionState(newState: ConnectionState) {
    val oldState = _connectionState.value
    Timber.i("Connection state: $oldState → $newState")
    _connectionState.value = newState
}
```

**Use Structured Logging**:
```kotlin
// Use consistent prefixes for easy grepping
Timber.d("GPS | lat=$lat, lon=$lon, valid=$validPosition")
Timber.d("BLE | device=${address.anonymize}, state=$state")
Timber.d("UI | screen=$screen, action=$action")
```

**Log Entry/Exit Points**:
```kotlin
fun setBondedDeviceAddress(address: String?): Boolean {
    Timber.d("setBondedDeviceAddress | address=${address.anonymize}, current=${getBondedDeviceAddress().anonymize}, started=$isStarted, state=${_connectionState.value}")

    val result = if (...) {
        Timber.w("Refusing connection - already using device")
        false
    } else {
        Timber.i("Proceeding with device change")
        true
    }

    Timber.d("setBondedDeviceAddress | result=$result")
    return result
}
```

### Debugging Compose UI Issues

**Enable Compose Metrics**:
```kotlin
// In build.gradle.kts
kotlinOptions {
    freeCompilerArgs += listOf(
        "-P",
        "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${project.buildDir}/compose_metrics",
        "-P",
        "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${project.buildDir}/compose_metrics"
    )
}
```

**Add Recomposition Logging**:
```kotlin
@Composable
fun MyComposable() {
    val recompositionCount = remember { mutableStateOf(0) }

    SideEffect {
        recompositionCount.value++
        Timber.d("MyComposable recomposed ${recompositionCount.value} times")
    }

    // ... rest of composable
}
```

**Debug State Changes**:
```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        Timber.d("State changed to: $state")
    }

    // ... rest of screen
}
```

---

## Common Patterns and Anti-Patterns

### Pattern: State Management

**✅ Good**:
```kotlin
// Use state enum
enum class ConnectionState { CONNECTED, DEVICE_SLEEP, DISCONNECTED }
val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)

// Check actual state
if (connectionState.value == ConnectionState.CONNECTED) {
    // ...
}
```

**❌ Bad**:
```kotlin
// Use boolean flags
var isConnected = false
var isStarted = false

// Flags can become inconsistent
if (isStarted) {  // Might be true even when not connected
    // ...
}
```

### Pattern: Null Safety

**✅ Good**:
```kotlin
// Use safe calls and takeIf
val latitude = ourNode?.takeIf { it.validPosition != null }?.latitude
```

**❌ Bad**:
```kotlin
// Rely on smart casts with delegated properties
ourNode?.validPosition?.let {
    ourNode.latitude  // Compiler error: smart cast impossible
}
```

### Pattern: Error Handling

**✅ Good**:
```kotlin
try {
    doRiskyOperation()
} catch (ex: SpecificException) {
    Timber.e(ex, "Context about what failed and current state: $state")
    // Handle gracefully
}
```

**❌ Bad**:
```kotlin
try {
    doRiskyOperation()
} catch (ex: Exception) {
    // Silent failure or generic error
}
```

---

## When to Seek Help

### Check These First:
1. Search existing GitHub issues in upstream project
2. Search Discord/forum for similar problems
3. Review recent commits for related changes
4. Check if issue reproduces on upstream main branch

### Gather This Information:
- Android version
- Device model
- Meshtastic firmware version
- Full logcat from issue occurrence
- Steps to reproduce
- Screenshots/screen recordings

### Where to Ask:
- **Bug reports**: GitHub Issues
- **Questions**: Discord or GitHub Discussions
- **Feature requests**: GitHub Discussions first, then Issues

---

**Last Updated**: 2025-10-20
**Maintainer**: suteny0r@gmail.com
