# Testing the Car (Android Auto) feature

Two ways to verify `feature:car`. Use the unit tests for correctness; use the DHU only when you
want to eyeball the real in-car experience end to end.

## 1. Automated — `SessionController` tests (no hardware, runs in CI)

```bash
./gradlew :feature:car:testGoogleDebugUnitTest
```

`CarScreensTest` drives the real `HomeScreen` through the androidx.car.app testing context, pushing
state via the `CarStateCoordinator` test seam (`setStateForTest`), and asserts the right template
renders per connection state plus the `ALERT_APP` → emergency flow. This is what catches regressions
(e.g. it caught the `TabTemplate.setActiveTabContentId` crash the 1.7.0 pin introduced). Robolectric
is pinned to `@Config(sdk = [36])` because no Robolectric SDK-37 sandbox exists yet — harmless, the
templates are SDK-agnostic.

## 2. Visual — Desktop Head Unit (DHU)

### Platform matters
DHU decodes an H.264 video stream from the phone. **macOS Apple Silicon (`2.1-mac-arm64`) fails at
this step** — it connects and reports `Has video focus: true` but renders no frames. Use an
**x86_64 Linux (or Windows) host**; `linux-arm64` DHU does not exist, and an emulated x86 VM has no
hardware decode (same failure). A VM is fine **only on an x86_64 host with real virtualization**.

### Phone prep (one time)
1. Settings → About → tap *Build number* ×7 → enable **USB debugging** and **Wireless debugging**.
2. Open Android Auto settings (on a Pixel: `adb shell am start -n com.google.android.projection.gearhead/.companion.settings.DefaultSettingsActivity`),
   tap *Version* ~10× to unlock **Developer settings**, then enable **Start head unit server**.

### On the x86_64 box
```bash
sdkmanager "extras;google;auto"                 # installs the linux-x86_64 DHU
# Connect to the phone over wifi (no re-plugging; works even if the phone lives on another machine):
adb pair <phone-ip>:<pair-port>                 # one-time; code shown under Wireless debugging
adb connect <phone-ip>:<debug-port>
adb forward tcp:5277 tcp:5277
cd "$ANDROID_SDK_ROOT/extras/google/auto" && ./desktop-head-unit
```
DHU commands: type `help` in its shell; `focus video`, `tap <x> <y>`, `screenshot <file>`.

### Caveat: our app has no car-launcher tile
`feature:car` is a **MESSAGING**-category app. It does not appear as an icon on the Android Auto
home — it surfaces when a **message notification** arrives. To see the `ConversationItem` UI in the
DHU you must have a **paired Meshtastic radio sending a text**; the notification (read-aloud + reply)
is the entry point. Without a radio you'll see the AA home, not our screens. (For the same reason,
the AAOS emulator is not a fit either: messaging isn't an AAOS distribution category, and the app is
projection-only — no `CarAppActivity`.)
