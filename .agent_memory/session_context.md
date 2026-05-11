# Agent Session Context - Meshtastic Android
# This is a dated, append-only handover log. Add new entries at the TOP.
# Do NOT edit or remove previous entries — stale state claims cause agent confusion.
# Format: ## YYYY-MM-DD — <summary>

## 2026-05-11 — Added DirectRadioControllerImpl common tests
- Created `core/service/src/commonTest/kotlin/org/meshtastic/core/service/DirectRadioControllerImplTest.kt`.
- Covered service-repository flow delegation, send message/send shared contact behavior, remote config request delegation, location stop, and device address updates.
- Validation note: `./gradlew --no-configuration-cache :core:service:allTests` is currently blocked by pre-existing compile failures in `core/network` (`MQTTRepositoryImpl` unresolved `KEEPALIVE_SECONDS`) and downstream `core/data` unresolved `org.meshtastic.core.network` symbols.

## 2026-05-11 — Added DatabaseManager withDb retry host test
- Created `core/database/src/androidHostTest/kotlin/org/meshtastic/core/database/DatabaseManagerWithDbRetryTest.kt`.
- Covered the concurrent `withDb()` retry path by pausing an in-flight query, switching to a new DB, closing the old pool, and asserting the retried query succeeds against the new DB.
- Verified with `./gradlew --no-configuration-cache :core:database:spotlessApply :core:database:testAndroidHostTest --tests "org.meshtastic.core.database.DatabaseManagerWithDbRetryTest"`
  and `./gradlew --no-configuration-cache :core:database:spotlessCheck :core:database:testAndroidHostTest`.

## 2026-05-11 — Expanded MQTT repository coverage
- Extended `core/network/src/commonTest/kotlin/org/meshtastic/core/network/repository/MQTTRepositoryImplTest.kt`
  with topic construction, JSON/protobuf decoding, reconnect retry, subscription retry, and connection-state coverage.
- Added internal `MqttClientSession` / `MqttClientSetup` test hook plus `updateConnectionState()` in
  `core/network/src/commonMain/kotlin/org/meshtastic/core/network/repository/MQTTRepositoryImpl.kt` to exercise repository behavior without a real broker.
- Verified with `./gradlew --no-configuration-cache :core:network:allTests`.

## 2026-05-11 — Added RadioConfigViewModel MQTT probe tests
- Extended `feature/settings/src/commonTest/kotlin/org/meshtastic/feature/settings/radio/RadioConfigViewModelTest.kt`
  with MQTT probe success, timeout, thrown-exception-to-Other, and clear/reset coverage.
- Verified with `./gradlew --no-configuration-cache :feature:settings:jvmTest --tests "org.meshtastic.feature.settings.radio.RadioConfigViewModelTest"`.

## 2026-05-11 — Added MeshRouterImpl accessor routing tests
- Created `core/data/src/commonTest/kotlin/org/meshtastic/core/data/manager/MeshRouterImplTest.kt`.
- Covered lazy routing access for action-handler send/request/admin calls, traceroute handler access, and service-action passthrough.
- Verified with `./gradlew --no-configuration-cache :core:data:allTests`.

## 2026-05-11 — Added SettingsViewModel saveDataCsv coverage
- Extended `feature/settings/src/commonTest/kotlin/org/meshtastic/feature/settings/SettingsViewModelTest.kt`
  with `saveDataCsv writes filtered export via file service`.
- The new test seeds `FakeNodeRepository` + `FakeMeshLogRepository`, captures the `FileService.write()`
  sink with Mokkery, and verifies filtered CSV output from the real `ExportDataUseCase`.
- Verified with `./gradlew --no-configuration-cache :feature:settings:jvmTest --tests "org.meshtastic.feature.settings.SettingsViewModelTest"`
  after running `:feature:settings:spotlessApply`.

## 2026-05-11 — Added CompassViewModel accuracy edge-case tests
- Extended `feature/node/src/commonTest/kotlin/org/meshtastic/feature/node/compass/CompassViewModelTest.kt`
  with PDOP-only, HDOP+VDOP, HDOP-only, precision-bits fallback, missing accuracy metadata,
  zero-distance angular error, and very-small-distance angular error coverage.
- Validation note: `:feature:node:allTests` still fails on the pre-existing
  `MetricsViewModelTest.saveEnvironmentMetricsCSV writes correct data` Turbine timeout in JVM and Android host tests.
  The new CompassViewModel tests pass in the same run.

## 2026-05-11 — Added Node domain model tests
- Created `core/model/src/commonTest/kotlin/org/meshtastic/core/model/NodeTest.kt`.
- Covered `isOnline`, `distance`, `bearing`, `colors`, `createFallback`, `getRelayNode`, `isUnknownUser`, `validPosition`, `hasPKC`, and `mismatchKey`.
- Validation blockers: `:core:model:allTests` currently fails on pre-existing `DataPacketTest` iOS compile errors, and direct `NodeTest` execution hits an existing class-version mismatch in `core:common` helpers.

## 2026-05-11 — Added HeartbeatSender transport tests
- Created `core/network/src/commonTest/kotlin/org/meshtastic/core/network/transport/HeartbeatSenderTest.kt`.
- Covered encoded heartbeat payloads, nonce sequencing, interval-driven scheduling, cancellation, zero-interval behavior, and restart semantics using coroutine virtual time.
- Verified with `./gradlew --console=plain --no-configuration-cache :core:network:allTests`.

## 2026-05-11 — Added BaseMapViewModel waypoint expiration tests
- Extended `feature/map/src/commonTest/kotlin/org/meshtastic/feature/map/BaseMapViewModelTest.kt`.
- Added coverage for future, boundary (`expire == now`), never-expiring (`expire == 0`), and mixed waypoint filtering.
- Verified with `./gradlew --no-daemon --no-configuration-cache :feature:map:spotlessCheck :feature:map:allTests`.

## 2026-05-03 — Switched Gradle GC to G1GC
- Replaced `-XX:+UseZGC` with `-XX:+UseG1GC` in `gradle.properties` to resolve "not supported" error.
- Added `-XX:+ParallelRefProcEnabled` for better build performance.
- Verified with Gradle sync.

## 2026-05-02 — CI cost-control PR review fixes
- Applied PR review feedback: encoding fixes in sort-strings.py, NUL-delimited staged-files loop
  in ai-guardrail.sh, installation instructions added, typo fix in strings.xml, command order
  fixed in AGENTS.md, narrowed .aiexclude/.gitattributes patterns, allTests added to SKILL.md.

## 2026-04-XX — Token Mitigation (Phase 1-3)
- `.copilotignore` and `.aiexclude` updated with stricter ignore rules.
- `AGENTS.md` modularized to ~3KB base; detailed rules moved to `.skills/`.
- `scripts/ai-guardrail.sh` added to prevent binary/log leaks (installation: see script header).
- CI Cost Control skill added at `.skills/ci-cost-control/SKILL.md`.

## Golden Context (stable across sessions)
- Always check `.skills/compose-ui/strings-index.txt` before reading `strings.xml`.
- Run `python3 scripts/sort-strings.py` after adding strings to keep the index organized.
- Always check `gh run list` before pushing.
- Pre-commit hook `scripts/ai-guardrail.sh` protects against binary leaks (see script for install).
