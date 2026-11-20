# SDK receive replay

This opt-in JVM test runs the same synthetic PhoneAPI asset through the Android replay transport and through an SDK client connected to that transport. Both feed Android's packet processor, data handler, and real Room repositories. The fixture supplies channel state, snapshot installation, the selected database, and session admission. It exercises receipt of a packet during the handshake, notification after the Room install, and the rejection of a delayed packet after a session switch.

Build `:core:jvmJar` at SDK commit `1494611e590c078a626e39e870142b2be513f331`. From the Android checkout, run the test with the resulting JVM jar:

```sh
./gradlew :core:data:jvmTest --tests org.meshtastic.core.data.manager.SdkRoomReplayTest \
  -PsdkReplayJar=/absolute/path/to/core-jvm-0.2.1-feat-session-aware-packet-stream-SNAPSHOT.jar
```

The fixture uses the app's existing protobuf and coroutine dependencies. The SDK jar is on the test classpath only. Production service lifecycle, database association, and live transports are separate integration checks.
