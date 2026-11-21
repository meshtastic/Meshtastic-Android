# SDK engine checks

Build against the cutover SDK checkout.

```sh
./gradlew :core:data:jvmTest :androidApp:assembleFdroidDebug \
  -PmeshtasticSdkCheckout=/absolute/path/to/meshtastic-sdk \
  -Dorg.gradle.isolated-projects=false --no-configuration-cache
```

The JVM tests replay the two-stage PhoneAPI handshake and use a loopback TCP peer to verify the
SDK session stream, Room snapshot, NodeDB refresh, and stale-session rejection. To run the TCP
snapshot test against a radio, set `MESHTASTIC_SDK_TCP_HOST` and optionally `MESHTASTIC_SDK_TCP_PORT`
(default 4403), then add `--rerun --no-build-cache`.

Android uses the SDK on TCP, BLE, USB and WebSocket. Desktop uses it on TCP, BLE, serial and
WebSocket. The browser uses it on WebSocket, BLE and Web Serial. Its Kable and Web Serial
grants remain with the app, as do selection, host permissions and Room storage.
