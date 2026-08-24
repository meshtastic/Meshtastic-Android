# Quickstart: Car App Library Integration

**Feature**: Car App Library Integration
**Date**: 2026-05-21

## Prerequisites

- Android Studio Ladybug or newer (for CAL preview tools)
- JDK 21 (`JAVA_HOME` set)
- `ANDROID_HOME` set with API 35+ SDK installed
- Proto submodule initialized: `git submodule update --init`
- `local.properties` configured: `cp secrets.defaults.properties local.properties`
- Android Auto Desktop Head Unit (DHU) installed via SDK Manager → SDK Tools → Android Auto Desktop Head Unit

## Setup

### 1. Sync and Build

```bash
# Full sync (includes new :feature:car module)
./gradlew sync

# Build google flavor (required — car module is google-only)
./gradlew assembleGoogleDebug
```

### 2. Install DHU for Testing

The Desktop Head Unit simulates Android Auto on your development machine.

```bash
# Install via SDK Manager (or command line)
sdkmanager "extras;google;auto"

# Start DHU (after connecting a device/emulator with the app installed)
$ANDROID_HOME/extras/google/auto/desktop-head-unit
```

### 3. Run on Android Auto (Projection Mode)

1. Install the google debug build on a physical device: `./gradlew installGoogleDebug`
2. Enable Developer Mode in Android Auto settings on the phone
3. Start the DHU: `desktop-head-unit`
4. The Meshtastic car app appears in the DHU's app launcher under "Messaging" category

### 4. Run on AAOS Emulator

```bash
# Create AAOS emulator (API 33+ automotive system image)
avdmanager create avd -n "AAOS_Test" -k "system-images;android-33;google_apis_playstore;x86_64" --device "automotive_1024p_landscape"

# Start emulator
emulator -avd AAOS_Test

# Install
./gradlew installGoogleDebug
```

## Development Workflow

### Module Location

All car-specific code lives in `feature/car/`:

```
feature/car/src/main/kotlin/org/meshtastic/feature/car/
├── di/              → Koin DI module
├── service/         → CarAppService + Session
├── screens/         → CAL Screen implementations
├── alerts/          → Emergency banner handler
├── panels/          → Minimized Control Panel
└── util/            → Helpers (Crashlytics tagger, template builders)
```

### Key Development Patterns

**Screen implementation**:
```kotlin
class MessagingScreen(carContext: CarContext) : Screen(carContext) {
    // Inject repositories via Koin
    private val packetRepository: PacketRepository by inject()

    override fun onGetTemplate(): Template {
        // Build template from current state
        // Call invalidate() when data changes to trigger re-render
    }
}
```

**Data observation** (CAL doesn't use Compose — use coroutine collection):
```kotlin
// In Screen's lifecycle, collect flows and call invalidate()
lifecycleScope.launch {
    repository.getContacts().collect { contacts ->
        cachedContacts = contacts
        invalidate()  // Triggers onGetTemplate() re-call
    }
}
```

**Template refresh**: CAL screens are invalidated manually — no reactive binding. Call `invalidate()` whenever backing data changes.

### Testing

```bash
# Unit tests (uses androidx.car.app:app-testing)
./gradlew :feature:car:testGoogleDebugUnitTest

# Lint + formatting
./gradlew :feature:car:spotlessApply :feature:car:spotlessCheck :feature:car:detekt
```

**Test approach**: Use `SessionController` and `TestCarContext` from `app-testing` artifact to simulate host interactions without a real car/DHU.

```kotlin
@Test
fun `messaging screen shows conversations`() {
    val controller = SessionController(
        MeshtasticCarSession(testSessionInfo),
        TestCarContext(ApplicationProvider.getApplicationContext())
    )
    // Push screen, assert template content
}
```

### Debugging

- **CAL Logcat filter**: `tag:CarApp OR tag:CarService`
- **Template errors**: CAL validates templates at runtime — check logcat for `TemplateValidationException`
- **Screen stack**: Use `ScreenManager.getTop()` to inspect current screen
- **Crashlytics**: Filter by `car_session` custom key in Firebase Console

## Common Tasks

| Task | Command / Action |
|------|------------------|
| Add a new screen | Create `Screen` subclass in `screens/`, register in navigation |
| Add a CAL dependency | Update `gradle/libs.versions.toml` + `feature/car/build.gradle.kts` |
| Test with DHU | `desktop-head-unit` after installing google debug build |
| Check template compliance | Run app on DHU; host validates template constraints |
| Filter car crashes | Firebase Console → Crashlytics → Filter: `car_session` is not empty |
| Full verification | `./gradlew spotlessApply :feature:car:spotlessCheck :feature:car:detekt :feature:car:testGoogleDebugUnitTest` |

## Architecture Notes

- **No Compose**: CAL uses its own template-based rendering. Don't mix Compose APIs.
- **No `commonMain`**: This is an Android-only module. All code in `src/main/kotlin/`.
- **Shared BLE**: Don't create new BLE connections. Inject existing `BleConnection` singleton.
- **Koin DI**: All core repositories are already in the graph. Just `inject()` them.
- **Flavor**: Only `google` flavor includes this module. Never reference it from `fdroid` code.
