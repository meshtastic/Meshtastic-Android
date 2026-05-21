# Research: TAK v2 Protocol Integration

**Status**: Complete (retroactive — documents decisions made in PR #5434)

## R1: Zstd Compression Strategy for LoRa MTU

**Decision**: Use pre-trained zstd dictionaries via `meshtastic/TAKPacket-SDK` (JitPack v0.1.3) with a 1-byte flags header encoding dictionary ID.

**Rationale**:
- LoRa MTU is 237 bytes raw (~225 usable after protobuf framing). Standard zstd without dictionaries performs poorly on small payloads. Pre-trained dictionaries (trained on representative CoT samples) achieve 60-80% compression on typical payloads.
- Two dictionaries: `DICT_ID_NON_AIRCRAFT` (0) for general CoT, `DICT_ID_AIRCRAFT` (1) for aircraft tracks (different statistical distribution).
- Fallback: `0xFF` flags byte = uncompressed (for TAK_TRACKER firmware and iOS stubs).

**Alternatives Considered**:
- LZ4: Faster but worse compression ratio at small payload sizes; doesn't fit complex CoT within MTU
- Custom delta encoding only: Insufficient for free-text fields (remarks, callsigns)
- No compression: PLI fits (~80 bytes) but shapes/routes (500-2000 bytes raw) would never fit

---

## R2: Platform Abstraction for Compression

**Decision**: Use `expect object TakV2Compressor` with `jvmAndroidMain` actual (full SDK) and `iosMain` actual (uncompressed stub, flags=0xFF).

**Rationale**:
- TAKPacket-SDK is JVM-only (depends on zstd-jni native library). iOS has no Kotlin/Native zstd binding available.
- iOS stubs emit uncompressed payloads that receiving nodes handle via the 0xFF flags check.
- The `jvmAndroidMain` shared source set avoids duplicating ~500 lines between Android and Desktop.

**Alternatives Considered**:
- Pure Kotlin zstd implementation: None exists with dictionary support
- Multiplatform C interop for zstd: Too complex for initial release; iOS can add later via Swift interop
- Separate `androidMain`/`jvmMain` actuals: Identical code, maintenance burden

---

## R3: CoT XML Processing Approach

**Decision**: Use `xmlutil` library for structured XML parsing (`CoTXmlParser`) and regex for detail stripping (`CoTDetailStripper`).

**Rationale**:
- `xmlutil` is the standard KMP-compatible XML library, supports serialization and streaming.
- Detail stripping needs only element removal (not DOM traversal), making regex simpler and allocation-free.
- The regex approach handles multi-line elements with dot-matches-all mode.

**Alternatives Considered**:
- Full DOM for everything: Higher memory allocation, unnecessary for simple element removal
- String manipulation without regex: Fragile with nested elements and attributes
- SAX streaming for stripping: Overcomplicated for "remove known elements" pattern

---

## R4: TLS Server Architecture

**Decision**: JSSE `SSLServerSocket` with mTLS in `jvmAndroidMain`, exposed via `TAKServer` expect interface.

**Rationale**:
- ATAK/iTAK clients expect standard TAK Server protocol: TLS on port 8089 with mutual certificate authentication.
- JSSE is available on both Android and Desktop JVM; no additional dependencies needed.
- Per-client `TAKClientConnection` with its own `CoroutineScope` (SupervisorJob) prevents one dead connection from cascading.
- `BufferedOutputStream` + `writeMutex` prevents XML stream corruption from concurrent broadcasts.

**Alternatives Considered**:
- Ktor server: Heavier dependency for a simple TLS socket listener
- Netty: Not KMP-compatible, overkill for <10 concurrent connections
- Raw sockets + manual TLS: Error-prone certificate handling

---

## R5: Version Gating Strategy

**Decision**: Runtime firmware version check via `Capabilities.supportsTakV2` (≥ 2.8.0) evaluated per-send to pick up firmware upgrades without app restart.

**Rationale**:
- Mixed-firmware deployments are the common case during upgrade cycles.
- Per-send evaluation (not cached at connection time) handles the edge case of firmware OTA during an active session.
- Inbound path always listens on both ports (72 and 78) regardless of local firmware — ensures no data loss.

**Alternatives Considered**:
- Compile-time feature flags: Can't handle mixed deployments
- User-configurable protocol selection: Too error-prone; auto-detection is strictly better
- Connection-time-only check: Would miss mid-session firmware upgrades

---

## R6: Route Interoperability (KML Bridge)

**Decision**: Generate KML data packages for route CoT events because ATAK's route CoT handling has limitations.

**Rationale**:
- ATAK can import routes from KML files placed in its monitored data package directory.
- Route CoT over mesh preserves the waypoint data; KML generation on the receiving end enables ATAK to display the full route with navigation capabilities.
- Uses `MissionPackageManifest v2` format compatible with both ATAK and iTAK.

**Alternatives Considered**:
- Rely on ATAK's native route CoT handling: Incomplete (doesn't render full route UI)
- Custom ATAK plugin: Out of scope; Meshtastic is a standalone app
- Route fragmentation across multiple mesh packets: Exceeds complexity budget and unreliable over LoRa

---

## R7: Offline Message Queue Design

**Decision**: 50-message cap with 5-minute TTL, auto-drained on client reconnect.

**Rationale**:
- Brief disconnections (screen off, ATAK restart) are common in tactical environments.
- 50-message cap prevents unbounded memory growth if mesh traffic is high.
- 5-minute TTL ensures stale tactical data doesn't replay (CoT has its own stale mechanism but queue provides defense-in-depth).
- `onClientConnected` callback triggers immediate drain.

**Alternatives Considered**:
- No queue (drop all): Loses critical tactical updates during brief disconnects
- Unlimited queue: Memory exhaustion risk on constrained Android devices
- Persistent (disk) queue: Overkill; 5-minute window doesn't warrant I/O complexity

---

## R8: Wake Lock Strategy

**Decision**: `PARTIAL_WAKE_LOCK` held for entire MeshService lifecycle when device is connected.

**Rationale**:
- Android's Doze mode throttles CPU, which kills keepalive timers and socket I/O for the TAK server.
- `PARTIAL_WAKE_LOCK` keeps CPU active without keeping screen on.
- Foreground service alone is insufficient — OEMs (Samsung, Xiaomi) aggressively throttle even foreground services.
- Reference-counted=false allows unconditional release on service stop.

**Alternatives Considered**:
- WorkManager periodic wakeup: 15-minute minimum interval, too slow for 10-second keepalives
- AlarmManager: Deprecated for this pattern; complex and battery-unfriendly
- No wake lock (foreground service only): Tested and fails on multiple OEM devices

---

## R9: CoT Detail Stripping for MTU Compliance

**Decision**: Strip 16 known bloat XML elements before compression to maximize payload fit.

**Rationale**:
- ATAK adds many display-only elements (color, strokeColor, usericon, model, __video, fileshare) that are irrelevant for mesh relay.
- Stripping before compression (not after) gives the compressor cleaner input and smaller output.
- Elements stripped are purely cosmetic; receiving ATAK clients reconstruct display from CoT type and position.

**Alternatives Considered**:
- Strip nothing, rely on compression alone: Shapes with rich detail regularly exceed MTU even compressed
- Strip everything except position: Loses contact/callsign/remarks which are tactically critical
- Configurable strip list: Over-engineering for v1; the 16 elements are well-established as non-essential

---

## R10: Android 17+ ACCESS_LOCAL_NETWORK Permission

**Decision**: Request `ACCESS_LOCAL_NETWORK` on API 37+ with user-visible error if denied, using platform-specific `TakPermissionUtil`.

**Rationale**:
- Android 17 restricts apps from binding to localhost/LAN ports without explicit permission.
- The TAK server binds to `127.0.0.1:8089` — requires this permission.
- Graceful degradation: server simply doesn't start if permission denied; UI shows actionable error.

**Alternatives Considered**:
- Always request regardless of API level: Permission doesn't exist pre-API 37; would crash
- Skip permission and catch bind failure: Unclear error message for users
- Use a different IPC mechanism: ATAK expects standard TCP/TLS on 8089; non-negotiable
