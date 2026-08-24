# Protocol Integration Checklist: TAK v2 Protocol Integration

**Purpose**: Validate requirements quality, completeness, and clarity across all TAK v2 spec dimensions — wire protocol, server lifecycle, UI, platform abstraction, and interoperability.
**Created**: 2026-05-13
**Feature**: `specs/005-tak-v2-protocol/spec.md`
**Focus**: Full-breadth requirements quality | PR review gate | Cross-platform coverage
**Depth**: Standard
**Audience**: Reviewer (PR)

---

## Constitution Compliance

- [ ] CHK001 — Are all business logic components confirmed to reside in `commonMain` with no `java.*`/`android.*` imports leaking into shared code? [Consistency, Spec §Source-Set Impact]
- [ ] CHK002 — Is zero-lint-tolerance verified with explicit `spotlessCheck` + `detekt` commands documented for the new `core:takserver` module? [Consistency, Spec §Design Standards]
- [ ] CHK003 — Are Compose Multiplatform constraints (no Jetpack Compose imports, `NumberFormatter.format()`) explicitly stated for `TAKConfigItemList.kt`? [Consistency, Spec §Assumptions]
- [ ] CHK004 — Is the privacy assessment specific enough to confirm CoT payload content is never logged (not just "no PII logged")? [Clarity, Spec §Privacy Assessment]
- [ ] CHK005 — Is the Design Standards review scoped to the TAK config screen only, or are mesh notification/error UIs also covered? [Completeness, Spec §Design Standards]
- [ ] CHK006 — Are verification commands documented for the full `core:takserver` + `feature:settings` scope (not just generic `assembleDebug`)? [Completeness, Spec §Success Criteria]

## Requirement Completeness — Wire Protocol

- [ ] CHK007 — Are all 23 CoT types explicitly enumerated in the spec, or does "including but not limited to" leave the list ambiguous? [Clarity, Spec §FR-002]
- [ ] CHK008 — Is the exact protobuf framing overhead (237 raw - ~225 usable = ~12 bytes) specified or left as an approximation? [Clarity, Spec §NFR-001]
- [ ] CHK009 — Are the two zstd dictionary types (aircraft vs non-aircraft) selection criteria documented — how does the system decide which dictionary to apply? [Gap, Spec §FR-003]
- [ ] CHK010 — Is the `flags` byte semantics for uncompressed packets (0xFF = TAK_TRACKER) formally defined, or only mentioned in edge cases? [Completeness, Spec §Edge Cases]
- [ ] CHK011 — Are requirements defined for what happens when zstd compression fails (not just decompression exceeding MAX_DECOMPRESSED_SIZE)? [Gap]
- [ ] CHK012 — Is the exact value of MAX_DECOMPRESSED_SIZE specified in requirements, or only referenced symbolically? [Clarity, Spec §FR-004]
- [ ] CHK013 — Are port numbers (72, 78) defined as constants with rationale, or are they magic numbers in requirements? [Clarity, Spec §FR-001, FR-005]
- [ ] CHK014 — Is the behavior specified when a TAKPacketV2 arrives on port 72 or a legacy TAKPacket arrives on port 78 (port mismatch)? [Coverage, Gap]

## Requirement Completeness — Type Mapping

- [ ] CHK015 — Are the 4 HOW types explicitly listed in the spec alongside the 23 CoT types? [Completeness, Spec §FR-002]
- [ ] CHK016 — Is the `CotType_Other` fallback behavior fully specified — what fields are preserved, what is lost? [Clarity, Spec §FR-011]
- [ ] CHK017 — Are bidirectional mapping requirements defined (CoT string → enum AND enum → CoT string) or only one direction? [Completeness, Spec §FR-002]
- [ ] CHK018 — Is the behavior defined when multiple CoT type strings map to the same enum value? [Coverage, Gap]

## Requirement Completeness — Server Lifecycle

- [ ] CHK019 — Are requirements specified for TAK server startup failure modes beyond "port already in use" (e.g., certificate corruption, memory exhaustion, network interface unavailable)? [Coverage, Spec §Edge Cases]
- [ ] CHK020 — Is the maximum number of concurrent ATAK/iTAK client connections specified? [Gap]
- [ ] CHK021 — Are graceful shutdown requirements defined — what happens to connected clients when the user disables the TAK server? [Gap]
- [ ] CHK022 — Is the 10-second keepalive interval requirement traceable to ATAK's 15-second stale threshold with margin documented? [Clarity, Spec §FR-015]
- [ ] CHK023 — Are requirements defined for TAK server behavior during Android configuration changes (rotation, locale change, split-screen)? [Gap]
- [ ] CHK024 — Is the offline message queue FIFO eviction behavior specified clearly — does eviction happen at enqueue time or dequeue time? [Clarity, Spec §FR-014]
- [ ] CHK025 — Are requirements defined for queue behavior when multiple clients reconnect simultaneously? [Coverage, Gap]

## Requirement Clarity — Legacy Fallback

- [ ] CHK026 — Is the firmware version detection mechanism specified (where/when is version checked — connection time, per-packet, cached)? [Clarity, Spec §FR-001]
- [ ] CHK027 — Is the firmware version comparison logic defined precisely (semver comparison, string match, numeric threshold)? [Clarity, Spec §Assumptions]
- [ ] CHK028 — Are requirements defined for what happens when firmware version is unknown or unavailable at transmission time? [Gap]
- [ ] CHK029 — Is "logged warning" for dropped unsupported types defined with specific severity level, log tag, and user visibility? [Clarity, Spec §FR-012]
- [ ] CHK030 — Are requirements defined for the transition period when a radio upgrades firmware mid-session? [Coverage, Gap]

## Requirement Consistency

- [ ] CHK031 — Do FR-005 (accept both ports) and FR-001 (version-gate output) consistently define the dual-path behavior without contradiction? [Consistency, Spec §FR-001, FR-005]
- [ ] CHK032 — Is the "~225 bytes usable" figure used consistently across all requirements referencing MTU constraints? [Consistency, Spec §NFR-001, FR-003]
- [ ] CHK033 — Do User Story 2 acceptance scenarios align with FR-012's "logged warning" — is the warning also "user-visible" per the Independent Test description? [Conflict, Spec §US2, FR-012]
- [ ] CHK034 — Is the relationship between FR-013 (strip 16 elements) and FR-003 (zstd compression) sequencing defined — strip first, then compress? [Consistency, Gap]
- [ ] CHK035 — Do the 40 test fixtures in tasks.md align with the "28 total mappings" in spec success criteria? [Consistency, Spec §SC-001]

## Acceptance Criteria Quality

- [ ] CHK036 — Is "within one mesh transmission cycle" (US1 scenario 1) quantified with a maximum time bound? [Measurability, Spec §US1]
- [ ] CHK037 — Is "no added processing delay > 100ms" (NFR-003) measurable — what's the measurement point (start/end boundaries)? [Measurability, Spec §NFR-003]
- [ ] CHK038 — Is "30+ minutes" (NFR-002, SC-004) a minimum requirement or typical expectation? Are test conditions specified (Doze, App Standby, background restrictions)? [Measurability, Spec §NFR-002]
- [ ] CHK039 — Is SC-006 ("65+ test methods") a minimum or approximate — does the requirement define what "passing" means for round-trip encoding? [Clarity, Spec §SC-006]
- [ ] CHK040 — Can SC-002 ("compressed PLI < 100 bytes") be measured independently of radio hardware? [Measurability, Spec §SC-002]

## Scenario Coverage — Edge Cases

- [ ] CHK041 — Are requirements defined for handling corrupted zstd dictionary data at runtime? [Gap]
- [ ] CHK042 — Is the behavior specified when ATAK sends CoT with coordinates outside valid ranges (lat > 90, lon > 180)? [Coverage, Gap]
- [ ] CHK043 — Are requirements defined for handling duplicate CoT messages arriving via both port 72 and port 78 simultaneously? [Coverage, Gap]
- [ ] CHK044 — Is the XML sanitization scope (FR-010) limited to the 5 characters listed, or does it also address CDATA injection, entity expansion (XXE), or DTD attacks? [Clarity, Spec §FR-010]
- [ ] CHK045 — Are requirements defined for behavior when the mesh network is unavailable but ATAK is connected (buffering vs immediate failure)? [Gap]
- [ ] CHK046 — Is the "50-message cap" (FR-014) justified with sizing analysis — could 50 large CoT messages cause memory pressure? [Clarity, Spec §FR-014]

## Non-Functional Requirements Coverage

- [ ] CHK047 — Are memory consumption requirements defined for zstd dictionary loading and decompression buffers? [Gap, NFR]
- [ ] CHK048 — Are battery impact requirements quantified beyond "partial wake lock" — expected drain rate, or max acceptable CPU usage? [Gap, NFR]
- [ ] CHK049 — Are startup time requirements defined for the TAK server initialization? [Gap, NFR]
- [ ] CHK050 — Are requirements specified for TAK server behavior under low-memory conditions (Android LMK threshold)? [Gap, NFR]
- [ ] CHK051 — Is thread/coroutine model specified for concurrent TAK client handling and mesh I/O? [Gap, NFR]

## Cross-Platform Requirements (KMP Boundaries)

- [ ] CHK052 — Are iOS stub behaviors explicitly specified — what does TAKServerIos return/throw for each operation? [Completeness, Spec §Source-Set Impact]
- [ ] CHK053 — Is iOS "uncompressed TAK_TRACKER mode" payload size limitation (~225 bytes) clearly stated as a functional constraint (not just an edge case note)? [Clarity, Spec §Edge Cases]
- [ ] CHK054 — Are desktop (JVM) TAK capabilities explicitly defined — is TLS server supported? File writing? What's excluded? [Completeness, Spec §Source-Set Impact]
- [ ] CHK055 — Are expect/actual contract requirements documented — what guarantees must each platform `actual` provide? [Gap]
- [ ] CHK056 — Is the iOS "pending Swift SDK integration" timeline or gating criteria defined, or is it an open-ended assumption? [Assumption, Spec §Assumptions]
- [ ] CHK057 — Are error/fallback requirements defined for when platform actuals are stubs (iOS) — do callers get exceptions, no-ops, or degraded functionality? [Clarity, Gap]

## Dependencies & Assumptions

- [ ] CHK058 — Is the TAKPacket-SDK v0.1.3 dependency stability assessed — is it alpha/beta, and are version pinning requirements defined? [Assumption]
- [ ] CHK059 — Is the "firmware version available at connection time" assumption validated — what if radio metadata is delayed or partial? [Assumption, Spec §Assumptions]
- [ ] CHK060 — Is the ATAK "15-second stale threshold" assumption documented with a source reference? [Assumption, Spec §FR-015]
- [ ] CHK061 — Are the bundled certificate requirements specified (validity period, rotation strategy, self-signed vs CA-signed)? [Gap]
- [ ] CHK062 — Is the Android 17+ ACCESS_LOCAL_NETWORK permission requirement validated against published API 37 documentation? [Assumption, Spec §FR-016]
- [ ] CHK063 — Is the "237-byte raw LoRa MTU" assumption sourced to specific radio hardware documentation or Meshtastic firmware specs? [Assumption, Spec §Assumptions]

## Security Requirements

- [ ] CHK064 — Are mTLS certificate validation requirements specified — does the server verify client certificates, or only encrypt? [Clarity, Spec §FR-006]
- [ ] CHK065 — Are certificate storage security requirements defined (KeyStore, encrypted preferences, plaintext resources)? [Gap]
- [ ] CHK066 — Is the threat model for local-network-accessible TAK server documented (LAN adversary, rogue ATAK client)? [Gap, Security]
- [ ] CHK067 — Are requirements defined for certificate expiration handling and renewal? [Gap, Security]
- [ ] CHK068 — Is the CoT XML sanitization (FR-010) scope sufficient for the threat model — are XXE and billion-laughs attacks addressed? [Clarity, Spec §FR-010]

## Cross-Artifact Consistency

- [ ] CHK069 — Do task IDs in tasks.md map 1:1 to plan.md phase structure without gaps or orphans? [Consistency]
- [ ] CHK070 — Does the plan's "99 files, +4698 lines" scope align with the task count and complexity in tasks.md? [Consistency]
- [ ] CHK071 — Are all 16 bloat elements listed in FR-013 consistent with the CoTDetailStripper implementation described in plan.md? [Consistency, Spec §FR-013]
- [ ] CHK072 — Do research.md technology decisions align with spec dependency choices (TAKPacket-SDK, xmlutil, Ktor)? [Consistency]

---

## Notes

- Focus: Full-breadth requirements quality across all spec dimensions
- Depth: Standard (PR review gate)
- Audience: PR Reviewer evaluating requirement completeness before merge
- 72 items covering: Constitution (6), Wire Protocol (8), Type Mapping (4), Server Lifecycle (7), Legacy Fallback (5), Consistency (5), Acceptance Criteria (5), Edge Cases (6), NFRs (5), Cross-Platform (6), Dependencies (6), Security (5), Cross-Artifact (4)
- Items reference spec sections where applicable; `[Gap]` markers indicate missing requirements
