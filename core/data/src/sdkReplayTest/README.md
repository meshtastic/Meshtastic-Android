# SDK receive replay

This opt-in JVM test runs the same synthetic PhoneAPI asset through the Android replay transport and through an SDK client connected to that transport. Both feed Android's packet processor, data handler, and real Room repositories. The fixture supplies channel state, snapshot installation, the selected database, and session admission. It exercises receipt of a packet during the handshake, notification after the Room install, and the rejection of a delayed packet after a session switch.

Build `:core:jvmJar` at SDK commit `d54ced912daebb76717358db0425b3908c52b782`. From the Android checkout, run the test with the resulting JVM jar:

```sh
./gradlew :core:data:jvmTest --tests org.meshtastic.core.data.manager.SdkRoomReplayTest \
  -PsdkReplayJar=/absolute/path/to/sdk-core-jvm.jar
```

The fixture uses the app's existing protobuf and coroutine dependencies. The SDK jar is on the test classpath only. The deterministic replay supplies its own session authority and database provider.

## TCP snapshot installation

Build `:core:jvmJar :transport-tcp:jvmJar` at the same SDK commit. The TCP test uses the SDK's real socket transport, Android's session admission and snapshot installer, and temporary Room databases. The default peer serves the shared replay asset on an ephemeral loopback port. It verifies the Room snapshot before the retained text can generate a notification, and tries the old snapshot again after replacing the app session and database.

```sh
./gradlew :core:data:jvmTest --tests org.meshtastic.core.data.manager.SdkTcpRoomTest \
  -PsdkReplayJar=/absolute/path/to/sdk-core-jvm.jar \
  -PsdkTcpJar=/absolute/path/to/sdk-transport-tcp-jvm.jar
```

To check a running PhoneAPI server or test radio, set `MESHTASTIC_SDK_TCP_HOST` and optionally `MESHTASTIC_SDK_TCP_PORT` (default 4403). Add `--rerun --no-build-cache` for an endpoint run. This mode checks the installed snapshot and retired session without requiring a particular incoming text. The harness disables clock sync and uses the SDK's in-memory storage. The app's normal backend selection remains outside this test.

## Android debug backend

Build against the same SDK commit. The SDK composite currently needs Isolated Projects and its configuration cache disabled.

```sh
./gradlew :androidApp:assembleFdroidDebug \
  -PmeshtasticSdkCheckout=/absolute/path/to/meshtastic-sdk \
  -Dorg.gradle.isolated-projects=false --no-configuration-cache
```

In that debug APK, create the app-private flag before selecting the TCP address in Connections.

```sh
adb install -r androidApp/build/outputs/apk/fdroid/debug/<apk>
adb shell "run-as com.geeksville.mesh.fdroid.debug sh -c 'mkdir -p files && touch files/enable-sdk-tcp'"
```

The app selects the backend for each fresh transport. To return to legacy TCP in the same APK, disconnect, remove the flag with `adb shell run-as com.geeksville.mesh.fdroid.debug rm files/enable-sdk-tcp`, then reconnect. SDK TCP keeps one client per app session and leaves outgoing message state and Room ownership with Android. SDK packet and post-handshake envelope events share the bounded receive queue. A peer socket close or a reboot envelope on an open socket retires the app session and opens a fresh SDK client for the same address. Android drains the old session's leases before publishing a replacement generation and retries with backoff from one to 30 seconds. An explicit disconnect or selection change supersedes the retry. Receive overflow reports the history gap and requires manual reconnection. In-session node-list refresh remains open.
