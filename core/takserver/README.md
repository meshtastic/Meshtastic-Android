# `:core:takserver`

## Overview

The `:core:takserver` module implements the **Meshtastic ↔ TAK (Team Awareness Kit) bridge**. It embeds an mTLS TCP server (port 8089) compatible with ATAK (Android), iTAK (iOS), and WinTAK clients, enabling mesh-networked position sharing and GeoChat with TAK-enabled devices.

**Targets:** Android · JVM (Desktop) · iOS — fully multiplatform with `expect`/`actual` splits for compression, file I/O, and the TCP server itself.

## Key Responsibilities

- Serve an mTLS TCP listener (port 8089) compatible with the CoT (Cursor-on-Target) protocol
- Convert Meshtastic protobuf packets (`TAKPacketV2`) to CoT XML events and vice versa
- Generate ATAK Data Package `.zip` exports (team contacts, map overlays)
- Compress CoT payloads using Zstd (TAK SDK format) with `expect`/`actual` platform implementations
- Buffer up to 50 CoT messages for 5 minutes when no TAK clients are connected; drain on reconnect
- Provide Crowdin-localised TAK preference XML for ATAK client provisioning

## Source Structure

```
src/
├── commonMain/kotlin/org/meshtastic/core/takserver/
│   ├── TAKServer.kt                 ← interface + expect createTAKServer()
│   ├── TAKServerManager.kt          ← interface + TAKServerManagerImpl (offline queue)
│   ├── TAKMeshIntegration.kt        ← bridges mesh service ↔ TAK server
│   ├── CoTConversion.kt             ← Position/User → CoTMessage extension fns
│   ├── CoTXml.kt / CoTXmlParser.kt / CoTXmlFrameBuffer.kt
│   ├── CoTXmlDataClasses.kt
│   ├── CoTDetailStripper.kt
│   ├── TAKModels.kt                 ← CoTMessage, TAKClientInfo, TAKConnectionEvent
│   ├── TAKPacketConversion.kt
│   ├── TAKPacketV2Conversion.kt
│   ├── TAKDefaults.kt
│   ├── TAKDataPackageGenerator.kt
│   ├── RouteDataPackageGenerator.kt
│   ├── TAKPrefXmlDataClasses.kt
│   ├── TakV2TypeMapper.kt
│   ├── TakConversionHelpers.kt
│   ├── XmlUtils.kt
│   ├── AtakFileWriter.kt            ← expect
│   ├── TakSdkCompressor.kt          ← expect (Zstd TAK-SDK frame)
│   ├── TakV2Compressor.kt           ← expect (Zstd TAKPacketV2 frame)
│   ├── ZipArchiver.kt               ← expect
│   ├── TakFixtureLoader.kt          ← expect (test fixtures)
│   ├── TakMeshTestRunner.kt
│   └── di/
│       └── CoreTakServerModule.kt
├── jvmAndroidMain/kotlin/           ← actual TAKServerJvm, TAKClientConnection, TakCertLoader
├── androidMain/kotlin/              ← actual AtakFileWriter (Android)
├── jvmMain/kotlin/                  ← actual AtakFileWriter (Desktop), XML pull-parser
└── iosMain/kotlin/                  ← actual TAKServerIos, actual compression impls
```

## Notable APIs

### `TAKServer` (interface)

```kotlin
interface TAKServer {
    val connectionCount: StateFlow<Int>
    var onMessage: ((CoTMessage, TAKClientInfo?) -> Unit)?
    var onClientConnected: (() -> Unit)?

    suspend fun start(scope: CoroutineScope): Result<Unit>
    fun stop()
    suspend fun broadcast(cotMessage: CoTMessage)
    suspend fun broadcastRawXml(xml: String)
    suspend fun hasConnections(): Boolean
}
```

The mTLS listener binds on port 8089 using a bundled `server.p12` / `ca.pem` identity, compatible with the ATAK Data Package provisioning flow.

### `TAKServerManager` (interface)

```kotlin
interface TAKServerManager {
    val isRunning: StateFlow<Boolean>
    val connectionCount: StateFlow<Int>
    val inboundMessages: SharedFlow<InboundCoTMessage>

    suspend fun start(scope: CoroutineScope)
    fun stop()
    suspend fun broadcast(cotMessage: CoTMessage)
    suspend fun broadcastRawXml(xml: String)
}
```

`TAKServerManagerImpl` adds an **offline queue**: buffers up to 50 CoT messages for 5 minutes when no clients are connected and drains them automatically on the next `onClientConnected` callback.

### `CoTMessage`

```kotlin
@Serializable
data class CoTMessage(
    val uid: String,
    val type: String,              // e.g. "a-f-G-U-C" (friendly ground unit)
    val time: Instant,
    val lat: Double, val lon: Double, val hae: Double,
    val contact: CoTContact?,
    val group: CoTGroup?,
    val track: CoTTrack?,
    val chat: CoTChat?,
    val remarks: String?,
    // ...
)

// Factory helpers
CoTMessage.pli(uid, callsign, lat, lon, ...)   // Position Location Information
CoTMessage.chat(senderUid, callsign, message, chatroom)
```

### CoT Conversion

```kotlin
// Meshtastic proto → CoT
org.meshtastic.proto.Position.toCoTMessage(uid, callsign, team, role, battery): CoTMessage
org.meshtastic.proto.User.toCoTMessage(position, team, role, battery): CoTMessage
```

## Dependency Graph

```
core:takserver
  ├── api → core:repository     (exported)
  ├── core:common, core:di, core:model, core:proto
  ├── okio, kotlinx.serialization.json
  ├── xmlutil-core, xmlutil-serialization
  ├── ktor-client-core, ktor-network   (TCP socket)
  └── zstd-jni (jvmAndroid/jvm), kotlinx.datetime
```

## Local TAK Server Feature

The Local TAK Server can be enabled from the app's Settings screen. When running, ATAK/iTAK clients on the same network can connect to `<device-ip>:8089` and their position reports are automatically bridged onto the mesh. Mesh node positions are broadcast to all connected TAK clients in real time.

## TAKPacket-SDK consumer & version-bump playbook

This module consumes the external [TAKPacket-SDK](https://github.com/meshtastic/TAKPacket-SDK) (`org.meshtastic:takpacket-sdk-jvm`) for the V2 wire format. The SDK does CoT-XML ↔ `TAKPacketV2` ↔ zstd-compressed bytes; it owns the dictionaries and the schema.

**Two V2 wire paths — keep both in mind when the SDK changes:**

- **Path A (primary, SDK-delegated):** `TakSdkCompressor` / `TakV2Compressor` call the SDK's `CotXmlParser` / `CotXmlBuilder` / `TakCompressor`. This path is insulated from proto field renames *as long as* the SDK artifact and the proto submodule are bumped together.
- **Path B (fallback, Android-local Wire proto):** `TAKPacketV2Conversion.kt` builds and reads the Wire-generated `TAKPacketV2` **directly** (used on the SDK-failure send fallback and as the iOS receive stub). It references proto fields by name, so it **breaks at compile time** on any schema change and must be updated in lockstep.

**The proto submodule (`core/proto/src/main/proto`) must track the SDK's proto.** It is the same `meshtastic/protobufs` repo the SDK generates from. `:core:proto`'s `build.gradle.kts` prunes the atak messages owned by the SDK's own Wire codegen.

**When bumping to a new (wire-breaking) SDK version:**
1. `gradle/libs.versions.toml` → `takpacket-sdk = "<new>"`.
2. Bump the `core/proto/src/main/proto` submodule to the matching protobufs commit (push it upstream first, then `git -C core/proto/src/main/proto checkout <sha>` and commit the gitlink). For *local* testing before the protobufs commit is pushed, syncing just `meshtastic/atak.proto`'s content works (Wire regenerates from the working tree).
3. **Keep `core/proto/build.gradle.kts`'s `prune(...)` list complete.** The SDK jar ships its own Wire codegen of `org.meshtastic.proto.*` for the WHOLE atak.proto, so `:core:proto` must `prune()` EVERY atak message/enum the SDK ships — otherwise both define the same class and the release **R8 build fails** with `Type org.meshtastic.proto.X is defined multiple times` (it compiles + passes `jvmTest` fine; only R8/dexing catches it). When the schema gains a message (e.g. `Marti` in v0.3.2 — which was missed and broke R8), add a matching `prune("meshtastic.X")` line (+ one per nested proto type — prune does NOT cascade). Verify with: `unzip -l <sdk jar> | grep org/meshtastic/proto/` vs the prune list. (Exception: types the SDK does NOT ship — e.g. `Team`, `MemberRole` — must stay UNpruned, or the bridge loses them.)
4. Update **Path B** (`TAKPacketV2Conversion.kt`) and the **bridge** (`TakV2Compressor.kt`) for any renamed/removed/added wire fields.
5. Test against the local SDK: publish it (`cd TAKPacket-SDK/kotlin && ./gradlew publishToMavenLocal`), then `./gradlew :core:takserver:jvmTest -PuseMavenLocal` (needs **JDK 21**; `-PuseMavenLocal` is gated in `settings.gradle.kts`). Against a *published* version, drop `-PuseMavenLocal` and add `--refresh-dependencies` to pull from Maven Central.
6. Verify the release R8 build (`./gradlew :androidApp:minifyFdroidReleaseWithR8`) — catches prune-list gaps (above) AND confirms the `zstd-jni:…@aar` native lib survives minification (`core/takserver/build.gradle.kts` wires the `@aar` for androidMain, plain jar + `xpp3` for jvmMain).

**v0.4.0 wire facts (so you don't re-introduce phantom changes):** PLI is **implicit** — the `bool pli` oneof arm was removed; a packet with no payload variant + an `a-f-*` cot type is a PLI. `DrawnShape` vertices are two packed `repeated sint32` columns (`vertex_lat_deltas` / `vertex_lon_deltas`, deltas from the envelope point), not `repeated CotGeoPoint`. **`course` stays `deg×100`, `uid` stays a string, `stale_seconds` stays tag 16** — these were evaluated and deliberately NOT changed; do not "fix" the ×100 scaling in `TAKPacketV2Conversion.kt`.

**Debug "Send Test CoTs":** `TakMeshTestRunner` sends the bundled `tak_test_fixtures/*.xml` through the SDK path (parse → strip → compress → send). `taktalk_sanity.xml` is intentionally first. The fixtures need no edits across SDK wire breaks because they ride the SDK path; they ARE the regression surface (drawing_* exercise packed vertices, pli_* exercise implicit PLI).

## Dependency Graph

<!--region graph-->
```mermaid
graph TB
  :core:takserver[takserver]:::kmp-library
  :core:takserver --> :core:repository
  :core:takserver -.-> :core:common
  :core:takserver -.-> :core:di
  :core:takserver -.-> :core:model
  :core:takserver -.-> :core:proto
  :core:takserver -.-> :core:testing

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-application-compose fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef compose-desktop-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-library-compose fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-test fill:#A0C4FF,stroke:#000,stroke-width:2px,color:#000;
classDef jvm-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library-compose fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;

```
<!--endregion-->
