## 2026-09-10 — 公司發行憑證與商店建檔

使用者確認建立 Apple Distribution 後，Xcode 已建立 LiberaNt LLC (D524H699HW) 發行憑證；
本機 find-identity 確認存在，私鑰未匯出。App Store Connect 兩個記錄已建：NTsocial
6810478003、NTsocial MeshLink 6810479958，均 Prepare for Submission，尚未上傳／送審。
新的 company App Group group.com.ntsocial.gateway 在兩個實際 Development-signed Release
artifacts 中驗證成功；共享 Keychain 一致。詳見 docs/ios-company-signing-status-2026-09-10.md。
使用者明確要求略過官網舊隱私文案，稍後自行修改；不得再將網站清理當成本次上傳阻礙。
App Privacy 仍照事實填寫。現修復已知八項 Detekt，保留跨平台行為、iOS Channels-only scanner。
Native/Android/Windows 完整 gate 已通過：2,025 tasks，103 executed、1,922 up-to-date，exit 0。不得把簽章預檢 archive 當最新固定來源發行候選。

# Agent Session Context - NTsocial MeshLink Android, Windows & iOS

## 2026-09-10 - Company App Group migrated; goal expanded to two App Store submissions
- User explicitly abandoned the original App Group, rejected the interim Team-ID-suffixed name, and retired
  all old personal signing configuration. Final product App Group is `group.com.ntsocial.gateway` for both
  `com.ntsocial.meshlink.ios` and `com.ntsocial.ios`, both under the one LiberaNt LLC team `D524H699HW`.
  Source contracts, Info.plists, effective entitlements, parent archive/baseline guards and current guidance
  are synchronized. Shared Keychain suffix remains `com.ntsocial.meshlink.gateway`; parent private group first.
- Xcode signed out the old personal Apple Account. Removed the unused parent Personal.entitlements file,
  its SwiftPM exclusion and the active Personal-Team fallback instructions. Historical evidence is preserved.
  Company account remains; both app configurations use company Team ID. No personal certificates were revoked.
- Xcode automatically provisioned both company App IDs with the new group. New Development profiles grant
  group.com.ntsocial.gateway; parent profile also retains the unused interim group and Wi-Fi Aware/Hotspot.
  The actual parent Release device build succeeded and codesign verification proves only the final new App
  Group in the signature, matching company shared/private Keychain and existing networking entitlements.
  It is development-signed (`get-task-allow=true`), not App Store distribution. MeshLink Release archive is
  still running (exec session 85627); parent build session 83135 completed. No actual device installs this turn.
- Full MeshLink checks for the final group: 2,097 tasks, 128 executed / 1,969 up-to-date, exit 1 only for the
  known eight Detekt findings. Parent release baseline and focused AppleGateway/MeshLink Swift 45 tests passed.
  Artifacts under `.agent_artifacts/ios-company-signing-2026-09-10/company-app-group/`; parent build at
  `/tmp/NTsocialCompanyAppGroupDerivedData/Build/Products/Release-iphoneos/NTSocialApp.app`.
- User explicitly expanded today's goal to publishing BOTH products on App Store. Created active goal with
  tools.create_goal; do not mark complete from setup/upload/submission alone. Apple review/public availability
  remains an external requirement. Company Distribution certificate creation is prepared in Xcode's menu;
  action-time confirmation was requested and remains unanswered. Never treat the separate terms reply as cert consent.
- App Store Connect Terms of Service V100 (2018-06-04) acceptance was explicitly approved by user and completed.
  Both App records now visibly exist under LiberaNt LLC: NTsocial ID `6810478003`, MeshLink ID `6810479958`,
  each iOS 1.0 Prepare for Submission. Original Bundle IDs preserved, primary en-US, SKUs ntsocial-ios and
  ntsocial-meshlink-ios. No builds uploaded or review submitted yet. Chrome store tab `543619531` on browser `1`.
- Current public privacy/legal pages are materially stale versus parent Cloud/persistence/crypto source.
  Community standards page exists. Checked-in parent Store descriptions/review notes also stale (Basic-only,
  no central server); must update source-aligned copy and public policy before truthful privacy answers.
  DSA trader banner remains in App Store Connect; export classification, screenshots, signed release candidate,
  current physical evidence and root static failures still require work. Old App Group Support draft is obsolete
  and must not be sent. No delegation authorized; do not spawn subagents.

## 2026-09-10 - Old Personal Team group still listed after Xcode refresh
- User completed old account login. Xcode now lists the old account's Personal Team. Read-only inspection
  at approximately 09:33 Taipei used an independent ignored `GroupOwnershipProbe` project with team
  `HN23VK3A6R`, manual signing, `CODE_SIGNING_ALLOWED=NO`, empty bundle ID, and an empty App Groups
  entitlement array. No original group identifier was populated in this probe's entitlement file.
- Signing & Capabilities shows the old Personal Team and an unchecked `group.com.ntsocial.meshlink.gateway`
  in its App Groups list. Clicked refresh; it disabled during refresh, then re-enabled with the same group
  still listed and no account/update error. This is refreshed Xcode team-inventory evidence in addition to
  the old signed profile evidence, strongly supporting the original group remaining with the old team.
  It is not an Apple Support backend confirmation or an old-team Portal group detail page, and does not
  establish that deletion would immediately release it for the company. Profile expiry is not release proof.
- Probe artifacts are ignored under `.agent_artifacts/ios-company-signing-2026-09-10/PersonalTeamGroupProbe/`.
  Product signing remains company `D524H699HW`; no old group selected/created/deleted, no bundle registration,
  no certificate creation, no support send, and no actual-device work. Pending Distribution/support send
  confirmations remain unanswered. Current task was ownership investigation; no group migration authorized.
- Updated `docs/ios-company-signing-status-2026-09-10.md`. Xcode remains on the read-only probe group view
  for user inspection. Company Apple Portal and unsent Support draft tabs remain open from earlier work.

## 2026-09-10 - Old Personal Team App Group evidence found; account login pending
- User explicitly requested checking whether the old personal account still holds the original App Group.
  Local old Apple Development certificate identifies Personal Team `HN23VK3A6R`; Xcode presently lists only
  the organization Apple Account. Opened Xcode Add Apple Account for the old certificate's account and stopped
  at the secure password field. Asked user to complete password/2FA in Xcode and say logged in; no password,
  OTP, key bytes, or credential-store entries were read. Company/project signing settings were not changed.
- Direct historical evidence found in the retained Xcode DerivedData NTSocialApp.app embedded.mobileprovision:
  profile `iOS Team Provisioning Profile: com.ntsocial.ios`, TeamIdentifier `HN23VK3A6R`, application identifier
  `HN23VK3A6R.com.ntsocial.ios`, and App Groups includes `group.com.ntsocial.meshlink.gateway`. Creation and
  expiration in Taipei are 2026-09-03 07:54:03 and 2026-09-10 07:54:03. This proves the old team previously
  had a profile authorizing this group, not current Portal ownership or automatic release upon profile expiry.
- Safe metadata/hash summary saved to ignored `.agent_artifacts/ios-company-signing-2026-09-10/old-personal-profile-evidence.json`;
  main signing report updated. No group deletion/migration, certificate creation, support message, or device
  mutation occurred. Pending Distribution/support permissions remain unanswered; continue old account read-only
  verification after user completes login. Support draft can be updated with this new evidence before any send.

## 2026-09-10 - App Group recovery options clarified
- User asked how to resolve the rejected original App Group. Explained two distinct paths: verify/recover the
  original group's ownership with the old team/Apple Support, or explicitly migrate both apps to a new company
  App Group while preserving both original Bundle IDs. A new group means a new shared container; retained
  old-group data would require an explicit migration. New-group availability is not yet checked.
- Existing Group ID appears in both apps' Info.plist/entitlements and the Kotlin/Swift AppleGatewayContract
  sources, so a migration cannot be a portal-only or entitlement-only change. Shared Keychain authorization
  must still match the company prefix. No group migration was authorized or performed by this explanatory turn.
- Pending Distribution-certificate and Apple Support send confirmations remain unanswered. The original group
  is unavailable to the company, but its present owner and any release timeline are unverified.

## 2026-09-10 - Company signing implementation; original App Group blocked by Apple
- User explicitly requested all five company-signing/profile/dual-App verification steps. MeshLink now has
  optional `iosApp/Config/Signing.xcconfig` referenced by Debug/Release and a Git-ignored local config selecting
  `D524H699HW`. Original Bundle ID and entitlement file are preserved. Both effective configurations were
  verified with `xcodebuild -showBuildSettings`. Parent `NTsocial_release` already had uncommitted optional
  signing config; its existing changes were preserved and both effective configurations verified on the same
  team. Parent sources were read only, not modified. Shared Gateway App Group/Keychain suffixes match.
- Actual company Portal registration of `group.com.ntsocial.meshlink.gateway` failed with identifier not
  available; the company's App Groups list was empty. Xcode independently displays the same error. The current
  owning team remains unknown; prior Personal Team ownership is a hypothesis, not verified fact. No alternate
  App Group, entitlement stripping, identifier deletion, or certificate revocation was performed.
- Xcode's LiberaNt LLC Manage Certificates -> Apple Distribution menu is prepared. The computer-use tool's
  action-time security confirmation was requested; no user response yet, so no Distribution certificate/private
  key has been created. Existing company Apple Development identity is locally usable and has OU D524H699HW.
- Apple Developer Support contact form is filled with the exact company, identifiers, error, and request to
  investigate/release/reassign the original App Group after ownership verification. Explicit permission to send
  this external message was requested and is pending; nothing sent and no case ID. Support tab and Portal tab
  are retained for handoff. Continue from the pending forms rather than creating duplicate requests.
- Full MeshLink validation ran 2,097 actionable tasks; formatting/builds/tests/KMP/lints succeeded, root exit 1
  only for the same eight pre-existing Detekt findings. Runtime JVM 26 and Gateway JVM 39 tests have no failures.
  Parent focused AppleGateway tests pass 45/45. Signing-disabled Simulator Debug build/install/launch passes
  on a newly created empty iOS 26.5/iPhone 17 simulator; process stayed at PID 82608. The build retains the
  AppIntents metadata-extraction warning. Actual Release archive exits 65 for missing App Group capability/
  identifier/entitlement in the wildcard company profile. No signed archive/IPA or true-device install occurred.
- Main report: `docs/ios-company-signing-status-2026-09-10.md`; detailed logs in ignored
  `.agent_artifacts/ios-company-signing-2026-09-10/`. Parent Release also needs existing Hotspot/Wi-Fi Aware
  entitlements provisioned. Real device work must retain the parent's empty-data baseline and isolate historical
  transports; the current request reauthorizes testing but does not authorize restoring old test state.

## 2026-09-10 - Company iOS signing readiness assessment
- User asked whether company Xcode signing, App Groups, and provisioning should be completed before the
  upcoming release. Read-only inspection confirms they are required next steps; registration alone is insufficient.
- Live LiberaNt LLC / `D524H699HW` portal shows no registered App Groups, one Development certificate, and
  no listed provisioning profiles. Local Xcode has a company wildcard development profile `D524H699HW.*`
  with `get-task-allow=true`, no App Group entitlement, and wildcard company Keychain authorization. It is
  not a distribution profile for MeshLink. Local valid signing identities are development identities only.
- MeshLink Debug/Release both use automatic signing and `$(NTSOCIAL_DEVELOPMENT_TEAM)`. Source already
  declares `group.com.ntsocial.meshlink.gateway` and `$(AppIdentifierPrefix)com.ntsocial.meshlink.gateway`.
- Recommended sequence: supply the company team for Debug/Release; register and associate the Gateway App Group
  with both exact App IDs (`com.ntsocial.meshlink.ios` and `com.ntsocial.ios`); verify matching shared-Keychain
  entitlements in both signed Apps; configure development and App Store Connect distribution signing/profile
  resources (Xcode automatic management is supported); inspect the resulting signed Release archive and perform
  two-App physical-device Gateway validation before TestFlight/store claims. Parent source was not inspected or
  modified in this assessment. No new certificate/key, profile, capability, group association, or source setting
  was created/changed, and nothing was uploaded. Any future credential creation or new cross-App data access
  through the UI needs the applicable action-time confirmation.

## 2026-09-10 - Original iOS Bundle ID successfully registered under LiberaNt LLC
- After the user said continue, the live Apple Developer portal initially listed `com.ntsocial.ios` and the
  wildcard App ID under `LiberaNt LLC` / Team ID `D524H699HW`. Registered a new explicit App ID with description
  `NTsocial MeshLink` and the exact original Bundle ID `com.ntsocial.meshlink.ios`.
- Clicked Register and verified the resulting company Identifiers list visibly contains
  `NTsocial MeshLink` / `com.ntsocial.meshlink.ios`. This supersedes the pending availability/registration status
  in the earlier preflight below. No alternative Bundle ID was used and no old identifier was deleted.
- Registration used the portal defaults (In-App Purchase automatically enabled; App Groups unchecked).
  No App Group association, Keychain configuration, certificate/profile generation, Xcode team change,
  device installation, or App Store Connect app creation/upload was performed. Existing source still uses
  `NTSOCIAL_DEVELOPMENT_TEAM`; future company signing should supply verified team `D524H699HW` and separately
  configure/verify the existing Gateway App Group and Keychain entitlements before claiming signed integration.
- Portal evidence: https://developer.apple.com/account/resources/identifiers/list (authenticated company view),
  retained as the browser deliverable. Only session memory changed locally; Android/Windows and product source
  are unchanged. Documentation diff checks apply; no product build was needed for identifier registration.

## 2026-09-10 - iOS company Bundle ID registration preflight (pending browser continuation)
- User explicitly authorized registering the original iOS Bundle ID under the paid US company developer account;
  preserving `com.ntsocial.meshlink.ios` is mandatory. Xcode Debug and Release still use that exact identifier,
  and signing team remains parameterized by `NTSOCIAL_DEVELOPMENT_TEAM`.
- Live Apple Developer account UI verified organization `LiberaNt LLC`, Team ID `D524H699HW`, Apple Developer
  Program membership, Account Holder role, and renewal date September 5, 2027. No credentials were captured.
- The Identifiers navigation was blocked because another Chrome extension UI was open. The Apple account tab
  was retained for handoff; user was asked to complete/dismiss the extension UI and say continue. No registration,
  App Group binding, provisioning, signing, or source-configuration changes were submitted. Bundle availability
  remains unverified; do not describe the old identifier as released or the company registration as complete.
- Apple account help states Personal Team App IDs expire after seven days, but actual availability of this
  identifier still requires the registration service result. Existing App Group and Keychain entitlement
  identities remain unchanged. Android and Windows are unaffected; no product build was needed for this preflight.


## 2026-09-03 - iOS expired Apple Gateway route automatic-recovery remediation
- Corrected the connected-iPhone failure from the five-phone run in the separately authorized
  `/Users/curry_tw/Documents/GitHub/NTsocial_release` parent worktree. A send bound by stable MeshLink source now
  recognizes an expired 120-second route, uses a payload-free wake/deep link, waits boundedly for a coherent READY
  replacement, rebuilds with the fresh slot/token/generation/capability, and then performs the unchanged strict
  authenticated mailbox enqueue. Persisted automatic routes restart as historical-only and current projections are
  invalidated at expiry; numeric-slot fallback was not added.
- Moved both projection preflight and final synchronous provider/mailbox admission off MainActor, kept BLE admission
  ahead of route renewal, fenced suspension points against panic/detach epochs, and single-flighted the complete
  canonical-message BLE-plus-MeshLink operation before the first retry store read. Focused Apple Gateway tests pass
  45/45 and the complete parent SwiftPM suite passes 794/794; the final signed Debug parent device build succeeds.
- Data-preserving installed the final NTsocial parent plus MeshLink on the connected iPhone 15 (iOS 26.6.1). The final
  XCUITest kept MeshLink backgrounded for 135 seconds, used exactly one Send tap, never test-launched/activated
  MeshLink after expiry, and observed product-driven foreground recovery, READY state, canonical text, no
  expired/rejected status, and acceptance of all four multipart commands. The MeshLink private ledger advanced
  exactly 14 -> 18 and all four new rows were `ACCEPTED` (all 18 retained rows accepted). This proves automatic route
  renewal and local Gateway/radio-queue admission, not RF airtime or remote receipt. Product MeshLink source was not
  changed; only this memory and the ignored disposable XCUITest harness changed in the MeshLink worktree.


## 2026-09-03 - Five-phone NTsocial/MeshLink physical channel-binding and message test
- Exercised three Android 16 phones and two physical iPhone 15 devices with the user's explicit authorization to send
  disposable text. At 11:17:10 CST all five NTsocial parent apps were launched concurrently; 32 seconds later every
  parent and every installed MeshLink companion process was alive, all three Android parents were foreground, and the
  Android log window plus the captured iOS MeshLink log contained no fatal/crash signal.
- On the connected Android phone, the parent resolved two live Meshtastic endpoints with 13 total native channels. A
  disposable private NTsocial channel was bound specifically to each endpoint's `NTsocial` secondary route; the UI
  showed 2/2 selected and retained the same bindings after reopening. Sending
  `TEST-MeshLink-Android-20260903-104644` produced canonical parent history/confirmation and 20 accepted Gateway
  enqueue/dequeue/`to_radio` operations with 20 successful QueueStatus responses. Both independent BLE GATT clients
  stayed open. The connected iPhone's MeshLink Room log then contained exactly 20 new PRIVATE_APP packets in the same
  receive window, providing physical Android-to-iPhone Meshtastic RF receipt evidence. The two disposable NTsocial
  logical channels were not shared membership, so this does not prove parent-to-parent canonical delivery.
- On the connected iPhone, a disposable parent channel bound to the current `#NTsocial`, secondary slot 1, locked route
  and the choice survived save/reopen. The first send exposed a real lifecycle defect: while MeshLink was backgrounded,
  the still-visible route had expired and the command was rejected. Foregrounding MeshLink refreshed status to READY;
  two subsequent authorized sends each reached 3/3 `ACCEPTED_LOCAL`, and the private durable ledger grew from zero to
  six distinct ACCEPTED rows while contemporaneous CoreBluetooth writes were present. No corresponding new PRIVATE_APP
  packet appeared in any of the three Android MeshLink databases, so iPhone-to-Android RF receipt remains unproven and
  the iOS path is only a partial pass.
- The two Android no-radio phones exposed zero native channels and no MeshLink radio-channel section, correctly failing
  closed instead of fabricating bindable routes. The no-radio iPhone's app data likewise had no active channel set or
  radio database, but its binding-UI test could not start because iOS timed out enabling automation mode on three
  attempts; the test method never ran and no fixture/state was created, so this is an automation-environment limitation,
  not evidence of an NTsocial product failure.
- Cleanup is complete. The connected Android parent left the disposable channel and persisted
  `lora_fleet_channel_bindings_v3` as `{"version":3,"bindings":[]}`; the connected iPhone has zero manual bindings and
  no joined disposable channel. No product source was changed and no Gradle gate was needed; only an ignored disposable
  `.agent_plans` XCUITest harness was extended for this physical test.


## 2026-08-31 - Android simultaneous multi-node BLE runtime remediation
- On branch `multi_nodes_` from HEAD `98663d1e8e4c421b6d5dc7ce7d4be00eb1914a2a`, reproduced the user's S24 failure:
  the primary Meshtastic radio completed Stage 2 while the secondary endpoint stopped in Error and owned no MeshLink
  GATT client. The Android Bluetooth stack and both radios supported concurrent LE connections; the failure was inside
  the secondary App graph.
- Root cause was Koin constructor-reference scope wiring that lost the required `ProcessLifecycle`/`ServiceScope`
  qualifiers and tried to resolve Kotlin `Lazy<T>` as a raw dependency. Replaced every affected secondary registration
  with an explicit scoped factory, bound secondary Gateway access to fail-closed `SecondaryGatewayRepository`, and
  moved the session `wired` latch after complete graph resolution so a failed attempt cannot poison a retry.
- Added `SecondaryRadioEndpointScopeRuntimeTest`, which creates production secondary Room/DataStore/Koin resources,
  resolves the complete `MeshConnectionManagerImpl`, verifies fail-closed Gateway ownership, and closes the session.
  Focused Koin/App tests pass 4/4, `:app:detekt` passes, and Google Debug assembly passes. Existing radio-fleet tests
  retain four independent session/generation and hard-capacity coverage.
- The final JDK-21/en-US gate completed 2,018 tasks (394 executed, 2 from cache, 1,622 up-to-date) in 2m31s. Formatting,
  both Android Debug assemblies, tests/`allTests`, Desktop/JVM, shared KMP/iOS Simulator compilation, and F-Droid plus
  Google Debug lints passed. Exit 1 is exclusively the six recorded pre-existing Detekt findings in unmodified BLE
  (3), domain (1), model (1), and network (1).
- The final 52,961,456-byte Google arm64 Debug APK, SHA-256
  `19CF41C5125DDA5970229A43D35C9920459294CD7BEBEAB7387E5AF6167D019D`, was data-preserving installed on the Android 16
  SM-S9280. Radios `5d6e` and `1407` simultaneously completed Stage 2 and remained two distinct MeshLink GATT clients
  in one stable PID. A secondary-only disconnect/reconnect removed and recreated only `1407`; `5d6e` stayed connected.
  After the full gate rebuilt and re-signed the APK, that exact artifact was installed and both radios again completed
  Stage 2; the UI showed `2 / 4` with both connected, and both clients remained through the final 30-second background
  check with no fatal, ANR, Koin-definition, or setup-timeout signal. The installed base APK hash matches the artifact.
- This proves the reported two-radio Android failure is fixed on available hardware and, together with the fleet/source
  tests, supports one-to-four endpoint capacity. Do not claim a three/four-radio physical run, RF send/remote receipt,
  connected-radio mutation isolation, Doze, long soak, Profile/Release-device, signed/store, Windows-device, or
  iOS-device evidence. Full report: `ANDROID_SIMULTANEOUS_MULTI_NODE_BLE_REMEDIATION_REPORT_2026-08-31.md`.


## 2026-08-30 - iOS four-Meshtastic endpoint isolation and UI phase 1
- On branch `multi_nodes_` from HEAD `61109fe184d1891401703586de2977f66b0a8ca7`, iOS now uses the shared durable
  maximum-four endpoint catalog and serialized fleet bootstrap. The first endpoint retains the established root
  compatibility graph and exclusive Apple Gateway ownership; every secondary endpoint owns its own address-derived
  Room database, radio/config DataStores, Koin scope, repository/service/packet graph, Kable transport generation, and
  Room-backed message drain.
- Added exact runtime-token and expected-scope registries so stale callbacks/sessions cannot remove or publish through a
  replacement. Shared address-keyed BLE ownership remains authoritative. Shutdown quiesces scoped work and closes
  pinned resources; secondary endpoints are never Apple Gateway candidates.
- iOS Connections now has Android-parity fleet capacity/cards and endpoint lifecycle actions. Conversations exposes All
  plus endpoint views; Nodes, Settings, Channels, and endpoint Conversations use exact endpoint/generation scopes.
  Switching endpoints resets that feature to its root. App-global preferences remain shared, and MeshCore remains
  transport-pending/root-only.
- Focused evidence passes iOS runtime 19/19, radio-fleet 8/8, and prefs 36/36 JVM tests, plus changed-module
  Spotless/Detekt, Simulator Debug linkage, and arm64 Release framework linkage. The Release framework run completed
  282 tasks in 7m06s. A fresh signing-disabled generic-iphoneos Release build at
  `/tmp/ntsocial-ios-multinode-release.hgDScm` exited quietly with code 0.
- The final JDK-21/en-US root gate completed 2,015 tasks (381 executed, 1,634 up-to-date) in 3m01s. Formatting, both
  Android Debug assemblies, tests/allTests, Desktop/JVM, shared KMP/iOS Simulator compilation, cloud guards, and both
  Android lints pass. Exit 1 is exclusively the six recorded pre-existing Detekt findings in BLE (3), domain (1),
  model (1), and network (1); changed iOS/Connections Detekt stays green.
- A fresh signing-disabled Simulator app installed and cold-launched on `Codex iPhone 17` as PID 36827 and retained the
  PID after three seconds. Only the empty localized Connections screen was visually checked because no BLE radio was
  attached or injected. Full report: `IOS_MULTI_NODE_PHASE1_IMPLEMENTATION_REPORT_2026-08-30.md`.
- Do not claim concurrent physical two/four-radio BLE, Stage 2, independent mutation, restoration/background, LoRa/RF,
  remote receipt, cross-endpoint scheduling/bridging, secondary Gateway, signing, TestFlight, or App Store proof.


## 2026-08-29 - iOS shared product shell and UI parity phase 1
- On branch `multi_nodes_` from HEAD `61109fe184d1891401703586de2977f66b0a8ca7`, replaced the iOS two-tab
  engineering shell with the shared Compose `MeshtasticAppShell`, Navigation 3 multi-back-stack, and conversations,
  nodes, MeshCore, settings, channels/Wi-Fi, and Bluetooth connections graphs. The existing Kable/CoreBluetooth,
  Room/DataStore, exact-session/readback, App Group/Keychain, durable ledger, and Apple Gateway ownership boundaries
  remain unchanged.
- Implemented the previously empty iOS Settings main screen with real radio configuration, theme/homoglyph/cache,
  About/version, remote administration, and host-owned Apple Gateway readiness. Added the iOS Connections Koin graph,
  feature-deep-link handoff, Foundation date/region formatting, clipboard/URL behavior, enum preferences, Japanese iOS
  runtime strings, and final zh-TW/ja Bluetooth empty-state localization. Unsupported OTA, backup/import/export,
  notification, file, location, compass, and fleet behavior remains hidden or fail closed.
- Simulator QA caught one real host issue before delivery: the first complete-shell launch crashed because iOS lacked a
  `ScannerViewModel` registration. The final Apple binding and empty USB projection fixed it. The final localized
  signing-disabled Simulator App cold-launched as PID 22251 and returned the same PID three seconds later; the visual
  Connections check shows the five-destination shell and complete Traditional Chinese empty state.
- Final focused iOS/Windows validation passed 368 tasks, iOS runtime JVM tests are 17/17, both Simulator Debug and arm64
  Release frameworks link, and Desktop tests pass. The correct JDK-21/en-US full root gate completed 2,015 tasks:
  formatting, both Android Debug assemblies, tests/allTests, Desktop/JVM, KMP/iOS Simulator compilation, cloud guards,
  and both lints pass; exit remains nonzero only for the six recorded pre-existing Detekt findings. Post-localization
  validation passed 749 cross-platform tasks plus the 161-task arm64 Release link.
- Fresh Xcode Derived Data `/tmp/ntsocial-ios-ui-parity-final.qCQEYr` (Simulator Debug), its final-source incremental
  localization rebuild, and fresh `/tmp/ntsocial-ios-ui-parity-device.ncW63J` (generic iphoneos Release) all built
  signing-disabled with quiet exit 0 and zero output. The bundle is `1.0.0 (1)`. Node UI still emits existing Coil/Skiko
  alignment and Vico shader linker diagnostics even though both frameworks link; dependency alignment and physical
  node-chart rendering remain gates.
- Full report: `IOS_UI_PARITY_PHASE1_IMPLEMENTATION_REPORT_2026-08-29.md`. Do not claim iOS multi-radio, MeshCore
  transport, signed/entitled interoperability, physical BLE/restoration, connected-radio administration, LoRa/RF or
  remote receipt, background permanence, TestFlight, or App Store readiness from this phase.


## 2026-08-25 - Android multi-node Channel Hub UI/UX and 107-minute three-phone run
- Combined the two 2026-08-25 proposals into a bounded architecture: endpoint scopes retain independent Room,
  DataStore, Koin, BLE, connection-generation, repository, and packet ownership; each publishes only a compact
  read-only conversation snapshot into a root fleet repository. Added runtime-token source registration, exact
  endpoint/generation navigation, and a fail-closed scope host. No cross-database Paging merge, global RF scheduler,
  Gateway v3, or MeshCore multi-node expansion was introduced.
- Replaced Android's basic per-endpoint Messages entry with a modern All + endpoint-tab Channel Hub. Cards expose
  nickname/address suffix, accent, connection, unread total, channel role/index, lock, preview/time, unread badge, and
  Open-all action; a separate DataStore persists appearance. Added en/zh-rTW/ja resources and deterministic fleet,
  appearance, source-ownership, and route tests. Nodes/Settings remain scoped; Desktop retains its single-radio UI with
  an explicit no-op fleet binding.
- Device profiling isolated an unrelated but visible efficiency defect: the shared `AnimatedConnectionsNavIcon`
  restarted a one-second glow for every MeshActivity and kept static screens rendering. Replacing it with the existing
  static icon reduced a same-screen 20-second comparison from 366 to 2 frames on D2 and 62 to 8 on D3, without changing
  BLE/session/packet behavior.
- Focused radio-fleet, prefs, navigation, messaging, and Android tests passed, as did changed-module Spotless/Detekt.
  The JDK-21/en-US full gate ran 2,000 actionable tasks and found the initial missing Desktop Koin binding; after the
  no-op binding fix, the 177-task Desktop format/Detekt/test rerun passed all 32 tests. Final-source Android Debug
  builds, tests/allTests, both lints, Desktop/JVM, shared KMP, and iOS Simulator compilation pass. Root Detekt is
  nonzero only for the six recorded pre-existing findings.
- The final Google arm64 Debug APK is `1.0.6 (7)`, 52,672,620 bytes, SHA-256
  `F6E92B9C55D5837DCA920B8A5CD22713B770CFA76E693FBFA6B0DE6CE78028D8`, and passes 16 KiB zipalign. It was
  data-preserving installed on the three Android 16 phones, whose installed hashes all match. D2 and D3 each retained
  their one existing BLE radio; D1 remained the expected no-radio case.
- Per the user's instruction, the planned three-hour soak ended early after complete minutes 0-107 because no further
  problem was observed. There are 324 samples and 12/12 passing UI checkpoints. Every phone kept one PID; D2/D3 kept
  Connected/FGS for 108/108 samples; all fatal, ANR, process-death, and projection counters were zero. D1/D2/D3 CPU
  averaged 0.23%/3.85%/4.51% one-core; memory was reclaimed and threads were bounded. Correct-PID final logcat found
  no App critical/error match except four non-fatal OPPO `IJankManager` vendor messages.
- Full report: `ANDROID_MULTI_NODE_CHANNEL_HUB_UI_UX_IMPLEMENTATION_AND_THREE_PHONE_REPORT_2026-08-25.md`. Do not
  claim a three-hour, two/four-radio-per-phone, RF send/remote receipt, Doze, Profile/Release energy, signed-release,
  store, Windows-device, or iOS-device result from this work.


## 2026-08-25 - Android three-phone P1 remediation and early-ended soak
- On branch `multi_nodes_` from HEAD `2c62c6bc0`, independently reproduced all four confirmed P1s from the 2026-08-24
  report: nested `/channels` stack corruption/crash, no MeshService recovery after `MY_PACKAGE_REPLACED`, a cold
  Gateway Provider query before Koin, and permanent auto BLE scan/indeterminate animation near 0.7 core/126 rendered
  frames per second on the affected Samsung.
- Minimally fixed nested deep links to append under the current real top-level stack; boot/package-replaced restart to
  delegate persisted-address hydration and the bounded no-device grace to MeshService; app-owned idempotent Koin
  bootstrap invoked synchronously by the exported Provider wrapper and reused by Application; and a 12-second,
  lifecycle/Connected-bounded BLE scan with no indeterminate scan bar. Added focused navigation, receiver, bootstrap,
  and scanner tests. Did not change Room/packet/RF/Gateway contracts or BLE transport priority.
- The JDK-21/en-US full gate completed 2,180 tasks (428 executed, 11 from cache, 1,741 up-to-date). Formatting, Android
  Debug builds, tests/allTests, Desktop/JVM, shared KMP, iOS Simulator compilation, both Debug lints, Google Release
  R8/Lint Vital/AAB, and cloud guards passed. Exit 1 was only the six existing findings in unmodified BLE (3), domain
  (1), model (1), and network (1) sources. The final 51,393,739-byte Google arm64 Debug APK `1.0.6 (7)` has SHA-256
  `8BC272A926D58AE811B63951707B0BF233D5BBC68F23A5300058607B9652C265` and passes 16 KiB zipalign.
- Data-preserving installed the exact APK on the same three Android 16 phones; all installed hashes matched and initial
  install times stayed intact. With no manual launch, both previously selected single radios restored FGS and Stage 2
  (the screen-off OPPO took about 41 seconds); the no-radio service stopped after grace. A 100-cycle cold Provider
  stress had 100/100 complete process bootstraps, 100/100 expected shell security rejections, zero Koin/fatal errors.
  Twelve manifest deep-link cases plus Settings -> Channels -> Back on all phones retained one PID and rendered safely.
- D2 Connections 70-second average improved from reproduced 71.1% one-core/126 fps to 4.69%/8.44 fps; a manual scan
  ended at the bounded timeout and HOME stopped it while Connected remained. A separate three-minute screen-off test
  averaged D2 2.56% and D3 6.42% one-core with no errors. The user ended the planned 185-minute soak early after the
  complete minutes 0-58: 59 samples/device and 12/12 UI events, every phone online with one PID and all safety counters
  zero. Full-window CPU averaged D1/D2/D3 0.27%/7.95%/16.42%; D3 energy remains an unproven Profile-build candidate.
- Full report: `ANDROID_MULTI_NODE_THREE_PHONE_HARDWARE_REMEDIATION_REPORT_2026-08-25.md`. Do not claim a three-hour,
  Doze, multi-radio-per-phone, RF/remote-receipt, authorized-parent command, signed-release, or store result from this run.


## 2026-08-24 - Android multi-node build and three-phone single-node hardware soak
- On branch `multi_nodes_` at HEAD `d3aa2eebf16cf5e5f7f44803b17e7a68594c2b59` plus the dirty worktree,
  built `:app:assembleGoogleDebug --rerun-tasks` with JDK 21/en-US. The build passed in 5m28s (450 executed tasks),
  including the no-cloud guard. The arm64 Debug APK is 51,377,431 bytes, version `1.0.6 (7)`, SHA-256
  `402FFFA26535344AD6B404753000E73CAE6DE41656715ABBDFF07E1E9B7BAB92`, and passes 16 KiB zipalign. This run did
  not repeat the full root test/lint/Detekt/KMP gate.
- Installed with data preservation on three Android 16/API-36 phones. The final installed base APK hash matched on
  all three. One phone had no selected radio; the other two each reconnected to one existing Meshtastic BLE node,
  completed Stage 2, negotiated 244-byte writes, rendered the `1 / 4` fleet UI, and passed read-only Messages,
  Nodes, Settings, and Channels checks. Composer input/clear was tested without sending.
- A formal 20:14:46-22:14:53 +08:00 soak produced all 363/363 expected rows (121/device). All three PIDs stayed
  fixed; the two connected phones retained MeshService/connected-device FGS for 121/121 samples and remained
  connected after two screen-off phases plus Messages -> Nodes -> Settings -> Connections navigation. Continuous
  app/system logs showed no new crash, ANR, OOM, navigation/Koin failure, liveness timeout, forced reconnect, or radio
  disconnect. Threads stayed fixed at 41/41/47 and memory was non-monotonic. USB charging kept device-idle ACTIVE,
  so this is background/screen-off evidence, not Doze or battery-life evidence.
- Four confirmed Android P1 issues remain: (1) nested `/channels` deep link sets `currentTabRoute` to a non-top-level
  graph and crashes `MultiBackstack`; (2) `MY_PACKAGE_REPLACED` is manifest-declared but rejected by
  `BootCompleteReceiver`, with an additional cold DataStore hydration race; (3) an authorized parent cold query can
  reach `NtsocialGatewayProvider` before Koin is started; (4) persisted auto-scan keeps Connections rendering near
  120 Hz at roughly 0.65-0.70 core and the package-specific scan remains active across Activity STOP/screen-off.
- D3 screen-off CPU averaged 8.91% one-core across six windows while observed GATT event counts matched D2; this is
  a P1 candidate, not a proven allocation/GC root cause until a Profile/Perfetto A/B controls payload/handler paths.
  OPPO navigation latency is a P2 candidate because the D2 scan animation biases cross-device frame statistics.
- Detailed evidence, root causes, specific fixes, tests, limits, and the privacy cleanup record are in
  `ANDROID_MULTI_NODE_THREE_PHONE_HARDWARE_TEST_REPORT_2026-08-24.md`. No product source was modified. The raw
  83-file/685,054,491-byte temporary evidence directory was deleted after aggregation because it contained device,
  BLE, node, message, and UI metadata.


## 2026-08-24 - Android four-Meshtastic endpoint isolation and per-node UI phase 1
- On branch `multi_nodes_`, added the `core:radio-fleet` KMP contracts and default manager for a durable, deduplicated,
  maximum-four Meshtastic endpoint catalog with exactly one immutable legacy-primary profile, independent lifecycle
  state/generation, serialized full bootstrap, deferred registration, and failure projection. MeshCore multi-node support
  remains explicitly out of scope.
- Android retains the existing root Meshtastic graph as the Gateway v1/v2 and host-integration compatibility facade.
  Each secondary BLE endpoint now owns a fixed-address connection plus an independent Room handle, channel/local/module
  DataStores, Koin scope, config/repository/service/packet graph, and queued-message drain. Secondary Gateway access fails
  closed; host phone-location, widgets, and endpointless broadcasts remain primary-owned or aggregate-only.
- Replaced the process-global BLE pointer with address-keyed, ownership-checked active BLE connections. Database handles
  are pinned while endpoint sessions are alive, and scoped DataStores/coroutines are released when the session closes.
- Connections now exposes up to four radio cards. Android Messages/channel history, Nodes, Settings, Channels, and
  Firmware surfaces use address-last-four endpoint tabs and scope-aware ViewModel keys. Switching an endpoint resets
  that feature to its root destination to prevent a child route from being rendered against the wrong graph. The
  At that Android-phase snapshot, the existing Desktop and iOS interfaces remained single-radio and iOS received a
  no-op actual for the shared fleet panel; the 2026-08-30 iOS phase above supersedes the iOS limitation.
- Focused fleet, endpoint-store migration/deduplication/capacity, database pinning, stale BLE ownership, Android
  compilation, and root/secondary Koin graph tests pass; modified sources add no Detekt finding. The final JDK-21/en-US root
  `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile --continue` run completed 1,685 tasks
  (251 executed, 1,434 up-to-date): every requested task except root Detekt passed; Detekt remains nonzero only for the six
  recorded findings in unmodified BLE (3), domain (1), model (1), and network (1) sources. Google/F-Droid Debug lint
  passed 770 tasks (105 executed, 665 up-to-date).
- This is source and deterministic fake-test evidence, not release or radio evidence. Required follow-up includes a
  two-radio then four-radio Android run, independent channel/settings mutation and history checks, disconnect/reconnect
  storms, process death/restore, Doze/background behavior, RF send/receive, and remote receipt. No Gateway v3,
  cross-endpoint outbox/bridge, RF scheduler, or MeshCore multi-radio claim is made.


## 2026-08-23 - Android multi-radio and multi-protocol architecture proposal
- Audited Android main at `e4c97badf810cbe5088a5ad9a3e72c853a72a2a7` and the DIY node repository at
  `76219cd7562f76d1543f12944efc4379f788a233`, plus current Android BLE, Meshtastic Client API, and MeshCore 1.17.1
  companion-protocol constraints.
- Confirmed the product is technically feasible, but current transport-above state is single-radio: one active
  transport/session/address, global BLE pointer, active database, radio-owned singleton graph, unscoped WorkManager
  packet IDs, and Gateway v2 routes without endpoint identity.
- Added `ANDROID_MULTI_RADIO_MULTI_PROTOCOL_ARCHITECTURE_PROPOSAL_2026-08-23.md` at the repository root in commit
  `31e280d066045180f12be4fd47e53ac7afa694f8`. The recommended order is endpoint-scoped Meshtastic isolation,
  four-radio fleet validation, endpoint-aware outbox/Gateway v3, MeshCore transport, custom adapter, then opt-in
  overlay-only bridging.
- This task changed documentation only. No production source, Gradle configuration, schema, firmware, or runtime
  behavior was changed, and no build or device/RF test was claimed.

## 2026-08-21 - Google Play Kotlin list compatibility remediation
- From clean base `956bca67bd989063d6b128a0163de99432385ffe`, the Google Play-reported Android 15
  compatibility path was traced to `ChannelSetDataSource.updateChannelSettings`: compiling against API 35+ can bind
  Kotlin-looking `removeLast()` calls to the new Java `List.removeLast()` API, producing `NoSuchMethodError` below API
  35. Both production commonMain tail-removal sites now use `removeAt(lastIndex)`, preserving their existing guarded
  semantics, and the only remaining source occurrence in an iOS runtime JVM test queue now uses `removeAt(0)`. No
  Kotlin source call to `removeFirst()` or `removeLast()` remains.
- Focused `:core:datastore:jvmTest`, `:core:domain:jvmTest`, and `:ios:runtime:jvmTest` pass, including all affected
  channel trimming/reliability and Gateway drain tests. Changed-source formatting is clean. The JDK-21/en-US full gate
  `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile :app:lintFdroidDebug
  :app:lintGoogleDebug :app:verifyGoogleReleaseNoCloudRuntimeDependencies :app:bundleGoogleRelease --continue
  --no-configuration-cache` completed 2,137 actionable tasks (376 executed, 1,761 up-to-date) in 15m46s. Tests, both
  Debug assemblies/lints, shared KMP, Desktop/JVM, iOS Simulator compilation, cloud-runtime checks, Lint Vital/R8, and
  Google Release bundling passed. Root Detekt remains nonzero only for six pre-existing findings in unmodified BLE (3),
  domain precise-location sharing (1), model (1), and network (1) sources.
- Android release metadata is now `versionCode=7` / `versionName=1.0.6`. The final 24,462,232-byte Google Release AAB
  has SHA-256 `A188B65F5EEEF7C37E923610745E2B28A77EC72FF3089D7865BE1A73D35E10D0` and passes official bundletool 1.18.3
  validation. Direct `dexdump` inspection of all three bundled DEX files found no
  `java.util.List.removeFirst/removeLast` method reference, and the exact Play-reported
  `ChannelSetDataSource...ExternalSyntheticApiModelOutline0` class is absent. The AAB is locally unsigned because no
  upload keystore is present; it is not itself Play-uploadable and no Play acceptance or new physical-device run was
  claimed.

## 2026-08-21 - Android English, Traditional Chinese, and Japanese first-launch selection
- From clean base `3d7befff2a4db14f2d0c42b95f3406f479857abe`, the Android host now puts an exact
  three-choice language screen before the existing MeshLink introduction only when the introduction is unfinished and
  no language was previously persisted. Returning Taiwanese installs that completed the introduction bypass it, while
  an interrupted first run that already chose a language resumes the original introduction. A combined nullable
  `AppLaunchPreferences` DataStore snapshot prevents the individual flows' fallback values from treating a returning
  install as fresh. Selection awaits the locale write before AppCompat recreation.
- The authorized parent source at `C:\Users\cth\Documents\GitHub\NTsocial_release` was inspected read-only at
  `bd51abd40f0ad2442e8fd6de370166da8867ac92`; its pre-existing dirty `app/build.gradle.kts` was not modified. MeshLink's
  existing US, Taiwan, Japan, and background PNGs are byte-identical to the parent assets (SHA-256 respectively
  `8156FBD2...ECAB`, `6C7F505D...7438`, `2D1B499F...0595`, and `9BA147A3...177D`), so no binary asset was copied. The
  Android Compose implementation preserves the parent screen's exact placement, dimensions, spacing, blue radio
  colors, native-language labels, and select-and-advance behavior.
- Android `locales_config.xml` and the in-App picker expose exactly `en`, `zh-TW`, and `ja`. The local Settings surface
  always contains `App language`, including API 33+, and its radio dialog mirrors the same three options. Existing
  shared translations and non-Android locale behavior are otherwise unchanged. New base/zh-rTW/ja labels are indexed
  through the repository string-sorting workflow.
- Focused `:core:prefs:jvmTest` and `:feature:intro:jvmTest` cover the authoritative cold-start read, awaited durable
  locale write, fresh install, completed Taiwanese upgrade, and interrupted persisted-locale paths; Android compilation,
  Spotless, and changed-module Detekt pass. The full JDK-21/en-US gate
  `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile :app:lintFdroidDebug
  :app:lintGoogleDebug --continue --no-configuration-cache` completed 1,960 actionable tasks (614 executed, 3 from
  cache, 1,343 up-to-date) in 22m55s. Tests, both Android Debug assemblies and lints, Desktop/JVM, shared KMP, and iOS
  Simulator compilation passed. The command was nonzero only for six findings in unmodified BLE (3), domain
  `SetPreciseLocationSharingUseCase` (1), model (1), and network (1) files; the domain `LongMethod` is an existing source
  finding omitted from the prior five-finding inventory. No physical-device or emulator visual/locale smoke test was
  performed.

## 2026-08-20 - QR secondary replacement and immediate background apply
- The shared scanned-channel dialog now has three explicit behaviors. Add remains additive and keeps all existing
  channels. An `add=true` QR may be switched to secondary replacement only after a complete current primary and LoRa
  state is available; the primary is locked, valid current and incoming secondaries are selectable, and selected
  incoming channels fill explicitly released secondary slots before appending. A full-config QR retains the existing
  complete replacement behavior and its explicit LoRa config. A new en/zh-rTW/ja description explains the partial
  replacement contract.
- Secondary replacement treats internal disabled placeholders as released slots and simulates the production reliable
  normalizer before enabling Accept. Every current secondary that the user chose to retain must remain at the same
  numeric slot after normalization. This prevents silent channel-index rotation from unmatched interior removals,
  placeholders, or semantic duplicates while still allowing an explicitly removed trailing channel to disappear.
- Add and secondary replacement submit null LoRa write intent. `ChannelReliabilityManagerImpl` captures the current LoRa
  config inside the serialized mutation context for normalization and expected exact readback, but null input now
  unconditionally skips `set_config`, including when the local config flow changes between channel writes. Explicit
  full-config replacement still sends and verifies the exact requested LoRa config. The existing channel writes,
  matching ACK/NAK handling, commit, stable fresh readback, and Gateway fail-closed activation path are unchanged.
- Before Accept, a localized en/zh-rTW/ja notice explains that the node normally restarts, reconnect and verification
  take about 30 seconds, the channel list may not update immediately after returning, and the admitted operation will
  continue in the background. Accept remains a single action: it synchronously claims the operation and then dismisses
  the QR dialog. The admitted apply/readback remains in its existing non-cancellable section, dismissal does not cancel
  it, and terminal or invalid outcomes are reported through the app-wide alert surface. Operation/dismissal state is
  isolated so completion of an older accepted QR cannot dismiss or consume a newly mounted QR dialog.
- Focused dialog tests cover the pre-Accept wait notice, exact confirm-before-dismiss ordering, Add, one-for-one
  secondary replacement, slot-stability rejection, internal placeholders, current-state readiness, current capacity
  recomposition, and full-config replacement. ViewModel tests cover synchronous claim, dismissal, cancellation
  resistance, post-dismiss invalid/NAK alerts, and second-QR isolation. Domain tests cover stable and racing null-LoRa
  no-write behavior plus the exact explicit LoRa payload.
- The JDK-21/en-US full gate
  `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile :app:lintFdroidDebug
  :app:lintGoogleDebug --continue --no-configuration-cache` completed 1,960 actionable tasks (592 executed, 6 from cache,
  1,362 up-to-date) in 19m49s. Formatting, tests, Android Debug assembly, both lints, Desktop/JVM, shared KMP, and iOS Simulator
  compilation passed; the root command was nonzero only for the same five documented pre-existing Detekt findings in
  unmodified BLE (3), model (1), and network (1) files. This directly changes Android and shared Desktop UI behavior;
  the current iOS shell does not expose the dialog. The user's device test confirmed the preceding Replace and
  immediate-dismiss behavior before this notice was added; the new notice has not yet received a device visual check,
  and no new camera, connected-radio, or RF run was performed for this follow-up.

## 2026-08-17 - Android channel-wait semantics and Gateway/parent route-catalog recovery
- The main-repository channel fix changes shared KMP reliability and Compose code used by Android and the shared
  Desktop surface, while preserving the exact-session/fail-closed contract used by the iOS runtime. Firmware admin
  outcomes are now typed as acknowledged, explicit rejection, or unconfirmed. A successfully decoded matching
  `Routing` response whose error is not `NONE` is an explicit rejection; a malformed matching response, queue/session
  loss, no response, node reboot, readback timeout, or radio/session rotation is unconfirmed rather than a red failure.
  A commit followed by the 30-second readback timeout, or an exact first readback followed by a context/generation race
  during stable capture or protected-snapshot persistence, returns `VERIFICATION_PENDING`. Only a complete stable
  readback that still belongs to the same exact context and mismatches the requested settings returns
  `READBACK_FAILED`. Pending identity remains fail closed for Gateway ingress.
- QR replace, Channels reset, and local Channel Config now render a non-red, non-modal progress/information surface
  explaining that the node normally restarts, reconnection/verification takes about 30 seconds, and the user may leave.
  The QR dialog permits Back, outside dismissal, and Cancel while applying. Once admitted, these three apply jobs finish
  their transaction/readback in a non-cancellable section even if navigation clears the ViewModel. Terminal results use
  the app-wide alert; pending/no-response outcomes do not. en, zh-rTW, and ja resources and focused UI/domain/data tests
  cover these distinctions. These are shared-source/build results; no Windows or iOS device validation was performed.
- A separate Android-only Gateway race came from publishing the configured `ChannelSet` and opaque radio generation in
  independent state flows. Provider `/v2/status`, `/v2/channels`, and command-route validation could therefore observe
  different generations during one refresh. `GatewayCatalogSnapshot` now publishes the channel set and generation
  atomically, and all three consumers derive their view from that snapshot. No PSK or other secret is added to the
  exported surface.
- The authorized `NTsocial_release` parent had a second catalog race: it selected a Provider primarily by legacy-v1
  readiness/package order, queried v2 status and channel rows without a closing generation fence, and could replace a
  last-known-good catalog with a transient empty result just as the LoRa dialog opened. Its Android changes score all
  installed candidates by coherent bindable v2 overlay catalog and connection state, serialize refresh, use bounded
  status -> channels -> status generation validation, retain the active last-known-good catalog across transient probe
  failure, and replace it only with a connected coherent alternative. Opening the manual route dialog requests a fresh
  catalog and shows a loading state instead of a false empty warning. Stable manual bindings continue to allow several
  logical NTsocial channels to share one radio route. Parent changes are limited to its Gateway manager, Chat/Channel UI,
  three locales, and focused binding/provider/route tests; no parent proprietary code was copied into this GPL repo.
- Main focused validation passed
  `:core:domain:jvmTest :core:data:jvmTest :core:ui:jvmTest :feature:settings:jvmTest
  :core:service:testAndroidHostTest --continue` with 263 tasks green. The full JDK-21/en-US gate
  `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile :app:lintFdroidDebug
  :app:lintGoogleDebug --continue` finished `BUILD SUCCESSFUL` in 8m13s with 1,960 actionable tasks
  (286 executed, 1 from cache, 1,673 up-to-date). Functional tests, APK assembly, both Android lints, shared KMP, and iOS
  compilation passed. Root Detekt remains nonzero only for five pre-existing findings in unmodified BLE
  `JvmDesktopBluetoothPairingService` (3), model `NtsocialGatewayIdentity` (1), and network `BleRadioTransport` (1).
  The parent focused suite passed 27/27 and its `:app:assembleDebug` passed with 42 tasks.
- The final F-Droid arm64 MeshLink Debug APK is 51,877,548 bytes, package
  `com.ntsocial.meshlink.fdroid.debug`, version 1.0.3 (4), SHA-256
  `1061020C17D6131115D829BE864715D18A8B34A92418F6DC691EB472B6758491`. The final parent Debug APK is
  32,707,210 bytes, package `com.ntsocial.android.debug`, version 1.5.5 (35), SHA-256
  `51EF930312D11A6278B47444B8091909584C6C48094B7817E98470E26704C3F4`. Both exact artifacts are installed on
  an Android 16 SM-S9280 and CPH2695; on-device package versions, base-APK sizes, and SHA-256 hashes match the local
  artifacts on both phones.
- On the Android 16 Samsung, fresh-install BLE scan/connect permission was granted, the existing node was discovered and
  connected, and Stage 2 completed. A secondary channel was reversibly renamed from `SignalTest` to `SignalTest2` and
  then restored, exercising two complete manual channel sends. During both operations the UI showed the neutral green
  message that the node was restarting or reconnecting, confirmation takes about 30 seconds, this is not an error, and
  the user may leave. System Back successfully left the screen while applying; both transactions continued in the
  background through reboot, reconnect, and fresh readback, and the original name was restored. There were zero terminal
  popups, `RADIO_REJECTED`, readback-mismatch, or fatal events. After both event storms the parent LoRa dialog still
  listed all five configured routes (one primary and four secondary), and all five were marked online.
- The parent test channel was then reversibly bound to that secondary route and sent one synthetic test envelope. Retained
  logs show exact counts of 9 Gateway receives, 9 accepted authorizations, 9 packet enqueues, 9 dequeues, 9
  `to_radio` dispatches, and 22 QueueStatus events, with zero rejects and zero fatal events. The binding was removed and
  the original state restored. This proves local protected-Gateway acceptance and dispatch to the connected radio queue;
  it does not prove RF airtime, remote receipt, or end-to-end delivery. A second node and remote receive remain required.

## 2026-08-09 - Android Gateway Debug/Release reciprocal trust matrix
- The connected Android 16 SM-S9280 currently has `com.ntsocial.android.debug` 1.5.3 (33) and
  `com.ntsocial.meshlink.google.debug` 1.0.3 (4), not the globally published `com.ntsocial.android`
  package. Both installed APKs have the same current signer SHA-256
  `B578F8445925AEA570F7E916C335172559773D7B6EC92DB0D76355E0E8F3FF8D`; the parent has both Gateway
  permissions granted, its MeshLink preference is enabled, and the selected provider is `meshlink`.
  There is no versionCode/versionName gate and no current package, authority, permission, or signer
  mismatch in this installed debug pair.
- MeshLink application IDs and the exact allowed parent package IDs remain unchanged. Fixed production
  (`29EF...E646`), stable team-debug (`C67E...FD61`), and retained development-debug (`B578...3FF8`)
  pins are recognized by both Debug and Release builds and by the Gateway `knownSigner` permissions.
  A debuggable MeshLink host additionally trusts only the exact `com.ntsocial.android.debug` package
  when both Apps' complete nonempty current-signer sets are identical. Release hosts, empty or unknown
  signer identities, partial multi-signer overlap, and arbitrary package names still fail closed.
- MeshLink verifier regressions pass 9/9; the complete `core:service` host-test suite plus its
  Android-main/host-test Spotless and Detekt gates pass. Root
  `spotlessCheck assembleDebug test allTests kmpSmokeCompile` and both Debug flavor lints completed
  1,856 actionable tasks successfully. Google Release AAB/R8/Lint Vital/no-cloud verification also
  passes (753 actionable tasks); the local AAB remains an unsigned release-pipeline artifact. Root
  Detekt remains nonzero only for the five known pre-existing findings in unmodified BLE (3), model (1),
  and network (1) sources. The `NTsocial_release` reciprocal
  matrix tests pass 9/9; its complete Debug unit-test task plus Debug and R8 Release APK assembly pass.
  A final independent cross-repository review confirmed all four controlled Debug/Release pairings and
  found no functional P0-P2 or missing manifest/permission gate.
- The final Google arm64 Debug MeshLink APK is 53,570,324 bytes with SHA-256
  `ACE5378FC35C161DEB09A0EAA195860DE7289754C03E23C36BCB640A393378E2`; the final NTsocial Debug APK
  is 32,552,070 bytes with SHA-256
  `1881005631DFCC132FC8963160A4AA9F8E42E813C18380E1AEC7A97F954125E2`. Both were installed as
  data-preserving updates and the installed base-APK sizes/hashes matched exactly. MeshLink first-install
  time remained `2026-08-09 07:30:12`; NTsocial remained `2026-08-08 20:32:59`.
- After the final pair was installed, a real parent-process restart appended 97 log lines with no new
  MeshLink/Gateway `SecurityException`, `Untrusted`, permission denial, or Provider-query failure while
  both processes stayed alive and both Gateway permissions remained granted. The existing debug E2E
  bootstrap then started the real parent foreground service; it logged no Gateway authorization failure.
  The handset stayed keyguard-locked, so no fresh parent UI status or connected-radio command was
  retained; this is local-debug identity/startup evidence, not a current Play-delivered pair, RF
  transmission, or remote-receipt proof.
- Reciprocal trust is synchronized with `NTsocial_release`: its exact MeshLink debug-package allow-list
  recognizes both the stable team-debug and retained `B578...3FF8` development-debug signers, while
  its exact release-package pin is unchanged. This covers the four Debug/Release pairings for the
  controlled project signers without accepting an arbitrary locally signed App. Android was the only
  affected product track; Windows and iOS behavior did not change.

## 2026-08-09 - Android Stage-2 connection deadlock and Messages UI recovery
- Diagnosed the reported missing Messages QR/share FAB and disabled channel composer on a USB-connected Android 16
  SM-S9280 with a standard Meshtastic radio. Baseline BLE/GATT, Stage 1, and Stage 2 all completed at the transport
  layer, but canonical state remained `Connecting`; the composer accessibility node was `enabled=false` and the FAB
  was absent. The stall guard later forced a reconnect.
- Root cause was commit `524778151`: `MeshConfigFlowManagerImpl` began awaiting
  `MeshConnectionManagerImpl.onNodeDbReady(epoch)` before publishing canonical `Connected`; `onNodeDbReady` awaited
  exact-session startup admin packets, while `PacketHandlerImpl` dequeued and sent packets only after canonical
  `Connected`. Its `finally` also restarted indefinitely while the blocked queue remained nonempty. Both UI symptoms
  use that same canonical state gate; the barcode provider/navigation and text field implementation were intact.
- Fixed the shared `PacketHandlerImpl` with explicit `READY`/`WAIT`/`REJECT` dispatch semantics. Ordinary history
  packets remain queued during `Connecting`; exact configured-session control packets may bypass them; canonical
  `Connected` wakes retained work; Gateway traffic remains canonical-Connected-only; stale/disconnected epochs fail
  closed through the atomic exact-session send. Rejected dequeued work completes response/dispatch/Gateway ownership
  in `NonCancellable`, preventing reconnect cancellation from orphaning callers.
- Deferred registration now occurs immediately before actual radio dispatch, rather than while an ordinary item is
  still waiting. This keeps zero-ID QueueStatus fallback bound to the sole in-flight item and prevents startup status
  from falsely completing retained history work. Independent read-only concurrency review found no remaining P0-P2.
- Added three PacketHandler regressions: configured exact control bypasses a waiting ordinary item in canonical
  `Connecting` and handles zero-ID QueueStatus correctly; an ordinary awaited item waits without scheduler spin and
  resumes on canonical `Connected`; Gateway cannot use the pre-connected exact-control window. The focused suite is
  20/20. Changed-module Spotless, Detekt, and complete data JVM tests pass.
- The required root `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile` and both Debug
  flavor lints ran with JDK 21/en-US and `--continue`: 1,960 actionable tasks completed; only the five documented
  pre-existing Detekt findings remain in unmodified BLE (3), model (1), and network (1) files. Android, Desktop/JVM,
  and shared KMP compilation/tests/lints therefore completed; no Windows or iOS device run was performed.
- Clean-uninstalled and installed the Google arm64 Debug APK. Artifact size is 52,299,121 bytes, version 1.0.3 (4),
  SHA-256 `04DF73D0911CF5FC2268654DF33110B4B03B8BF62E349508226E0405D5DD5846`; the installed base APK hash matched exactly
  and first-install time reset. After onboarding and reselecting the same radio, logcat showed Stage 2 followed by four
  exact-session startup sends and matching successful QueueStatus completions, then retained ordinary work success.
- The same process/session stayed connected for over five minutes with zero handshake-stall, forced-reconnect,
  app-level disconnect, queue-timeout, RadioNotConnectedException, or fatal events. The Messages FAB was present; its
  QR scanner opened with Camera permission; channel sharing generated a Meshtastic QR and URL (secret-bearing evidence
  intentionally not retained in prose); a channel composer was enabled/focusable, accepted `abc123`, showed `6/200`,
  and was cleared to `0/200` without pressing Send. This is one-device Debug evidence, not Play/RF/remote-receipt proof.
- Preserved the user's pre-existing `config.properties` changes; task source changes are limited to PacketHandler
  production/test code plus this status documentation.

## 2026-08-08 - Android local-debug Gateway signer interoperability
- The Android Gateway caller verifier keeps the fixed NTsocial release (`29EF...0646`) and stable
  team-debug (`C67E...FD61`) pins for both MeshLink build types. A debuggable MeshLink host now also
  accepts only `com.ntsocial.android.debug` when the parent's complete current signer set is nonempty
  and exactly equals the host's complete current signer set. Release hosts never enable this path, and
  partial multi-signer overlap fails closed. This historical 2026-08-08 behavior is supplemented by
  the retained development-debug fixed pin documented in the 2026-08-09 section above.
- The verifier derives host debuggability from `ApplicationInfo.FLAG_DEBUGGABLE` and reads current
  host/client signers through `PackageManager`, so `core:service` does not depend on the App module's
  `BuildConfig`. Fixed pins continue checking signing history for legitimate certificate rotation.
- At that 2026-08-08 snapshot no development signer digest was added to source; the later retained
  development-debug pin is documented in the current 2026-08-09 section above. Both F-Droid and
  Google Debug merged manifests are debuggable; both Release merged manifests omit the debuggable
  flag.
- Focused trust tests pass 8/8; current service host plus JVM tests pass 122, and the full root test
  outputs contain 2,793 passing tests. Changed service Android main/host-test Detekt and Spotless pass.
  F-Droid/Google Debug APK assembly, both Debug lints, KMP smoke compilation, and the Google Release
  no-cloud/R8/Lint Vital/AAB pipeline pass. Root Detekt remains nonzero only for five pre-existing
  findings outside this change.
- The exact local-debug NTsocial and F-Droid MeshLink APKs, both signed by the same machine-local
  certificate without pinning it, were installed with data-preserving updates on three Android 16
  phones. All three returned v1/v2 status rows, issued a capability, accepted the verified sender and
  capability, then returned the correlated expected `COMMAND_REJECTED/invalid_route` event for a
  deliberately nonexistent route. UI discovery, Open-app launch, toggle recovery, and parent-process
  restart recovery passed; an ADB-shell Provider query remained untrusted. No Meshtastic node, RF,
  remote receipt, signed Play artifact, or F-Droid-release signer test was available.

## 2026-08-07 - iOS third-track source implementation and simulator verification
- Added iOS as the repository's third product track on branch `codex/feat/ios-meshlink`, from base
  HEAD `988d3327e45772e73dd2147ee7fffe4a26d370a6`. The implementation remains uncommitted at this
  handoff; do not present it as a published tag, signed artifact, TestFlight build, or App Store release.
- Added `core/gateway` Apple Gateway v1: strict versioned/length-delimited command authentication,
  HMAC-SHA256 verification, nonce replay defense, 32-byte Base64URL/120-second caller-source-slot-
  generation-capability routes, opaque generation rotation, deterministic packet IDs, a private restart-stable
  256-record-per-caller ledger, reclaimable App Group mailbox claims, append-only results, bounded
  128-row complete-`NM` overlay ingress, and paged stable-only native broadcast-text changes. Port 256
  is outbound; legacy 497 remains receive-only. `ACCEPTED_LOCAL` is written only after durable Room/
  retry admission and accepted-ledger commit and never means RF or remote delivery. Additive schema-v1
  `overlay_epoch_state` stores each epoch's monotonic high-water outside bounded ingress retention,
  backfills from retained rows for an early v1 database, and clears only on explicit reset. An exact
  accepted-ledger record is authenticated and replayed before process-local route resolution after a
  ledger-commit/result-publication crash; it republishes the original packet ID without a second radio
  admission, while changed content conflicts. Failure of the final accepted-ledger commit after radio
  admission remains a retryable `PENDING_PROVIDER_WAKE`/`QUEUE_FAILED`, not proof of no scheduling.
- Added atomic shared `RadioSessionState`. Gateway readiness and inbound projection require selected radio equal
  to active radio, one monotonic session epoch with config completed for that exact epoch, the selected radio's
  active Room database, positive complete-channel readback generation, a non-mutating final snapshot generation,
  Bluetooth permission/enabled, connected transport/App state, history epoch, and complete channel fingerprint.
  Route issue and durable admission use the same guard; radio selection/channel admission share
  `ChannelOperationLock`. An inbound-session revision can trigger a drain even when the other readiness fields are
  unchanged. A `READY` transition drains pending work, and retryable commands use one coalesced
  500/1,000/2,000-ms job with at most three attempts. Exhausting the 64-command per-pass budget schedules a
  delayed continuation so command 65 is neither starved nor processed in a busy loop.
- Hardened radio replacement across shared code. A generation-bound callback facade and one synchronous validation/
  side-effect lock revoke retired transport connect/disconnect/data callbacks before teardown. Selection pauses and
  awaits old radio ingress plus registered child writes, drains and awaits the retired outbound queue/status/log
  generation, switches the per-radio database, loads that database's node cache from a direct authoritative snapshot,
  and only then resumes and starts the replacement transport. Stale work cannot write into or complete the replacement
  database/session. Expected epoch survives admin/readback request, queue admission and dequeue through the synchronous
  transport-send linearization point, including same-address reconnect with a reused transport object.
- Manual/QR apply, protected-channel reconcile, and built-in provisioning share one mutation contract: validate the
  exact session and admin owner, invalidate Gateway ingress, perform exact-session firmware mutations, obtain a
  correlated fresh readback, then activate only the verified final identity. Fresh readback reuses firmware's `69420`
  config-only sentinel and a host-exclusive owner/token that may start only after prior FULL Stage 2 and without another
  owner; its dedicated completion flow cannot be satisfied by a stale FULL response, parallel handshake, old session,
  or generic readback generation. Mutation serialization does not hold the short operation lock while waiting for the
  producer commit. Rejection, mismatch, timeout, or session replacement remains fail closed.
- Durable Gateway rows retain their accepted source-channel identity. Actual iOS drain revalidates the active exact
  session/ingress and slot-derived PSK/LoRa source identity, then keeps the operation boundary through exact-session
  queue admission and matching firmware QueueStatus. A slot/PSK change before dispatch fails closed rather than sending
  to the new channel occupying that numeric slot.
- Added the iOS runtime and host: static `MeshLinkKit`, SwiftUI/Xcode wrapper, real Koin composition,
  shared repositories/Room/`DirectRadioControllerImpl`/`MeshServiceOrchestrator`, Room-backed durable
  queue replay, Kable/CoreBluetooth availability and peripheral reconstruction/restoration setup,
  negotiated write length, Security.framework random bytes, file-backed DataStore, App Group SQLite,
  shared-Keychain 32-byte HMAC bootstrap, payload-free Darwin hints, source Privacy Manifest, App Icon
  asset catalog, and `ntsocial-meshlink://process`. The focused UI covers host/App Group/Bluetooth/background/parent
  readiness plus scan/select/connect/disconnect/forget; routes, native-text diagnostics, results,
  Gateway reset/panic wipe, maps, and broad settings are deferred to avoid duplicating the parent.
- Source identifiers are companion `com.ntsocial.meshlink.ios`, framework
  `com.ntsocial.meshlink.ios.framework`, parent caller `com.ntsocial.ios`, App Group
  `group.com.ntsocial.meshlink.gateway`, and Keychain suffix `com.ntsocial.meshlink.gateway`.
  These are source declarations only; no signed dual-App entitlement/provisioning proof exists.
- Current-source focused evidence is 135/135: domain 16/16, data 104/104, and `:ios:runtime:jvmTest` 15/15
  (session/database guard 4, bounded retry scheduler 3, command-drain budget 3, durable dispatch identity 2,
  inbound-projection signal 1, shell/deep-link 2). `:core:gateway:jvmTest` separately passed 36 tests. Deterministic
  coverage includes same-address/same-transport reconnect, exact admin/readback/raw send, firmware-69420 host
  owner/token correlation, readback producer progress, premature activation, ambiguous mutation results, and
  accepted-before-drain source replacement. The final bounded audit found no reproducible P0/P1 in these boundaries.
  Current Spotless, five changed modules' Detekt, Gateway/runtime iosArm64 and iosSimulatorArm64 compilation,
  iOS runtime simulator-test compilation, static framework link, and diff hygiene passed. With JDK 21, en-US
  `JAVA_TOOL_OPTIONS`, and one worker, current-source root
  `assembleDebug test allTests kmpSmokeCompile --continue --console=plain` completed `BUILD SUCCESSFUL` in 2m:
  1,406 actionable tasks (333 executed, 1 from cache, 1,072 up-to-date). Native iOS test link/run tasks remained
  `SKIPPED` by convention. Root `detekt --continue` is nonzero only for five pre-existing findings:
  `JvmDesktopBluetoothPairingService.kt` line 154 `TooGenericExceptionCaught` and lines 143/188 `ThrowsCount`,
  `NtsocialGatewayIdentity.kt` line 168 `MagicNumber`, and `BleRadioTransport.kt` line 246 `ThrowsCount`.
  Changed modules and Spotless are green. The separately authorized
  `NTsocial_release` Swift adapter includes separate current-catalog/same-epoch historical source resolution and
  authenticated `enqueueNativeBroadcastText` while its composer remains deferred. A first-send
  `pendingLocalAcceptance` is durably recorded as exact message/attempt/transport `.queued` plus `.admission` and
  remains pending until a same-attempt terminal result; later `ACCEPTED_LOCAL` acknowledges and advances once.
  Parent-private restart correlation preserves the final social-header message ID, attempt, multipart kind/index/count,
  transfer ID, and logical channel instead of reconstructing them from a chunk wrapper. Slot-indexed duplicate source
  identities remain available for outbound routing; canonical/history projection collapses them deterministically
  (PRIMARY first, then lowest slot) and rejects conflicting security semantics. Retention gaps, malformed envelopes,
  and lost/expired multipart transfers write a durable gap/quarantine/abandoned-transfer terminal record before a
  bounded cursor advance; deterministic poison may be skipped only after that record, while transient store/projection
  failure never advances the cursor and already-evicted rows are not claimed recoverable.
  `swift test --package-path ios --filter AppleGatewayAdapterTests` passed 27/27 focused tests and the complete
  SwiftPM suite passed 668/668, including the preceding restart/pending/multipart/duplicate/gap contracts plus shared
  HMAC/identity vectors, exact Keychain service/account, additive overlay-epoch migration, native-text enqueue, and
  commit-before-cursor behavior. The parent release build is green; this remains source/build evidence, not signed
  two-App interoperability proof.
- A final pre-host audit exposed a Koin construction cycle between `NtsocialGatewayRepositoryImpl` and
  `IosDurableMessageQueue` that caused a launch-time stack overflow. The current source removes the queue-to-repository
  dependency in favor of cycle-free `GatewayIngressSessionGate.activeSessionEpoch`; post-fix runtime 15/15,
  runtime Detekt, Simulator compilation, and framework link pass. From fresh Derived Data
  `/tmp/ntsocial-ios-final-fixed.2fROK9`, the signing-disabled Simulator Debug `xcodebuild clean build -quiet`
  exited 0 with zero output. The bundle installed and cold-launched on `Codex iPhone 17`
  (`E3249756-57AF-4D9C-AA2B-3332E9309529`) as PID 67524 and returned the same live PID after two seconds.
  A fresh signing-disabled generic-iphoneos Release clean build from
  `/tmp/ntsocial-ios-final-device.YaTk5N` also exited 0 with zero output; its bundle reports 1.0.0/build 1 and
  contains `Assets.car` plus root `PrivacyInfo.xcprivacy`. These are unsigned source-build/simulator results,
  not signed archive, provisioning, entitlement, physical-device install, BLE/restoration, connected-radio, RF,
  TestFlight, or App Store evidence.
- Native iOS tests remain disabled in the repository convention, so `iosSimulatorArm64Test` can
  report success while link/run tasks are skipped; current native evidence is compilation only. The prior
  Skiko ICU 18.5-versus-17.0 warning is closed by a build-phase script that fail-closes unless the member
  has the expected data-only layout, then relinks its LC_BUILD_VERSION to iOS 17 and atomically rebuilds
  the static archive. This closes the observed simulator linker warning, not the signed archive gate.
- The only known open source P2 is bounded liveness after an exact readback has been admitted, its caller times out or
  cancels, and firmware never emits a late response: the host owner remains fail closed for that same epoch, so a
  reconnect/new epoch is required to restore exact readback/identity activation. This is not wrong-channel dispatch,
  cross-session mutation, or data disclosure; ingress remains closed and durable dispatch identity is revalidated.
- Still required: matching Apple Developer identifiers/profiles and signed App Group/Keychain/
  Darwin/deep-link two-App proof; physical-device Bluetooth permission/scan/handshake/restoration;
  connected-radio durable admission; LoRa airtime, second-radio reception, parent canonical import,
  retry/restart and Android/iOS interoperability; native test execution; signing/archive/final
  Privacy Manifest and linked-API review/licensing/TestFlight/App Store gates. Never claim permanent
  background execution. The user's verbal
  Play-listing statement for Android was not repository-verified and does not override the existing
  Android Play build/submission evidence in `AGENTS.md`.

## 2026-08-06 - Android channel persistence and phone-GPS reliability implementation
- Implemented one serialized verified local-channel apply path from base `4151d6227`: ensure the
  local admin session, materialize full slots, require queue admission plus a matching
  request/sender `Routing.NONE` for each edit and commit, then require a fresh complete config
  readback before reporting success or updating local observed state.
- QR and local manual edits now use that coordinator. The QR dialog stays open while applying,
  reports explicit failure, and cannot remain stuck in Applying after an exception. Message
  migration after a manual channel edit occurs only after verified radio convergence.
- Added explicit opt-in, per-stable-radio channel snapshots in App-private DataStore. Automatic
  recovery is limited to provably missing secondary placeholders, revalidates identity, generation,
  capacity, primary, and LoRa context, attempts once per radio generation, and fails closed on every
  conflict. Protected reconciliation runs before built-in NTsocial provisioning; the existing
  provisioner remains a legacy queue/cache path and is not claimed as equally verified.
- Reworked complete-config collection into a generation-scoped handshake barrier so channel/LoRa
  values commit atomically at `config_complete`; interrupted handshakes preserve the last complete
  cache. Routing NAK now completes awaited responses as failure rather than success.
- Exposed the existing per-node phone-location preference in Device -> Position and centralized
  desired-state reconciliation across connected node, fixed position, Fine or Coarse permission,
  system location, process lifecycle, reconnect, node switch, and restart. Start/stop/restart use
  one lock, so teardown cannot race a late start.
- `MeshService` now uses the location foreground-service type only while explicit phone-location
  opt-in, permission, and system location are active; opt-out stops the listener. No background
  location permission or cloud service was added.
- Focused model/prefs/datastore/data/domain/service/UI/settings tests pass. With JDK 21 and en-US
  locale, root `spotlessApply spotlessCheck assembleDebug test allTests` and
  `kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug` pass. Root Detekt still reports only
  the seven pre-existing findings in unmodified BLE (3), model (1), network (1), and data (2)
  sources; this task adds none.
- Shared channel changes pass Desktop/JVM and KMP compilation without changing Windows IPC, UI,
  branding, or packaging. No Meshtastic radio was attached, so real QR/readback, reconnect repair,
  Android 11-17 lifecycle/permission behavior, RF delivery, and second-radio phone-position
  reception remain mandatory release evidence.

## 2026-08-04 - Private-message fork/upstream investigation and repair plan
- Compared fork main fec591ec8 with upstream main e51fdf8a5 from merge base c0d95d6ac and traced
  native TEXT_MESSAGE_APP direct messages, Android WorkManager/radio admission, Room contact
  persistence, paged Contacts identity, and the NTsocial PRIVATE_APP / port-256 Gateway path.
- Native DM field construction remains upstream-compatible: app channel 8 becomes wire channel 0
  with PKI/public key, while normal text stays on port 1. Do not replace native DM with port 256.
- Confirmed the fork lacks upstream 93b24572: paged contacts discard stored contact_key and
  recompute it from a possibly-null local identity, which can collapse outgoing-latest DMs onto the
  self key after cold start/reconnect. The plan ports the small Pair contact-key fix without a Room
  migration and isolates private inserts from broadcast-only Gateway identity capture.
- Confirmed AndroidRadioControllerImpl silently returns when its Activity-lifecycle AIDL binder is
  null; SendMessageWorker then marks ENROUTE/success. The plan moves message send to the existing
  in-process path, preserves a throw/retry stopgap, and adds awaited local packet-queue admission.
- Confirmed the Gateway has no explicit private-send command. SEND_CHANNEL_TEXT is broadcast-only,
  and existing directed port-256 envelopes remain on configured channels 0..7 rather than the PKI
  transport. The plan adds an additive v2, known-node, non-self, PKI-only private-overlay command
  and rejects any SEND_CHANNEL_TEXT carrying EXTRA_TO to prevent silent broadcast.
- Also scoped the firmware-2.8 QueueStatus res=35 fix, SendMessageUseCase error propagation,
  address/renumber hardening, metadata-only diagnostics, automated tests, and a two-radio RF matrix.
- Added NTSOCIAL_MESHLINK_PRIVATE_MESSAGE_REPAIR_PLAN.md at repository root. This task changes
  documentation only; it does not implement Kotlin fixes or claim RF/remote-receipt validation.

## 2026-07-31 - Official Meshtastic channel-QR interoperability repair
- The supplied official Meshtastic QR is a valid dense eight-channel `add=true` URL. The current
  ZXing decoder, URI parser, Base64 decoder, and `ChannelSet` protobuf parser all accept it; no
  proprietary or alternate QR format was needed.
- Fixed the Android-only live scanner's two practical blockers: CameraX analysis no longer relies
  on its VGA default and instead requests a 1280-by-960 4:3 stream, while ZXing is constrained to
  QR with `TRY_HARDER`. Scanner sessions now deliver only the first result/dismissal on the main
  thread and dispose their analyzer, Preview, ImageAnalysis, and executor without `unbindAll`.
- Fixed the post-decode ADD path for full eight-channel official QR codes. The preview reads the
  radio's positive `maxChannels` value with an eight-channel fallback, resolves semantic
  name/PSK duplicates, removes blank placeholders, selects only channels that fit, and keeps
  overflow visible unchecked. Radio channel/config writes are awaited sequentially and the cache
  is replaced only after successful admission.
- Added synthetic-secret tests for a 587-character dense Meshtastic-format QR in a padded
  1280-by-960 camera plane, single-result delivery, eight-channel `add=true` URL round-trip, and
  one-existing-plus-eight-incoming capacity behavior. No supplied PSK or raw QR payload was added
  to source, tests, logs, or this handover.
- With JDK 21, Android SDK, initialized proto submodule, and en-US JVM locale, targeted barcode
  tests passed in both flavors and `:core:model:allTests :core:ui:allTests` passed. The required
  `spotlessApply spotlessCheck assembleDebug test allTests` and `kmpSmokeCompile
  :app:lintFdroidDebug :app:lintGoogleDebug` gates also passed.
- Changed-module Detekt passes. Current root Detekt remains red for seven pre-existing findings in
  unmodified modules: desktop BLE pairing (3), Gateway identity model (1), BLE radio transport
  (1), and Gateway repository data code (2). This QR repair does not broaden into those unrelated
  refactors.
- Rebuilt the Google arm64 Debug target and passed its no-cloud-runtime guard. The exact APK is
  51,948,716 bytes, package `com.ntsocial.meshlink.google.debug`, `versionCode=2`,
  `versionName=1.0.1`, and SHA-256
  `76B8F876CC4C2327B3C3E2274C0ECC09D06EA217C9154F204D385BA9D35368E6`.
- Installed that APK with `adb install -r -t` on SM-S9080, two SM-S9280 phones, and OPPO CPH2695.
  All four remained in ADB `device` state, retained their 2026-07-28 first-install timestamps, and
  returned an installed `base.apk` SHA-256 identical to the local artifact. This is preserved-data
  install and artifact-integrity evidence only; the Apps were not launched and no QR/radio action
  was performed in this install turn.
- Track impact: camera acquisition is Android-only. The capacity-aware shared dialog and
  sequential application path compile for and improve Windows imports without changing Windows
  branding, packaging, or IPC. Physical camera-to-screen scanning of the supplied QR and
  connected-radio application remain required release evidence; automated image tests are not
  claimed as device proof.

## 2026-07-29 - Gateway native channel-text source implementation
- Added additive v2 `SEND_CHANNEL_TEXT` with `text`, the `1L shl 4` native-send capability, and a
  strict nonblank 180-byte UTF-8 limit. The command uses the existing verified caller,
  single-use capability, source/route/generation binding, and durable `client_message_id` ledger.
- Added a Gateway repository admission path which revalidates the route against the current
  channel identity, resolves a stable local node ID, constructs only broadcast
  `TEXT_MESSAGE_APP`, captures the existing stable source-message identity, writes a normal
  MeshLink channel-history row, awaits the platform `MessageQueue`, and then permits the Receiver
  to commit ACCEPTED. Caller-supplied destinations, ports, packet IDs, and serialized packets are
  not accepted.
- Serialized native-text route revalidation, existing-row matching, insertion, and queue admission
  with a dedicated repository mutex; concurrent retries of one packet/client ID therefore persist
  one normal MeshLink chat row while retaining safe queued-work re-admission.
- Advanced Room 42 to 43 with nullable `origin_client_message_id` on `Packet`; it is exported as
  optional `/v2/message-changes` own-echo correlation metadata and never enters `DataPacket` or
  an upstream protobuf. The generated `43.json` schema is present.
- Provider status now advertises native send when a configured channel and stable local node ID
  are ready; v2 capability bits and every catalog row advertise native send support. Added
  focused UTF-8/node-resolution, durable insert/queue/retry, Room/DAO origin round-trip, Provider
  projection, parser/fingerprint, and contract tests.
- Serial validation from a clean output state passed 361 focused
  model/data/database/service tests. `spotlessApply spotlessCheck`, `assembleDebug`,
  `kmpSmokeCompile`, and both Debug flavor lint tasks passed. Root `test allTests` initially exposed
  18 hardcoded-English Compose assertions under the host's zh-TW locale; the required en-US rerun
  completed successfully. Root `detekt` remains red only for three pre-existing findings in the
  unmodified desktop BLE pairing service. An additional `assembleRelease` attempt is independently
  blocked by the unmodified Widget module's missing `colorControlNormal` and
  `widget_local_stats_preview` release resources.
- The resulting 50,783,283-byte Google arm64 Debug APK has SHA-256
  `94F4477B3D3BB0AD63B0EF229FA78549885363DBC69FE315D67C4677BAD5857B`. It and parent NTsocial Debug
  were installed with `adb install -r` on four Android 16 arm64 phones. All four read back matching
  installed hashes, retained prior first-install timestamps/data, launched both apps, retained live
  processes, and produced no matching recent FATAL/ANR. Every database opened after installation
  migrated from schema 42 to 43 and contains `origin_client_message_id`; two dormant databases for
  previously selected radios remain at 42 and will migrate when opened.
- The operator explicitly required Meshtastic connection tests to be skipped because this
  environment currently has no Meshtastic node. Therefore the current artifact has no connected
  radio command-admission, RF transmission/reception, or remote receipt evidence; older field
  evidence below must not be promoted to the current binary.

## 2026-07-28 - Gateway event permission and four-phone LoRa field validation
- Added the missing `uses-permission` for
  `com.ntsocial.meshlink.permission.ACCESS_NTSOCIAL_GATEWAY`. NTsocial registers its dynamic
  Gateway EVENT receiver with that sender permission, so defining the permission without requesting
  it caused MeshLink envelope/status broadcasts to be silently rejected.
- Rebuilt and installed the Google debug ARM64 APK without clearing app data on the two
  radio-bound phones. Its SHA-256 is
  `C48DD5B89E1FB6960ECE4A666CA31728612BB94BF008E7D8D95A9C2EAFE28F1A`; both installed
  packages report the permission requested and granted.
- Hardware evidence covered both RF directions with the parent app stopped on the receiving side:
  MeshLink received the LoRa frames, the remote parent decoded the matching social message ID, and
  ordinary NTsocial history sync then delivered the row to the remote unbound phone. All four phones
  showed both direction markers in channel `F44571CB-E21E-523F-BA13-126DA61EFB27`.
- A separate parent-app cold-start race was found and corrected in `NTsocial_release`: the service
  now installs its incoming packet collector before starting this Gateway, and the UI no longer
  starts the Gateway first. A cached three-fragment LoRa message committed 5.8 seconds after a cold
  parent launch with no manual wakeup broadcast.
- The changed MeshLink manifest passes `git diff --check`; the Google debug build/install and field
  run passed. The full multi-variant AGENTS validation was not rerun. Permission/event delivery is
  not by itself RF proof; the RF claim above relies on the separately captured sender `to_radio`,
  receiver raw-frame, parent decode, and canonical-store evidence.

## 2026-07-26 - PSK-derived Gateway identity and stable-only history correction
- Encrypted Meshtastic channels now derive `source_channel_id` from a domain-separated SHA-256 digest
  of the resolved PSK only. Name, slot, role, numeric channel ID, and install state are excluded;
  shorthand PSKs 1 through 10 converge with their expanded full keys.
- `security_class` now follows the resolved PSK: empty is `CLEAR`, the ten expanded built-in keys are
  `WELL_KNOWN`, and every other non-empty key is `CUSTOM`. Raw PSKs remain unexported.
- CLEAR compatibility is retained byte-for-byte: nonzero IDs keep the v2 unsigned numeric-ID domain,
  and zero-ID CLEAR channels keep the v2 canonical resolved-name plus empty-key domain.
- Gateway v2 message history now exports only rows that captured stable identity at insertion, and
  its status high-water uses that same stable-only predicate. Nullable upgraded rows remain in Room
  for local compatibility but are never recomputed from a reused current slot, backfilled, or deleted.
- Rows that already captured an older identity remain in their original partition; no alias or
  rewrite is introduced. The obsolete install-local HMAC providers and Android/Desktop DI seams are
  removed; any old private preference is inert.
- Final low-parallel `spotlessApply spotlessCheck assembleDebug test allTests` passed in 8m33s;
  `kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug` passed in 1m56s, and changed-module
  Spotless/Detekt passed. Root Detekt still reports only the three documented pre-existing findings in
  the unmodified JVM desktop BLE pairing service.

## 2026-07-26 - Gateway v2 publication-state correction
- Gateway v2/channel unification is no longer an uncommitted worktree: commit
  `5fc0ae898b83bf0dace8dbec2162e23d1f923534` is present on `origin/main`.
- Updated `AGENTS.md` to bind its current implementation and validation claims to that published
  commit. Any older same-day handover wording that calls this work uncommitted is superseded.

## 2026-07-26 - Gateway v2 and parent channel-unification contracts reconciled
- Re-audited the current uncommitted Gateway v2 worktree at base `ee6059281` together with the parent
  `NTsocial_release` channel-unification implementation. Updated both repositories' `AGENTS.md` files
  so automatic per-catalog projections and manual many-to-one parent bindings remain distinct.
- Recorded the exact v2 Provider, Room 42 insertion-cursor, route-token, capability, stable identity,
  WorkManager/ledger admission, provisioning, platform-key, and acceptance semantics. Gateway v1
  remains immutable; native Meshtastic text send and Windows Gateway IPC remain unimplemented.
- Corrected an important live-state limitation: whole-history clear transactionally writes a new
  Room epoch, but the active publisher currently retains its captured old epoch while sequence resets
  to zero until the history flow is resubscribed. Do not claim live clear/reset correctness yet.
- Replaced stale full-green guidance with the current low-parallel 2,417-test result and the three
  pre-existing root-Detekt findings in `JvmDesktopBluetoothPairingService.kt`. No Gradle task was
  rerun for this documentation-only reconciliation; both repository diffs pass `git diff --check`.

## 2026-07-26 - NTsocial cross-signer Gateway IPC and four-device FB27 field validation
- Expanded the Gateway trust boundary so MeshLink release/debug builds accept only the pinned NTsocial release
  (`com.ntsocial.android`, signer `29EF...646`) and team-debug (`com.ntsocial.android.debug`, signer `C67E...D61`)
  identities. Provider/command Receiver authorization is enforced in-process by package ownership, UID, and signing
  certificate; manifest component permission gates no longer prevent intended debug/release cross-pairing.
- Fixed Android 14+ command delivery with sender identity sharing in the parent app. In MeshLink, the trusted broadcast
  caller is now captured synchronously during `onReceive()` and passed into async work; reading
  `getSentFromUid()`/`getSentFromPackage()` after `onReceive()` had returned produced a false `sender_untrusted`.
- Added payload-free `ntsocial_gateway_tx` stages from Receiver authorization through `CommandSender`, packet queue,
  and `to_radio`. Device evidence showed `received -> authorization accepted -> dispatch -> enqueue -> dequeue ->
  to_radio`, followed by an actual BLE write to each Meshtastic node. No message text, PSK, capability token, or raw
  payload is logged.
- Fixed `MeshService` startup so a persisted selected device gets a bounded 15-second preference-load grace instead of
  losing started-service ownership. Both S24 MeshLink processes remained connected to their existing nodes while the
  parent NTsocial app was foregrounded.
- Installed the same final Google debug ARM64 APK on SM-S9080, two SM-S9280 devices, and OPPO CPH2695. Installed
  `base.apk` SHA-256 was identical on all four:
  `3C6B1330BFD22D65F0200B2D60B6A31C370208F1150407AFB0F6EA96D72C6E58`.
- Both S24 devices retained their dedicated Meshtastic BLE nodes. All four phones joined NTsocial channel
  `F44571CB-E21E-523F-BA13-126DA61EFB27`; a four-device canonical-history observation converged within 940 ms of the
  sender's canonical append.
- Validation passed for `spotlessApply`, `:core:data:allTests`, `:core:service:testAndroidHostTest`,
  `:app:assembleGoogleDebug`, and `:app:verifyGoogleDebugNoCloudRuntimeComponents`; the final sender-capture change was
  followed by a successful `spotlessApply :app:assembleGoogleDebug --parallel`. The broader combined baseline still
  has unrelated existing detekt debt in `JvmDesktopBluetoothPairingService.kt` and `BleRadioTransport.kt`.
- Scope boundary: Gateway IPC and radio TX/ToRadio are hardware-verified, but the Ping run did not produce an NTsocial
  `viaLoRa=true` ingress. Do not claim pure LoRa receiver E2E from these artifacts. The full field report is
  `C:\Users\cth\Documents\GitHub\NTsocial_release\docs\meshlink_interop_field_report_2026-07-26.md`.

## 2026-07-23 - NTsocial butterfly foreground-service notification branding
- Replaced the remaining Meshtastic mountain notification small-icon resource with a dedicated 24dp NTsocial
  butterfly vector. The simplified segmented-wing silhouette is derived from the established NTsocial butterfly
  visual language and remains legible as an Android monochrome notification mask.
- Renamed the resource from `meshtastic_ic_notification` to `ntsocial_ic_notification` and updated the main mesh
  service foreground notification, the expedited keep-alive worker fallback, notification summaries, and the generic
  notification manager. No source reference to the retired mountain icon remains.
- Added the official `#67EA94` NTsocial green as the shared Android notification accent and channel light color. The
  notification drawer may show this accent, while Android/OEM status bars still system-tint small icons (normally
  white); the top-bar glyph shape is now the NTsocial butterfly regardless of that platform-controlled tint.
- Added Robolectric regressions for the posted notification icon/color and every canonical notification channel's
  light color. Targeted `spotlessApply :core:service:testAndroidHostTest` passed.
- Full validation passed with JDK 21, the initialized proto submodule, Android SDK, and English test locale:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue --no-configuration-cache` (`BUILD
  SUCCESSFUL`, 7m7s, 1,589 actionable tasks) and `kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug
  --continue --no-configuration-cache` (`BUILD SUCCESSFUL`, 2m39s, 920 actionable tasks). No device/OEM status-bar
  smoke test was performed.

## 2026-07-19 - Google Release configuration-cache signing build fix
- Replaced the remaining `doLast` implementation of
  `verifyGoogleReleaseNoCloudRuntimeDependencies` with the typed build-logic task
  `VerifyNoCloudRuntimeDependenciesTask`. The resolved forbidden-coordinate list is now a declared
  task input, so the task action no longer captures Gradle script objects that configuration cache
  cannot serialize.
- Confirmed `:app:verifyGoogleReleaseNoCloudRuntimeDependencies --configuration-cache
  --configuration-cache-problems=fail` both stores and reuses a configuration-cache entry. Confirmed
  `:app:bundleGoogleRelease` also stores and reuses its entry without configuration-cache problems;
  its `signGoogleReleaseBundle` task ran successfully.
- Full validation passed with JDK 21, Android SDK, and English test locale:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile
  :app:lintFdroidDebug :app:lintGoogleDebug --continue --no-configuration-cache` (BUILD SUCCESSFUL,
  16m20s, 1,895 actionable tasks).
- This CLI workspace intentionally has no `keystore.properties`, so its local AAB is unsigned when
  run without Android Studio's injected signing inputs. Do not treat that artifact as upload-ready;
  retry Android Studio's Generate Signed Bundle flow with the user's selected upload key after sync.

## 2026-07-19 - Play PNG icon and feature graphic
- Added the Play-ready PNGs under the valid no-density Android resource directory
  `app/src/main/res/drawable-nodpi/`: `ntsocial_meshlink_play_icon_512.png` (512 × 512 RGB PNG, 70,109 bytes)
  and `ntsocial_meshlink_play_feature_graphic_1024x500.png` (1024 × 500 RGB PNG, 509,855 bytes).
- The icon preserves the established NTsocial butterfly silhouette in green on black. The feature graphic identifies
  `NTsocial MeshLink` first and `LiberaNt` second, with an original green optical-fiber/radio-network background;
  it contains no map, real location, personal data, third-party logo, or unimplemented feature claim.
- Used the current project green launcher foreground as the exact butterfly source and an ImageGen-created original
  background; both final artifacts are 24-bit PNGs. `:app:verifyGoogleReleaseNoCloudRuntimeDependencies
  :app:bundleGoogleRelease --no-configuration-cache` passed with JDK 21 (BUILD SUCCESSFUL, 2m44s).
- A connected authorized `SM-S9280` phone and matching Google arm64 debug APK are available for authentic store
  screenshots, but screenshots were intentionally not generated or captured in this task because they must show
  truthful current UI and device installation/interaction requires user confirmation.

## 2026-07-19 - Explicit manual Android version configuration
- Replaced the normal local Git-derived version defaults with the two explicit, user-editable root settings
  `VERSION_CODE=1` and `VERSION_NAME=1.0.0` in `config.properties`. Android Studio and Desktop builds now resolve
  those values by default; injected Gradle/CI and environment overrides retain higher precedence for automation.
- Removed the flavor-specific version-name rewrite, so the Google release manifest now preserves the exact configured
  user-visible name instead of appending `(<code>) google`. Updated the Maven publishing fallback and release process
  documentation/workflow references from the retired `VERSION_NAME_BASE` key to `VERSION_NAME`.
- Kept `VERSION_CODE_OFFSET` only as a documented legacy fallback for builds that intentionally omit
  `VERSION_CODE`; existing release CI continues to inject its calculated values explicitly and is not changed.
- Validation passed with JDK 21, Android SDK, and English test locale: full baseline
  `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue --no-configuration-cache` (18m38s,
  1,589 actionable tasks); `kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug --continue
  --no-configuration-cache` (1m20s, 920 tasks); and
  `:app:verifyGoogleReleaseNoCloudRuntimeDependencies :app:bundleGoogleRelease --no-configuration-cache`
  (1m58s, 753 tasks). Bundletool validation passed and the generated `googleRelease` AAB manifest reports
  `versionCode=1` and `versionName=1.0.0`.

## 2026-07-19 - Google Play stewardship and open-source copy audit
- Audited and revised all ten `docs/google-play/` submission drafts after the LiberaNt-first repository copyright
  migration. Every document now consistently identifies LiberaNt LLC as publisher and LiberaNt LLC / the NTsocial
  team as the lead developer, integrator, and ongoing maintainer of NTsocial MeshLink.
- Reworked the Traditional Chinese store listing so its first product identity is the LiberaNt-developed open-source
  companion for Android NTsocial, followed by Meshtastic radio compatibility. The full description now names the
  protected Gateway, cross-App trust, envelope/channel boundary, KMP maintenance, and release work as major
  LiberaNt/NTsocial contributions.
- Added a canonical public-identity and provenance statement to the Play work-package README, App-content/reviewer
  notes, launch plan, release checklist, privacy policy, terms, and community guidelines. It claims LiberaNt
  copyright only in NTsocial original work and copyrightable modifications, preserves GPL-3.0-or-later freedoms,
  retains upstream/contributor rights, and disclaims official Meshtastic/MeshCore sponsorship or endorsement.
- Kept all existing Production blockers intact: location FGS/minimum-scope work, first-send terms acceptance,
  in-App UGC reporting and effective block/ignore, policy hosting and App URLs, final signing, signer pairing,
  cloud-free device testing, and Console/account requirements remain incomplete until separately verified.
- Verified Play field sizes: title 17/30 characters, short description 62/80, full description 1,526/4,000, and
  release notes 166 characters. All 22 local Markdown links resolve; code fences are balanced; `git diff --check`
  passes. With JDK 21 and the initialized proto submodule, `spotlessCheck detekt --continue
  --no-configuration-cache` passed in 1m18s (168 actionable tasks).

## 2026-07-19 - LiberaNt-first copyright and attribution audit
- Audited the fork history, the adjacent NTsocial parent repository, the MeshCore integration commits, and 2,090
  tracked files. The repository now states prominently that LiberaNt LLC leads NTsocial MeshLink development and
  maintenance and that MeshLink is the open-source companion/gateway for the NTsocial Android App.
- Replaced the blanket Meshtastic-only Spotless/Detekt header with a dual-attribution GPL header: LiberaNt LLC for
  NTsocial original work and modifications, and Meshtastic LLC only for Meshtastic-derived portions where present.
  Migrated 1,323 files to explicit LiberaNt notices; 1,325 files carry GPL SPDX and Meshtastic-derived notices.
- Added `NOTICE.md`, `THIRD_PARTY_NOTICES.md`, and `docs/copyright-and-attribution.md`; rewrote the main governance
  documents and documented the pinned MeshCore, meshcore.js, and meshcore_py sources. Exact MIT license texts for
  the two vendored/client-derived MeshCore libraries are preserved in the third-party notices.
- Deliberately did not copy the parent App's proprietary `All Rights Reserved` terms into this GPL repository.
  Contributor copyright remains with contributors unless separately assigned, while project stewardship and
  release authority are documented as LiberaNt-led. Root `LICENSE`, wrappers, and `core/proto` were not modified.
- Validation passed: `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue
  --no-configuration-cache` completed successfully in 17m (1,589 actionable tasks), and `kmpSmokeCompile
  :app:lintFdroidDebug :app:lintGoogleDebug --continue --no-configuration-cache` completed successfully in 2m09s
  (920 actionable tasks). Final scans found no source-template `$YEAR`, no missing AIDL notices, no corrupt docs,
  no broken local Markdown links, no `core/proto` diff, and a clean `git diff --check` result.

## 2026-07-19 - Android Studio Kotlin compiler crash fixed and interrupted caches recovered
- Diagnosed the red `:core:repository:compileKotlinJvm` failure as a Kotlin 2.3.21 compiler-internal concurrency
  crash, not an application source error: FIR metadata serialization and asynchronous JVM code generation threw
  `ArrayIndexOutOfBoundsException` while every Kotlin JVM task was forced to use one backend thread per CPU core.
- Removed the global `-Xbackend-threads=0` advanced compiler argument from `KotlinAndroid.kt`. Gradle module/task
  parallelism remains enabled, while Kotlin JVM compilation now uses its stable default single backend thread.
- The abnormal computer interruption left two Gradle 9.5 transform locks with an invalid protocol byte. Moved only
  their exact transform workspaces and lock files to the recoverable quarantine
  `C:\Users\cth\.gradle\cache-quarantine-20260719-0658`; Gradle also isolated one corrupt local build-cache entry and
  rebuilt it. No project source or user data was deleted.
- Full baseline `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue
  --no-configuration-cache` passed in 3m32s (1,589 actionable tasks). KMP/flavor validation `kmpSmokeCompile
  :app:lintFdroidDebug :app:lintGoogleDebug --continue --no-configuration-cache` passed in 1m50s (920 actionable
  tasks).
- Reopened Android Studio 2026.1.2, completed project sync, and ran Build Project from the IDE. Its Build Output shows
  `:app:assembleGoogleDebug` and `BUILD SUCCESSFUL in 2m 38s` (449 actionable tasks); only non-blocking yellow warnings
  remain.

## 2026-07-18 - Android Studio configuration-cache manifest guard fixed
- Replaced the untyped `doLast` manifest verification closure with the typed build-logic task
  `VerifyNoCloudRuntimeComponentsTask`. Its variant name, forbidden manifest entries, and merged manifest are now
  declared Gradle inputs, so no task action captures the AGP variant object.
- No application manifest content or forbidden-component policy changed. The existing fdroid/google finalizer wiring
  still runs the same cloud-runtime manifest checks after assembly and bundle tasks.
- `:app:assembleGoogleDebug --configuration-cache --configuration-cache-problems=fail` stored a configuration-cache
  entry and an exact rerun reused it successfully. This verifies the Android Studio failure's two serialization
  problems are fixed without disabling configuration cache.
- The expanded baseline `spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile :app:lintFdroidDebug
  :app:lintGoogleDebug --continue --no-configuration-cache` passed in 15m29s (1,829 actionable tasks).
- Release-path verification `:app:verifyGoogleReleaseNoCloudRuntimeDependencies :app:bundleGoogleRelease
  --no-configuration-cache` passed in 2m39s (753 actionable tasks), including
  `verifyGoogleReleaseNoCloudRuntimeComponents`. This is artifact validation only; signing/Play-readiness status is
  unchanged.

## 2026-07-18 - Android Studio configuration-cache build failure diagnosed
- The Android Studio Build Output failure at 20:13 was not a Kotlin/Java compilation error. The generated Gradle 9.5
  configuration-cache report identified two serialization problems on
  `:app:verifyGoogleDebugNoCloudRuntimeComponents`.
- `app/build.gradle.kts` registers that verification task inside `androidComponents.onVariants` and its task action
  directly captures the AGP `variant` object via `variant.name`. Configuration-cache storage therefore traverses the
  Android variant graph and encounters an unsupported `JavaCompile` task plus a `DefaultLegacyConfiguration` (the
  latter incidentally attempts `fdroidDebugCompileClasspath` resolution).
- `gradle.properties` enables configuration cache globally. The immediate workaround is to run the desired Gradle task
  with `--no-configuration-cache`, as used by the current passing validation baseline. A durable repair should make the
  verification task configuration-cache-safe by snapshotting primitive/provider inputs or using a typed task instead
  of capturing the AGP variant object. No production source or Gradle fix was changed during this diagnosis.
# This is a dated, append-only handover log. Add new entries at the TOP.
# Do NOT edit or remove previous entries — stale state claims cause agent confusion.
# Format: ## YYYY-MM-DD — <summary>

## 2026-07-18 — AGENTS guide reconciled with the cloud-free code and Play submission state
- Audited the root `AGENTS.md` against the current source, artifacts, Google Play document packet, and prior validation
  evidence. Corrected three-phone/parent interoperability wording so the pre-cloud-removal device run is treated as
  regression evidence only; the current cloud-free/no-map artifact still needs its own device and Internal-track smoke.
- Split Play build success from submission readiness. The unsigned AAB proves R8/Lint/guard/packaging only, while
  location FGS/API-37 behavior, UGC terms/report/block safeguards, stale localized `analytics_notice` text, final policy
  URLs, upload signing/Play signer trust, current assets/Console declarations, and account/track testing remain gates.
- Added durable architecture rules for cloud-runtime exclusion, conservative Play Data safety disclosure, location FGS,
  UGC, store metadata/policy drafts, and the no-op `PlatformAnalytics` compatibility seam. Clarified that the `google`
  flavor's Meshtastic project API is not a Google service and that F-Droid intentionally uses bundled JSON fallback.
- Recorded the active upstream-mountain widget icon and unused legacy splash vector as branding debt, renamed the native
  rule to 16 KB page compatibility, and expanded the required KMP/flavor/Play release verification commands.
- Synchronized `.github/copilot-instructions.md` as required by `AGENTS.md`. Structural tag checks, referenced-path
  checks, stale-phrase scans, and `git diff --check` passed; no Gradle run was needed for this documentation-only task.

## 2026-07-18 — Google Play submission docs rebaselined for the cloud-free first release
- Rewrote the complete `docs/google-play/` submission packet around the current cloud-free Android runtime. The manual
  first-upload path no longer asks for GCP, Cloud billing, Maps, Firebase, Crashlytics, Datadog, ML Kit, or a Play
  service-account key. It still truthfully requires a verified Play developer account, upload signing, Play App Signing,
  Console declarations, store assets, Internal-track installation, and any account-specific testing gate.
- Replaced stale store copy and policy drafts with Traditional Chinese-first copy for the current no-map, local-ZXing,
  no-telemetry build. Legacy Fastlane metadata and screenshots are explicitly barred from the first upload because they
  still describe maps, analytics, or the official Meshtastic app.
- Corrected the Data safety baseline: no publisher analytics/crash backend does not mean `Collect = No`. Mesh messages,
  names/IDs, optional location, and other user-directed transports leave the Android device, so the draft conservatively
  uses `Collect = Yes`, `Encryption = No`, optional/App functionality types, and requires path-by-path evidence before
  relying on a `Shared = No` exception.
- Documented two source-confirmed Production blockers rather than hiding them in Console prose: the service currently
  adds the location FGS type whenever location permission exists, and the app has not yet proven first-send terms
  acceptance plus in-app UGC reporting/blocking safeguards. Target SDK 37 also requires a deliberate Android 17
  minimum-scope/location-button decision before submission.
- Final documentation audit also found unused localized `analytics_notice` resources that still describe the removed
  Firebase/Crashlytics/Datadog flow. They are not referenced by Kotlin and do not restore those SDKs, but must be removed
  or rewritten before the final AAB so packaged policy text cannot contradict the cloud-free release.
- Synchronized `BUILD_LOGIC_CONVENTIONS_GUIDE.md`, `kmp-status.md`, and `roadmap.md` with the removed map module,
  cloud-runtime guards, unsigned AAB status, and remaining Play delivery gates. Local Markdown links, code fences,
  listing field lengths, and `git diff --check` were validated; no Gradle build was needed for this documentation-only
  follow-up.

## 2026-07-18 — Cloud runtime and rendered maps removed; unsigned Google release pipeline passes
- Implemented the user's decentralized first-release boundary across both Android flavors: removed Google Cloud/Maps,
  Google Play services location, Firebase/Crashlytics, Datadog, ML Kit, osmdroid/GeoPackage, their Gradle plugins,
  credentials/configuration, mapping uploads, CI secrets, manifests, and runtime dependencies. `googleRelease` remains
  only as the existing Play publishing task name.
- Deleted the rendered-map feature, map tab/routes/deep links, inline maps, traceroute maps, map providers/preferences,
  Android Auto map metadata, and map-only controls. Node coordinates, distance, compass, position logs/CSV, Android
  platform location, Meshtastic GPS forwarding, and the user-controlled Meshtastic MQTT/radio location preference remain.
- Removed analytics/crash-report onboarding and Settings UI, analytics preferences/install ID, toggle use case, and
  platform implementations. The retained platform analytics boundary is an explicit no-op for upstream-compatible call
  sites and never records, stores, or transmits events.
- Replaced both flavor-specific ML Kit scanners with one shared on-device ZXing analyzer. ZXing's historical Java
  namespace is `com.google.zxing`, but it is the local open-source decoder and does not use a Google service or network.
- Added release dependency and merged-manifest guards for both flavors. They reject Google Play services, Firebase,
  Maps, ML Kit, Datadog, data transport, advertising/AdServices, and cloud runtime components if reintroduced.
- Updated Google Play docs, privacy/data-safety drafts, CI/release workflows, Fastlane, public project docs, and agent
  guidance to reflect the cloud-free runtime. Manual first upload does not need a Play service-account key; Play itself,
  Play App Signing, and Android Vitals remain Google-controlled distribution infrastructure.
- Fixed an existing App Bundle blocker: APK ABI splits now disable automatically for every `bundle*` invocation while
  remaining enabled for APK/F-Droid tasks. `:app:bundleGoogleRelease` then passed R8, Lint Vital, cloud guards, and AAB
  packaging (`BUILD SUCCESSFUL` in 2m23s; 706 actionable tasks).
- The generated `app/build/outputs/bundle/googleRelease/app-google-release.aab` is 25,533,958 bytes with SHA-256
  `39B2D41A07F5BBB687D3070B0BC200FACF8FEC7E5F4591627AF3A8F0DD03C511`, but `jarsigner` confirms it is unsigned
  because no upload keystore is configured. It is a release-pipeline validation artifact, not Play-uploadable.
- Full validation passed with JDK 21: `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue
  --no-configuration-cache` in 4m41s (1,569 actionable tasks), followed by `kmpSmokeCompile :app:lintFdroidDebug
  :app:lintGoogleDebug --continue --no-configuration-cache` in 1m18s (932 actionable tasks).
- Final artifact audit found zero forbidden cloud/runtime strings across the merged Google release manifest and all
  three AAB DEX files. Both current arm64 debug APKs pass `zipalign -c -P 16`; the Google arm64 APK's five ELF libraries
  all have 0x4000-aligned `PT_LOAD` segments.
- Remaining Play gates are a real upload keystore, Play App Signing and final signer synchronization with the NTsocial
  parent trust rules, a current cloud-free artifact device smoke test, store assets and Console policy declarations,
  Internal-track testing, and any account-specific closed-testing requirement. No GCP billing, Maps key,
  `google-services.json`, Firebase project, Datadog token, or ML Kit setup is required.

## 2026-07-17 - GitHub Desktop stale index-lock recovery
- GitHub Desktop could not commit because `.git/index.lock` was a zero-byte stale file left from 21:49 local time.
  There were no active merge, rebase, cherry-pick, revert, bisect, or sequencer operations and no unrelated Git process;
  the lock was moved recoverably to the Windows temp directory before any index write.
- A fresh `git fetch origin --prune` confirmed `main` and `origin/main` had zero divergence before publication. All 26
  working-tree files belong to the completed Google Play launch-document and advertising-stack removal scope; they were
  staged explicitly, with zero unstaged files and a passing `git diff --cached --check`.
- The LF-to-CRLF notices shown by GitHub Desktop are informational checkout-conversion warnings, not the commit failure.
  GitHub CLI is not installed, so this direct `main` synchronization uses ordinary Git and the existing GitHub Desktop /
  Git Credential Manager authentication rather than a CLI-created pull request.

## 2026-07-17 - Advertising stack removed and Play document pack centralized
- Centralized all nine Google Play launch documents under `docs/google-play/`; the repository root no longer contains
  separate privacy, terms, or community-guidelines copies. Local Markdown links were checked with zero broken targets.
- Removed the direct Firebase Analytics dependency, catalog alias, Analytics consent/event calls, and Analytics-specific
  manifest metadata. This also removes Play Services Measurement, Ads Identifier, Privacy Sandbox Ads, AppMeasurement,
  AdServices permissions, and Install Referrer permission from the Google release graph and merged manifest.
- Retained Firebase Crashlytics and Datadog as optional, user-controlled diagnostics only. General custom events now use
  the existing Datadog RUM pipeline; the privacy notice and Play Data safety/Advertising ID answers were synchronized.
- Added a Google-variant merged-manifest verification task that fails assemble/bundle if advertising identifiers,
  AdServices, Install Referrer, `android.ext.adservices`, or AppMeasurement reappear.
- The complete JDK 21 baseline passed twice. The final clean run included `spotlessApply spotlessCheck detekt
  assembleDebug test allTests kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug
  :app:processGoogleReleaseMainManifest :app:verifyGoogleReleaseNoAdvertisingComponents --continue
  --no-configuration-cache` (`BUILD SUCCESSFUL` in 1m11s; 1,962 actionable tasks). A fresh Google release runtime
  dependency scan and merged-manifest scan both reported zero forbidden matches; `git diff --check` also passed.
- No production AAB was built, signed, uploaded, or submitted. A real upload keystore, authorized production
  Crashlytics/Datadog configuration, final Play App Signing trust synchronization, and App Bundle Explorer verification
  remain required; nothing was staged, committed, or pushed.

## 2026-07-17 - Google Play launch document pack and policy audit
- Created a Traditional Chinese Google Play submission pack under `docs/google-play/`, including public-facing
  `PRIVACY_POLICY.md`, `TERMS_OF_USE.md`, and `COMMUNITY_GUIDELINES.md`. Updated the zh-TW Fastlane listing,
  release notes, support links, and the base/zh-TW in-app analytics and privacy strings. The parent App's stale,
  proprietary privacy/EULA text was not copied because it conflicts with MeshLink's actual SDKs, behavior, and GPL-3.0
  status. `LiberaNt LLC` and `huangct_2025@liber-ant.com` remain candidate publisher/contact values that the Play
  account owner must confirm.
- Audited the actual Google release manifest and runtime behavior. The merged manifest currently contains Advertising
  ID and AdServices permissions through Play Services Measurement, and the connected-device service declares both
  connected-device and location foreground-service types. Location FGS activation and the current prominent disclosure
  do not yet match the desired store declaration. These are submission blockers, not documentation-only concerns.
- Play release remains blocked on a real upload keystore and authorized production Google/Firebase/DataDog settings;
  the current release path can fall back to debug signing. Play App Signing's final signer must also be synchronized
  with MeshLink/parent package-and-certificate trust before production interoperability can be claimed.
- The App packages roughly 40 Compose locale directories and 39 Fastlane locale directories, so it is not currently
  zh-TW-only. Non-English/non-zh-TW analytics notices remain stale upstream text. For the first listing, either audit
  every advertised locale or package/list only the verified languages. Release builds do not expose Demo Mode outside
  Firebase Test Lab. Store assets, UGC terms acceptance/reporting, location disclosure implementation, and any applicable
  new-personal-account closed-testing eligibility remain open gates.
- Validation ran the expanded local baseline with JDK 21 and completed Spotless, Detekt, debug assembly, KMP smoke
  compilation, and both F-Droid/Google lint tasks. The combined command failed only on one nondeterministic
  `ScannerViewModelTest.connectionProgressText reflects connectionProgress` occurrence; a forced targeted rerun and a
  subsequent `:feature:connections:allTests` run both passed. Markdown local links, Play field lengths, XML parsing, and
  `git diff --check` were also verified.
- No Play-ready AAB was produced, no production credentials were added, and nothing was pushed, committed, or submitted
  to Play Console. All documentation and metadata changes remain in the working tree for user review.

## 2026-07-17 - Three-phone stabilization, parent interoperability, and final local acceptance
- Repaired Android 16 KB native-page compatibility by pinning `mil.nga.geopackage:geopackage-android` 6.7.5 on the
  F-Droid osmdroid dependency path. The arm64 debug APK passes `zipalign -c -P 16`; all packaged arm64 ELF load
  segments were audited at 0x4000 alignment. Do not remove the override without repeating both archive and ELF audits.
- Repaired debug parent pairing: a debuggable MeshLink host now accepts only `com.ntsocial.android.debug` sharing a
  signer digest with the host, while release trust remains pinned. Removed the stale machine-specific debug signer
  resource and added focused verifier tests. Unauthorized shell Provider access remains rejected.
- Repaired two parent-App edge cases in the adjacent `NTsocial_release` worktree: attachment transfer now treats
  `UNAVAILABLE` and `CONFLICT` receipts as terminal, and legacy sync Bloom filters use JVM-safe Base64 helpers. The
  parent `:app:testDebugUnitTest :app:assembleDebug` validation passed with 477 unit tests.
- Replaced the registration-only `NavigationAssemblyTest` Robolectric/Compose Activity launch with direct Navigation 3
  provider construction. Five forced targeted rounds passed (1/1 each, roughly 1.7-2.4s), eliminating the prior
  one-minute `UncompletedCoroutinesError` cleanup flake.
- Final MeshLink validation passed:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile :app:lintFdroidDebug
  :app:lintGoogleDebug --continue --no-configuration-cache` (`BUILD SUCCESSFUL` in 10m13s; 1,951 actionable tasks).
  The final XML report set contains 2,439 tests, zero failures, and zero errors.
- Fixed MeshLink and parent debug APKs were clean-installed on three Android 16/API 36 arm64 phones: Samsung SM-S9080
  (`R5CT30QMRTY`), Samsung SM-S9280 (`R5CWC4KNTRL`), and OPPO CPH2695 (`TWBYJJRWSGHIGU55`). Onboarding and all primary
  MeshLink destinations opened; parent status correctly reported MeshLink preparing the radio channel; explicit launch,
  repeated foreground/background switching, English-keyboard text entry, and parent-local/BLE cross-phone sync passed.
  Relevant logs had zero crash, ANR, fatal, Provider trust, or app-process failure signals.
- No Meshtastic/MeshCore node was available in this run. Therefore no radio connection, LoRa transmission, RF reception,
  remote receipt, or MeshCore transport was tested or implied. Prior connected-radio queue acceptance still proves only
  the local queue/Gateway boundary. Play upload also remains blocked on a real upload keystore and authorized production
  Google/Firebase/DataDog configuration; debug device success is not a Play-ready AAB claim.
- The main and parent worktrees intentionally remain uncommitted for user review. Root `AGENTS.md` and the Copilot quick
  reference were synchronized with these current build, packaging, interop, testing, and release boundaries.

## 2026-07-17 - MeshCore UI aligned with Android NTsocial visual language
- Used the local `NTsocial_release` Android implementation as the visual source of truth, specifically its theme,
  shell/header, people list, private chat, message bubble, and composer components. The non-dynamic MeshLink palette
  already exactly matched NTsocial indigo `#4F46E5`, emerald `#10B981`, amber `#F59E0B`, and the corresponding surfaces.
- Added feature-local `MeshCoreNtsocialVisualTheme` primitives with NTsocial's exact monospace typography metrics,
  standard Material shapes, elevated header treatment, uppercase section labels, 16x10 list rows, and 28dp identity
  icons. The boundary intentionally preserves the host theme and Dynamic Color contract instead of changing global or
  Meshtastic UI behavior.
- Reworked the independent MeshCore home, directory, radio/settings, and conversation screens to match NTsocial's
  compact information hierarchy. Conversations now use 78%-width asymmetric 16dp/4dp bubbles, NTsocial semantic
  container colors, aligned metadata, and the same compact composer structure while remaining truthfully read-only.
- Added `MeshCoreNtsocialVisualsTest` to lock the typography contract, updated English/Traditional Chinese pending-
  transport wording, regenerated the string index, and documented the visual boundary in `docs/meshcore-integration.md`.
- Targeted MeshCore formatting, Detekt, JVM/Android/Desktop compile and tests passed. The first full parallel baseline
  encountered one unrelated Desktop notification-test timeout; its isolated retry passed, and the complete baseline
  then passed on retry: `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile --continue
  --no-configuration-cache` (`BUILD SUCCESSFUL in 1m 5s`, 1,632 actionable tasks).
- The Desktop host launched successfully for a visual run, but Windows UI automation could not capture the window
  because `GetCursorPos` returned access denied. No screenshot-based visual claim was made. MeshCore transport remains
  pending, and no Meshtastic radio/service/database/settings internals or Gateway v1 contract were changed.

## 2026-07-17 - Independent MeshCore UI and Companion protocol foundation
- Added isolated `core/meshcore` and `feature/meshcore` KMP modules. The former owns MeshCore-only domain models and a
  bounds-checked Companion Radio Protocol codec; the latter owns a StateFlow store, Koin ViewModel, dedicated messages,
  contacts/channels, radio/settings panels, and conversation screen. No Meshtastic radio/service/database/settings
  implementation or Gateway v1 contract was changed.
- Added a sixth MeshCore top-level destination, a serializable Navigation 3 graph/conversation route, `/meshcore` deep
  link, Android/Desktop graph assembly, Koin wiring, icons, and English/Traditional Chinese resources.
- Official reference snapshot used on 2026-07-17: `meshcore-dev/MeshCore`
  `219812b9f136744c3478908e9487afd0d6031b53` (source identifies Companion v1.16.0), `meshcore-dev/meshcore.js`
  `bbe1f9301b801cbd48a053687f16eea9634634cd`, and `meshcore-dev/meshcore_py`
  `5bac3573b51c4298062881885b6d15a994109076`. The codec covers the official Nordic UART UUIDs, app target protocol 3,
  176-byte frames, contacts/channels, device/self/radio state, message sync, direct/channel text and v3 signal metadata.
- Added protocol codec tests, state-store tests, route serialization/parity, deep-link, Android/Desktop assembly, and
  top-level parity coverage. The full local baseline passed with JBR 21, Android SDK and English locale:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile --continue --no-configuration-cache`
  (`BUILD SUCCESSFUL in 18m 9s`, 1,632 actionable tasks).
- Truthful status: this phase establishes a real protocol/UI boundary but does not yet implement MeshCore BLE/USB/TCP
  transport, scanning/connection lifecycle, settings writes, message sending, persistence, background sync, parent-app
  IPC, or two-radio RF validation. UI text and `docs/meshcore-integration.md` explicitly identify transport as pending.

## 2026-07-15 - Build and release status synchronized into agent guidance
- Updated root `AGENTS.md` with the verified G1GC/JBR compatibility rule, the successful 2026-07-15 full baseline and
  Google debug packaging evidence, and the explicit boundary between local compile success and Play release readiness.
- Recorded that no Play-uploadable AAB is validated or Git-tracked, that the Google release trial reached R8 before
  dummy Firebase configuration caused production mapping upload rejection, and that the official workflow still needs
  an authorized upload keystore plus production Google/Firebase/DataDog configuration.
- Synchronized the same day-to-day build and release guidance into `.github/copilot-instructions.md` as required by the
  repository documentation-sync contract. No source code, signing material, production credentials, or artifacts changed.

## 2026-07-15 - Android Studio Gradle Sync repair and compile verification
- Fixed Android Studio's `Unable to start the daemon process` failure by replacing unsupported
  `-XX:+UseZGC -XX:+ZGenerational` with `-XX:+UseG1GC` in root `gradle.properties`. The configured Android Studio
  JBR is JDK 21 but does not include ZGC; `gradlew help --stacktrace` subsequently completed successfully.
- Re-ran the required local baseline after the abnormal shutdown with JDK 21, the local Android SDK, and English locale:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue --no-configuration-cache`. It completed
  successfully in 3m42s with 1,533 actionable tasks; Gradle discarded and rebuilt the few interrupted local caches.
- Confirmed a Google universal debug APK at
  `app/build/outputs/apk/google/debug/app-google-universal-debug.apk` (83,759,469 bytes). This proves compilation,
  packaging, static checks, and tests work locally; it is not a Google Play release artifact.
- A Google release AAB trial reached release compilation/R8 but local mapping upload failed because production
  Firebase/release credentials are absent. Per the user's revised scope, the retry was explicitly stopped and no AAB
  was copied into Git or prepared for cloud backup. Temporary AAB-specific build-script/Fastlane changes were reverted.
- Final intended worktree change is only the G1GC compatibility fix plus this mandatory handover entry. A production
  Play AAB still requires the project's real upload keystore and production service configuration.

## 2026-07-10 - Manual per-channel LoRa rule and AGENTS synchronization
- Updated both repository `AGENTS.md` files to record the implemented Provider/capability/explicit-command/event
  architecture, port-256-only outbound policy, MeshLink radio ownership, variant pairing, privacy boundary, and the
  2026-07-10 hardware E2E evidence. Older parent AIDL notes are explicitly historical rather than current guidance.
- User-confirmed product rule: there is no automatic channel binding. A normal NTsocial channel may use LoRa only after
  the user manually enables and binds it in that channel UI; an unbound channel remains BLE-only. Removed the parent
  `MeshtasticGatewayManager` refresh-time logic that wrote the canonical lane into every joined channel and removed the
  now-invalid contract helper/tests.
- Added `ChatViewModelMeshtasticTest.sendPost_withUnmappedChannel_keepsLoRaOutOfTransport`. Target validation passed:
  `:app:assembleDebug`, `MeshLinkGatewayContractTest` (4 tests), and the new unbound-channel test. The parent project
  has no Spotless task; an attempted `:app:spotlessCheck` therefore did not execute compilation or tests.
- The updated parent debug APK was built, but the USB device disconnected before `adb install -r`; no device data or
  installed APK was changed in this follow-up. Reconnect the same device before installing the updated APK if a fresh
  on-device check is desired.

## 2026-07-10 - Cross-app Android LoRa hardware E2E acceptance
- Retested both installed debug applications over the connected Android 16 device without clearing data or changing the
  user's radio configuration. The user-created normal channel `#NTsocialLORA` was already LoRa-enabled and bound; the
  parent channel UI showed `LoRa enabled` before the send.
- A 20-second idle baseline for the parent and MeshLink processes had zero error-level log lines. The outbound test was
  entered through the normal channel `MessageInput` and submitted with its actual accessible Send control (not the
  Meshtastic test/feed UI). The input cleared and one committed local message appeared in that channel.
- Parent evidence in the send window: one normal-channel LoRa route preparation, port 256 only, three explicit protected
  Gateway command broadcasts, one accepted dispatch, and one accepted asynchronous lane completion. No legacy AIDL call
  or port 497 outbound path was used.
- MeshLink evidence in the same window: three radio-transport writes, eight observed queue-status events, and three
  `queueJob ... success true` firmware responses. The parent and MeshLink windows both had zero error-level logs and no
  reject, timeout, disconnected-radio, inactive-transport, or write-failure signal.
- This proves normal NTsocial channel -> protected Provider/capability -> explicit COMMAND broadcast -> MeshLink ->
  connected radio firmware queue delivery. It does not by itself prove RF reception at a remote peer; that requires a
  second receiving radio or an on-air receiver/return message.

## 2026-07-10 - MeshLink ContentProvider / Intent Gateway boundary (pending parent full validation)
- Added the Android-only cross-app Gateway boundary: `${applicationId}.gateway` Provider paths `/v1/status`,
  `/v1/envelopes`, `/v1/nodes`, and `/v1/channels`; an explicit `com.ntsocial.meshlink.gateway.COMMAND` receiver;
  and explicit, metadata-only `com.ntsocial.meshlink.gateway.EVENT` broadcasts. Events invoke `ContentResolver.notifyChange`
  and never include message bytes. `NtsocialGatewayEventPublisher` starts after Koin in `MeshUtilApplication`.
- Provider status includes connection, cache count/limit, ports and limits, canonical default-channel readiness/index,
  provisioning outcome, local node ID, and sanitized gateway health. Nodes omit positions, keys, notes, and raw protobufs;
  channels expose only index/name/uplink/downlink, never PSKs or RF config. Envelope bytes remain Provider-only.
- Security: ACCESS/CONTROL `signature|knownSigner` permissions and release/debug signer resources are declared. The
  Provider pins caller UID/package/certificate to `com.ntsocial.android`; debug is accepted only for a debuggable
  MeshLink host and `com.ntsocial.android.debug`. Package visibility queries were added. Android 8-13 commands use a
  short-lived, single-use Provider-issued capability bound to `request_id`; Android 14+ also verifies sender UID/package/
  certificate directly.
- Added raw envelope outbound path: validates an existing `NM` envelope, sends it unchanged only on PRIVATE_APP/256,
  limits complete external envelopes to 180 bytes, caches it as outbound, and rejects a noncanonical channel index.
  Legacy 497 remains inbound-only. Cache uses a Mutex; event dedupe state is bounded to current cache keys.
- Preserved AIDL Gateway service as deprecated compatibility and fixed `AndroidRadioControllerImpl` to use
  `context.packageName`, not a hard-coded package.
- Focused tests added for raw envelope behavior, capability one-time/request/UID/expiry behavior, and Provider contract
  constants. Targeted commands passed before final manifest/event-only polish:
  `:core:service:compileAndroidMain :app:processFdroidDebugMainManifest`,
  `:core:service:testAndroidHostTest --tests NtsocialGatewayCommandCapabilityStoreTest`, and
  `:core:data:jvmTest --tests NtsocialGatewayRepositoryImplTest` (8 tests, zero failures in XML).
- A broad `spotlessApply` attempt found one max-line-length issue in the new capability test; it was fixed afterward.
  An overlapping earlier Gradle invocation also produced a Kotlin compiler internal AIOOBE, so the parent should run the
  normal single baseline after this handoff rather than treat that as a source failure.

## 2026-07-10 - Physical BLE/logcat diagnostic (no fix applied yet)
- Real-device debug of the F-Droid Debug app on Android 16 found no `FATAL EXCEPTION` or app crash, but reproduced a live BLE failure: the UI shows `Connection timeout — no data received` after the service liveness guard observes 67,128 ms without a received radio packet (threshold 60,000 ms).
- The observed connection timeline had successful BLE profile setup and Stage 1/Stage 2 handshakes, an earlier genuine remote BLE disconnect/reconnect, then a later liveness timeout. After the timeout, no new BLE connection attempt/retry appeared for over three minutes.
- Root cause is an application bug: `SharedRadioInterfaceService.checkLiveness()` calls `onDisconnect(isPermanent = false)`, which only changes the connection state to `DeviceSleep`; it does not close/rebuild `radioTransport`. `BleRadioTransport` only runs its reconnect loop when it observes a real GATT disconnect or exception. Result: a zombie transport can leave the app visibly disconnected without recovery. The app-level `MeshConnectionManagerImpl` maps the state to `Disconnected` when power saving is off but also does not restart the transport.
- Prior to the timeout, current-connection UI RSSI reads were timing out repeatedly. `CurrentlyConnectedInfo` polls GATT RSSI every two seconds with a one-second timeout, so a stale link produces a timeout/log cycle roughly every three seconds instead of becoming a reconnect signal. A heartbeat may also be sent while BLE `toRadio` is unavailable during reconnect; this creates avoidable warning noise.
- Performance evidence: while the Connections screen has user-enabled BLE scan active, the indeterminate `LinearProgressIndicator` drives ~120 fps foreground rendering. Android 16 logs `View.setRequestedFrameRate(frameRate=NaN)` twice per frame, filling logcat (~9,904 entries in 41.2 seconds). Frame pacing itself was healthy (0.88% jank, 7 ms median); the concern is battery/log-buffer overhead, not a demonstrated UI stall. Stable background rendering stopped (0 frames over a 10-second settled background interval).
- Privacy defect: do not reproduce/expose device data, but current debug Logcat contains a full position emitted by `CommandSenderImpl.sendPosition()` and other raw protobuf/position log paths. This violates the project privacy rule and needs removal/sanitisation. Temporary device/local screenshots used for diagnosis were deleted; no PII was retained in this handover.
- Recommended fix sequence (requires user authorization to implement): add an explicit transient transport-restart path for liveness failure under the transport mutex; gate heartbeats until a transport is actually connected/ready; add regression tests for liveness-restart and repeated RSSI timeouts; remove/sanitize precise-location and raw-packet logging; then retest reconnect on this phone.

## 2026-07-10 - Physical Android deployment verified
- User connected an ADB-authorized Samsung `SM_S9280` (`R5CWC4KNTRL`) over USB. Confirmed its ABI is `arm64-v8a`.
- Built `:app:assembleFdroidDebug` successfully with JDK 21 and the local Android SDK. The build emits ABI-split APKs; installed `app/build/outputs/apk/fdroid/debug/app-fdroid-arm64-v8a-debug.apk` (56.37 MiB) through `adb install -r -t`.
- Install succeeded. The F-Droid debug application ID is `com.ntsocial.meshlink.fdroid.debug` (not the production base ID); verified its package path, running PID, `MainActivity` window, and `topResumedActivity`. The app is currently launched in the foreground on the device.
- Deployment used the F-Droid flavor intentionally because it is the directly testable open-source build and does not rely on production Google Maps/DataDog credentials. Do not silently uninstall it when updating; use `adb install -r -t` and preserve app data unless the user explicitly requests removal.

## 2026-07-10 - Windows Robolectric SQLite host-runtime repair
- Fixed the reproducible Windows-only `UnsatisfiedLinkError: no sqliteJni in java.library.path` in Android Robolectric tests. This was a missing host-JVM native SQLite runtime, not a Windows desktop-app target or a production Android SQLite defect.
- Added the upstream-compatible `androidx.sqlite:sqlite-bundled-jvm:2.6.2` runtime dependency to the app's unit-test classpath and `core:database` Android-host-test runtime classpath. It supplies the host-native `sqliteJni` loader, including `sqliteJni.dll` on Windows.
- Removed the Windows skip guards from `PacketFtsSearchTest` and `MigrationTest`; those tests now execute on Windows rather than hiding the native-load issue. No production source or protobufs changed.
- Verified with JDK 21, Android SDK, English locale, and no configuration cache: targeted `:app:testFdroidDebugUnitTest :app:testGoogleDebugUnitTest :core:database:testAndroidHostTest` passed. XML confirms NavigationAssemblyTest passes in both flavors, four migration tests and three FTS tests execute with `skipped=0`, `failures=0`, and `errors=0`.
- Full required baseline also passed: `spotlessCheck detekt assembleDebug test allTests -Pci=true --continue --no-configuration-cache` (`BUILD SUCCESSFUL`, 1,469 actionable tasks). Existing non-failing KMP host-test/Skiko version warnings remain unrelated to SQLite. Android instrumentation/LoRa hardware acceptance is still a separate emulator/device setup and is not replaced by Robolectric.

## 2026-07-10 - Completion audit and full local verification report
- User requested a current-completion audit and actual full local test of the NTsocial MeshLink fork. Read-only audit found `main` at `cf52c9fd`, 12 fork-only commits and 701 commits behind `upstream/main` `a3aa9769` (2.8.0 line), while `config.properties` still says `2.7.14`.
- Local CI-equivalent static matrix passed using JDK 21, Android SDK, English locale: `spotlessCheck`, `detekt`, app F-Droid/Google lint, barcode F-Droid/Google lint, API lint, and `kmpSmokeCompile` (`BUILD SUCCESSFUL in 4m 47s`).
- Full CI test task matrix was run with all 19 core KMP, 8 feature KMP, and 7 app/desktop/Android task paths. It finished in `12m 46s` with an expected non-zero result: `:app:testFdroidDebugUnitTest` and `:app:testGoogleDebugUnitTest` each fail `NavigationAssemblyTest.verifyNavigationGraphsAssembleWithoutCrashing` after the configured two retries. Test XML records 2,221 executions, 6 retry-attempt failures, 0 errors, 7 skipped. Root cause is reproducible Windows/Robolectric `UnsatisfiedLinkError: no sqliteJni in java.library.path`, triggered when navigation/Koin initialization runs the Room FTS backfill through `BundledSQLiteDriver`; it is not a navigation assertion failure. Four migration and three Android-host FTS tests are intentionally skipped on Windows for the same native SQLite limitation.
- Packaging passed: `:app:assembleFdroidDebug`, `:app:assembleGoogleDebug`, and `:desktop:createDistributable` all succeeded (`BUILD SUCCESSFUL in 36s`). Worktree remained clean (no product-source changes).
- Audit conclusion: official modern Meshtastic parity is roughly 55% (+/-5%), NTsocial Gateway goal roughly 30-35%, combined "complete official app + production NTsocial bridge" roughly 45-50%. Existing 2.8 slices include FTS, Device Links, air quality, XEdDSA, protected Gateway IPC/MVP port 256 + receive-only 497, and default NTsocial channel provisioning. Major missing work includes discovery, docs, Android Auto, AI App Functions, translation/geofence/lockdown/TINY preset paths, persistent/reliable Gateway delivery, scheduler/policy/dashboard, cross-app E2E, and hardware acceptance.
- CI risks discovered: public main has no observable Actions runs/check contexts or branch protection; F-Droid reproducible-build signing-block check in `.github/workflows/reusable-check.yml` has quoted-heredoc and inverted-status logic; tracked Google-map test sources have no matching Gradle test task; API/widget tasks are `NO-SOURCE`; no device/hardware acceptance coverage.

## 2026-07-06 - Meshtastic 2.8 parity stop report: database slice + interrupted baseline
- User requested stopping work and reporting at this point. Do not mark the overall 2.8 parity goal complete.
- Current branch is `codex/meshtastic-2-8-parity`. `gradle.properties` was restored to the original
  `UseZGC + ZGenerational` JVM args after temporary local Gradle/JBR G1GC runs.
- Added official 2.8.0 Android database reliability parity:
  `MeshtasticDatabase.configureCommon(multiConnection)` now supports upstream-style multi vs single
  connection pools, and Android `DatabaseBuilder` uses `BundledSQLiteDriver` with a single connection
  pool, matching official 2.8.0 behavior while preserving NTsocial database package/schema.
- Adjusted FTS tests for Windows/local reliability after bundled SQLite native loading failed in
  Robolectric host tests. `PacketFtsSearchTest` now has JVM coverage that actually runs all three FTS
  cases; Android host FTS/migration tests use `BundledSQLiteDriver` and skip on Windows where
  `sqliteJni` cannot load.
- Targeted validation passed:
  `spotlessApply :core:database:jvmTest :core:database:testAndroidHostTest --no-configuration-cache`.
  JVM FTS tests passed; Android host FTS/migration tests were skipped on Windows by design.
- Full baseline was started:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests --no-configuration-cache`.
  It was interrupted per user request. Before interruption, many core/app/feature tests passed,
  including NTsocial Gateway, NTsocial channel provisioning, device links, model/navigation/network,
  and database targeted paths. One known failing item appeared before interruption:
  `:app:testFdroidDebugUnitTest` `NavigationAssemblyTest > verifyNavigationGraphsAssembleWithoutCrashing`
  failed with `UnsatisfiedLinkError`. Because the run was stopped, normal XML/HTML test reports were
  not fully written; only in-progress binary test-result files remained.
- No Gradle/Java processes from this workspace were left running after cleanup.

## 2026-07-06 - Meshtastic 2.8 parity slice: XEdDSA + air-quality persistence
- Completed the next targeted parity slice on `codex/meshtastic-2-8-parity` while preserving NTsocial Gateway/private-app
  behavior and branding. Prior slices in this worktree already include message search, device links, and air-quality UI/logs.
- Added the missing persistence path for air quality telemetry: `NodeEntity` now has `air_quality_metrics` with Room
  default `x''`, `NodeRepositoryImpl` writes it, `NodeManagerImpl.handleReceivedTelemetry()` updates it, and Room schema
  `41.json` includes it.
- Added XEdDSA signing support:
  `DataPacket.xeddsaSigned`, `Message.xeddsaSigned`, `MeshDataMapper` propagation from `MeshPacket.xeddsa_signed`,
  `Node.signsPackets`, `NodeEntity.has_xeddsa_signed`, `NodeManagerImpl.installNodeInfo()` from
  `NodeInfo.has_xeddsa_signed`, signed message badge, signed node status icon/dialog, node details signed row, and resources.
- Proto note: bumped `core/proto/src/main/proto` submodule only to protobufs commit
  `108919393a2a3fdf6ab82e50e10965e74394620f` (`Add initial protobufs for XEdDSA (#753)`). Do not use protobuf
  `origin/master` blindly here: it introduced later config changes that broke local traffic-management UI fields.
- Tests/validation passed with temporary local `gradle.properties` ZGC->G1GC switch restored afterward:
  `:core:database:kspKotlinJvm`, targeted `:core:model:jvmTest :core:database:jvmTest :core:data:jvmTest
  :core:navigation:jvmTest :feature:node:jvmTest :feature:messaging:jvmTest :feature:settings:jvmTest`,
  `spotlessApply`, `spotlessCheck detekt`, a final `:feature:node:jvmTest`, and `git diff --check`.
- Known validation warning only: existing Skiko version mismatch warning in feature node Compose checks and existing
  deprecation/no-cast warnings; they did not fail validation.

## 2026-07-06 - Official Meshtastic feature completeness audit
- Audited local `main` against freshly fetched official `meshtastic/Meshtastic-Android` `upstream/main`.
  Local HEAD is `c77785a023ad0d9ee7ae66d33f0cbf8bbdd6207a`; official latest is
  `4d07bc6641335cbeaa1d279b755b9e3cd11fccaf` from 2026-07-06; merge-base is
  `c0d95d6ac4196fcbc705f2d3f174c7d9c46a77b2`. Local is 623 commits behind and 10
  NTsocial commits ahead.
- Conclusion: NTsocial MeshLink does **not** currently implement all latest official Meshtastic Android
  features. Local `VERSION_NAME_BASE=2.7.14`; official `main` is `2.8.0`.
- Confirmed missing/latest-upstream feature modules: official has `feature:discovery`, `feature:docs`,
  `feature:car`, `core:konsist`, `baselineprofile`, `screenshot-tests`, and `docs-screenshots`; local
  lacks those modules. Official README highlights full-text message search, mesh network discovery,
  Android Auto, air-quality telemetry, device hardware links, and App Functions/system-AI integration.
- Concrete gaps found in local code: no FTS5 `PacketFts`/`searchMessages`/`MessageSearchBar`; no
  `feature/discovery`; no in-app docs browser/Chirpy docs assistant; no Car App Library
  `CarAppService`/templates (only older notification metadata); no App Functions or `AiFunctionProvider`;
  no `device_links.json`/msh.to link directory; air-quality support is request/config-only and lacks
  official PM/CO2 persistence, node cards, chart/log, and CSV export path.
- Additional official changes still unmerged include message translation, Mesh Beacon offers, waypoint
  geofences, NFC tag writing, firmware lockdown, XEdDSA signing UI, LoRa region-preset/TINY preset
  support, stale cache fixes, crash/fuzz hardening, and the removal of upstream AIDL/service architecture.
- Existing local specs corroborate unfinished work: `001-local-mesh-discovery` has 50 unchecked tasks,
  `002-node-list-layout` has 38 unchecked tasks, and `003-app-docs-markdown` has 90 unchecked tasks.
  These specs are marked Not Started.
- Important merge risk: NTsocial-specific code to preserve includes protected Gateway IPC, private-app
  transport/cache, default NTsocial channel provisioning, branding/assets, app id/package namespace, and
  the `core:api` transitional IPC surface even though official removed upstream AIDL.

## 2026-07-05 - NTsocial_release visual alignment pass
- Imported visual assets from `C:\Users\USER\Desktop\GitHub\NTsocial_release`: butterfly intro/wordmark,
  dark background art, flag images, butterfly logo, Android launcher mipmaps, and play-store icon.
- Added NTsocial intro visual helpers in `feature:intro`: animated butterfly splash, shared background
  scaffold, dark scrim, white copy, blue CTA pills, and reused the background across welcome,
  permission, and critical-alert intro screens.
- Updated visible shell touchpoints: app bar fallback branding now uses the NTsocial butterfly logo,
  the no-device connections card uses NTsocial background art, connection action buttons use rounder
  NTsocial-style shapes, and Android splash uses the imported launcher foreground on black.
- Stabilized unrelated flaky validation blockers encountered during full baseline:
  `DesktopNotificationManagerTest` now waits on fallback flow with coroutine timeouts, and
  `MessageViewModelTest` waits for returned `Job`s from IO-backed `safeLaunch` calls.
- Verification passed with `ANDROID_HOME=C:\Users\USER\AppData\Local\Android\Sdk`,
  `JAVA_HOME=C:\Users\USER\.jdks\openjdk-21`, and
  `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"`:
  `.\gradlew.bat spotlessApply spotlessCheck detekt assembleDebug test allTests`.

## 2026-06-29 - Upstream firmware/Android divergence audit
- Confirmed this workspace is an Android/KMP app fork, not a direct `meshtastic/firmware` checkout.
  `upstream` points to `meshtastic/Meshtastic-Android`; firmware must be compared separately.
- Local `main` / `origin/main` is `638c04d64`; official Android `upstream/main` is `e634e71ea`;
  merge-base is `c0d95d6ac`. Local fork is 8 commits ahead and 543 Android upstream commits behind.
- Local app base is `VERSION_NAME_BASE=2.7.14`; official Android upstream main is `2.8.0`.
  Local bundled firmware metadata has stable `v2.7.15.567b8ea` and alpha up to
  `v2.7.22.96dd647`; upstream Android metadata has alpha up to `v2.7.26.54e0d8d`.
- Local protobuf submodule `core/proto/src/main/proto` is `v2.7.23-7-g1d6f1a7`; official
  protobufs has `v2.7.26`. The delta is 38 protobuf commits, including newer hardware models,
  Lockdown auth/status messages, TAK/ATAK schema changes, ITU region split, and KMP publishing.

## 2026-05-09 - AGENTS channel provisioning rule sync
- Updated `AGENTS.md` architecture boundaries to make built-in NTsocial channel provisioning a
  durable project rule: bundle the canonical public NTsocial channel, auto-register it after node DB
  readiness without confirmation, preserve primary when possible, replace the last secondary when
  full, and apply QR LoRa/RF config only on effectively unconfigured radios.
- Synchronized `.github/copilot-instructions.md` with the same quick-reference guidance.
- No Gradle validation was run for this docs-only change; `git diff --check` should remain clean.

## 2026-05-09 - Built-in NTsocial channel provisioning
- Added `NtsocialDefaultChannel` in `core:model` as the canonical built-in public NTsocial
  Meshtastic channel. The decoded channel is `NTsocial`, has a 32-byte PSK, uplink/downlink enabled,
  and includes LoRa config. The decodable QR payload uses `...GPoBIAQoBTg...`; the visually similar
  `...GPoBIASoBTg...` transcription fails protobuf decoding.
- Added `NtsocialChannelProvisioner` in `core:data`. It checks post-handshake channel state, treats
  same name or same PSK as NTsocial, updates non-canonical NTsocial slots, adds a free secondary
  slot, replaces the last secondary when full, and replaces primary only on one-channel radios.
- LoRa/RF config from the QR is only applied when the current local LoRa config is missing or
  `region == UNSET`; configured radios keep their existing region/frequency/preset.
- Wired provisioning from `MeshConnectionManagerImpl.onNodeDbReady()` after owner/session seeding,
  using the existing local admin session refresh flow before `set_channel` / `set_config` writes.
- Added decode and provisioner tests, plus manager wiring coverage. Verification passed:
  `spotlessApply spotlessCheck detekt assembleDebug test allTests --no-configuration-cache` with
  `ANDROID_HOME=C:\Users\USER\AppData\Local\Android\Sdk`,
  `JAVA_HOME=C:\Users\USER\.jdks\openjdk-21`, and English `JAVA_TOOL_OPTIONS`.

## 2026-05-09 - NTsocial protected Gateway IPC MVP
- Added project-owned protected NTsocial Gateway IPC in `core:api`: `INtsocialGatewayService`,
  `INtsocialEnvelopeCallback`, `NtsocialEnvelopeData`, `NtsocialGatewayStatus`, and
  `NtsocialGatewayContract`. The contract exposes `sendNtsocialPayload`, envelope observation,
  cache snapshot, and gateway status without exposing the deprecated `IMeshService` surface.
- Added `NtsocialGatewayService` in `core:service/androidMain`. It is a separate Binder service
  that enforces the NTsocial signature permission for cross-process callers, sends through
  `NtsocialGatewayRepository.sendTestPayload`, streams cached validated envelopes via callbacks,
  and reports connection/cache/port/payload-limit status.
- Declared `com.ntsocial.meshlink.permission.BIND_NTSOCIAL_GATEWAY` as a signature permission and
  exported `com.ntsocial.meshlink.core.service.NtsocialGatewayService` with bind action
  `com.ntsocial.meshlink.gateway.BIND`. Existing `MeshService` / `IMeshService` behavior was left
  unchanged.
- Added Android host contract tests and a fake NTsocial Gateway binder to verify the new IPC
  contract, mapper surface, constants, PRIVATE_APP port 256, legacy receive-only 497, and payload
  size status.
- Verification: `spotlessApply spotlessCheck detekt assembleDebug test allTests --no-configuration-cache`
  passed with `ANDROID_HOME=C:\Users\USER\AppData\Local\Android\Sdk` and
  `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"`.

## 2026-05-09 - NTsocial PRIVATE_APP transport MVP
- Added the first NTsocial Gateway data plane: `NtsocialEnvelopeCodec` validates the MVP
  `NM + version + 16-byte headerMsgId + payload` envelope, caps raw envelope size at 200 bytes,
  and keeps outbound payload capacity at 181 bytes.
- Added `NtsocialGatewayRepository` plus an in-memory cache/dedup implementation. Outbound test
  payloads are sent only on `PRIVATE_APP / port 256`; legacy port `497` is accepted only by the
  inbound cache path.
- Wired `MeshDataHandlerImpl` so incoming private-app-compatible data packets are offered to the
  NTsocial cache while preserving existing generic Meshtastic broadcast behavior.
- Added focused tests for envelope parsing, invalid magic/version rejection, size limits, cache
  deduplication, legacy receive-only handling, outbound port policy, and the MeshDataHandler hook.
- Verification: `spotlessApply spotlessCheck detekt assembleDebug test allTests --no-configuration-cache`
  passed with `ANDROID_HOME=C:\Users\USER\AppData\Local\Android\Sdk` and
  `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"`. One earlier `:desktop:test` run hit
  an existing flaky fallback notification assertion; an immediate rerun passed, and the final full
  baseline passed.

## 2026-05-09 - AGENTS current-state audit
- Rewrote `AGENTS.md` for the current `NTsocial MeshLink` identity: app id
  `com.ntsocial.meshlink`, project package boundary `com.ntsocial.meshlink.*`, preserved
  upstream `org.meshtastic.proto`, NTsocial token skinning, and gateway roadmap status.
- Clarified that gateway/cache/IPC/RF scheduler/node-policy features are planned unless code exists,
  and documented port 256, legacy 497 receive-only, channelId/channelIndex, LoRa media exclusion,
  user-consent `rebroadcast_mode = ALL`, and GPL/open-source boundaries.
- Synchronized `.github/copilot-instructions.md` with the new naming, validation command including
  `spotlessCheck`, debug package IDs, branch guidance, and NTsocial UI/gateway boundaries.

## 2026-05-09 - Rename to NTsocial MeshLink package identity
- Renamed project-owned Android/KMP source packages and paths from `org.meshtastic.*` /
  `com.geeksville.mesh` to `com.ntsocial.meshlink.*`; preserved upstream protocol boundary
  `org.meshtastic.proto`.
- Updated app display labels to `NTsocial MeshLink`, app id to `com.ntsocial.meshlink`,
  Gradle convention plugin ids to `com.ntsocial.meshlink.*`, desktop ids to
  `com.ntsocial.meshlink.desktop`, and Room schema path to the new database package.
- Verification: bootstrap completed with JDK 21 and Android SDK. Full
  `spotlessCheck detekt assembleDebug test allTests` passed with
  `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"`. DataStore-style prefs tests
  now use in-memory test stores to avoid Windows atomic rename flakes.

## 2026-05-09 - NTsocial visual token skinning phase 1
- Updated core UI theme tokens only: `Color.kt`, `CustomColors.kt`, and `Type.kt`.
- Non-Dynamic theme now uses NTsocial indigo/emerald/amber with gray surfaces; Dynamic Color,
  `AppTheme` API, theme picker, prefs, MainActivity, and navigation shell remain unchanged.
- Verification: bootstrap completed with Android SDK and JDK 21; full
  `spotlessApply spotlessCheck detekt assembleDebug test allTests` passed when run with
  `JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"` after clearing the stale Gradle
  problems report. Without English locale, hardcoded English Compose tests fail against zh-rTW
  resources.

## 2026-05-09 - NTsocial Gateway README identity rewrite
- Rewrote root `README.md` only as a Traditional Chinese-first early-fork identity for
  "NTsocial Meshtastic Gateway for Android", with a short English summary.
- Removed upstream download badges/release-channel claims and kept upstream attribution,
  GPL-3.0 license boundary, planned `PRIVATE_APP / port 256`, receive-only legacy `497`,
  user-consent `rebroadcast_mode = ALL` policy, and `channelId` vs `channelIndex` framing.
- Bootstrap note: `local.properties` was initialized from `secrets.defaults.properties`
  and remains git-ignored. Proto submodule update required elevated permissions for `.git/modules`.
- Verification: stale upstream download URL `rg` check passed; `spotlessCheck detekt` passed.
  Full `spotlessApply spotlessCheck detekt assembleDebug test allTests` did not pass because
  `:app:testGoogleDebugUnitTest` hit a Robolectric native ICU runtime failure and Gradle also
  reported an existing problems-report output collision.

## 2026-05-02 — CI cost-control PR review fixes
- Applied PR review feedback: encoding fixes in sort-strings.py, NUL-delimited staged-files loop
  in ai-guardrail.sh, installation instructions added, typo fix in strings.xml, command order
  fixed in AGENTS.md, narrowed .aiexclude/.gitattributes patterns, allTests added to SKILL.md.

## 2026-04-XX — Token Mitigation (Phase 1-3)
- `.copilotignore` and `.aiexclude` updated with stricter ignore rules.
- `AGENTS.md` modularized to ~3KB base; detailed rules moved to `.skills/`.
- `scripts/ai-guardrail.sh` added to prevent binary/log leaks (installation: see script header).
- CI Cost Control skill added at `.skills/ci-cost-control/SKILL.md`.

## 2026-07-10 - Android liveness recovery, RSSI resilience, and device validation
- Restored Windows Robolectric SQLite coverage by adding `sqlite-bundled-jvm` to the relevant app and
  database host-test runtimes, then re-enabled the migration and FTS tests that had been skipped on Windows.
- Fixed the BLE zombie-link path in `SharedRadioInterfaceService`: a >60-second liveness timeout now
  silently closes and recreates the BLE transport under the transport mutex. It is gated by an explicit
  `connectionRequested` flag, protected from overlapping restarts, avoids writing a polite disconnect frame
  into the unresponsive transport, and never raises the user-facing connection-timeout alert.
- Added `SharedRadioInterfaceServiceLivenessTest` coverage for restart/recreate behavior, no permanent
  disconnect or alert, stale/repeated restart suppression, inbound-data reset, non-BLE no-op, and explicit
  disconnect behavior. Added `FakeRadioTransport.closeCount` test support.
- Changed presentation-only RSSI polling from a 2-second/1-second-timeout loop to normal 5-second polling
  with capped 5/10/20/30-second exponential retry backoff. Added `RssiPollingTest`.
- Reduced sensitive Logcat exposure: packet/position/telemetry/store-forward/serial-diagnostic logs now
  retain only safe type/size metadata, HTTP logging is `INFO` instead of `BODY`, and stream-frame logs no
  longer include endpoint-derived tags. Existing MeshLog database retention remains a separate product
  decision and was not silently altered.
- Verification passed in full with JDK 21, Android SDK, and English test locale:
  `./gradlew.bat spotlessCheck detekt assembleDebug test allTests -Pci=true --continue --no-configuration-cache`
  (`BUILD SUCCESSFUL`, 9m31s). Targeted service, connection, data, and network suites also passed.
- Deployed the F-Droid ARM64 debug APK to a USB-connected Android device. A clean launch reached
  `Connected`; ADB-driven message navigation and the visible Send button successfully produced an outbound
  radio-frame write, and the user visually confirmed the sent message. The non-mutating Settings/About page
  also rendered. A 75-second sanitized idle monitor showed no liveness-timeout alert or connection warning.

## 2026-07-10 - Cross-app ContentProvider Gateway integration and validation
- Replaced the parent app's LoRa-side Meshtastic AIDL integration with the MeshLink ContentProvider + explicit
  command/event boundary. The parent package `com.ntsocial.android.debug` targets
  `com.ntsocial.meshlink.fdroid.debug` in debug builds; the production variant targets `com.ntsocial.meshlink`.
  BLE/GATT code was not changed.
- MeshLink now exposes verified `/v1/status`, `/v1/envelopes`, `/v1/nodes`, and `/v1/channels` snapshots. Commands
  require a short-lived, single-use Provider-issued capability on API 26-33 and sender verification on API 34+.
  New outbound traffic is raw `NM` traffic on port 256 only, bounded to 180 bytes and the canonical provisioned
  NTsocial channel; legacy port 497 stays inbound-only.
- Provider data excludes radio configuration, PSKs, positions, notes, and raw node protobufs. Events are explicit and
  metadata-only; the parent re-queries the Provider. Parent readiness automatically binds joined logical channels to
  the canonical RF channel many-to-one.
- Validation passed: MeshLink's required full baseline
  `spotlessApply spotlessCheck detekt assembleDebug test allTests --no-configuration-cache` completed with final exit
  code 0 using a single-worker, reduced-memory local invocation; parent debug assembly plus
  `MeshLinkGatewayContractTest` completed with exit code 0. Both debug APKs were verified as Android Debug signed
  with SHA-256 `B578F8445925AEA570F7E916C335172559773D7B6EC92DB0D76355E0E8F3FF8D` and have the expected packages.
- Device follow-up is still required: the initially connected Android 16 device (`R5CWC4KNTRL`) disappeared from ADB
  before installation, so neither new APK was installed and no device settings/data were changed. Once the device
  reconnects, install both APKs with `adb install -r`, query the Provider as the debug parent, verify unauthorized
  shell denial, launch both apps, and monitor sanitized logs for the cross-app event/re-query path.

## 2026-07-18 - Google Play launch plan documentation
- Saved the complete Traditional Chinese Google Play first-launch plan at
  `docs/google-play/06-first-play-launch-plan-zh-TW.md`.
- This session was documentation-only; no application code, release credentials, Play Console state, or GCP
  resources were changed, and no Gradle validation was required.

## 2026-07-23 - Bluetooth-only first-release connections UI
- Simplified the shared `ConnectionsScreen` presentation to a single Bluetooth device section. Removed the BLE/TCP/USB
  transport filter chips, the TCP/network and USB list sections and empty states, the manual TCP address sheet, and the
  screen-level network auto-scan/permission trigger. The connection status card, BLE scan action, BLE devices, region
  warning, and disconnect/navigation behavior remain.
- Narrowed `bleDevicesForUi`, `BluetoothDeviceList`, and `DeviceListItem` to `DeviceListEntry.Ble`, so USB/TCP icons and
  actions cannot be rendered through this UI. Because the screen is in `commonMain`, the simplified presentation is
  shared by the Android and desktop hosts.
- Preserved all TCP/USB backend code: Android/JVM discovery, `ScannerViewModel` TCP/USB flows and scan/select handlers,
  device models, transports, preferences, and tests remain. Existing connection tests confirmed BLE, TCP, and USB
  backend behavior still passes.
- Validation passed with JDK 21, Android SDK, and English test locale: targeted
  `:feature:connections:allTests` (`BUILD SUCCESSFUL`, 10m35s); required full
  `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue --no-configuration-cache`
  (`BUILD SUCCESSFUL`, 21m9s; 1,589 actionable tasks); and
  `kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug --continue --no-configuration-cache`
  (`BUILD SUCCESSFUL`, 2m32s; 920 actionable tasks). No device UI smoke test was performed in this session.
- Synchronized `AGENTS.md` and `.github/copilot-instructions.md` with the Bluetooth-only first-release Connections UI
  rule, retained USB/TCP backend boundary, current build evidence, and outstanding device-smoke limitation.

## 2026-07-23 - NTsocial MeshLink Windows full branding
- Rebranded only the Windows `:desktop` product as `NTsocial MeshLink`: window/taskbar/tray/default app-bar imagery,
  toast identity, MSI/EXE/start-menu name, `LiberaNt LLC` vendor, `NTsocial` menu group, and stable upgrade UUID
  `6784A2DD-CE59-518B-AA15-C26302D6FA85`. macOS/Linux metadata and icons remain unchanged; application ID remains
  `com.ntsocial.meshlink.desktop`.
- Copied the authorized blue NTsocial ICO and 24/48/512 PNG marks from the read-only adjacent `NTsocial_Windows`
  repository at HEAD `84c6f8c4349eaecff741a09d4e77a7c3e9d04b68`. Exact source paths, introduction commits, and SHA-256 values are
  recorded in `desktop/BRANDING_ASSETS.md`. The reference repository remained clean. The shared butterfly fiber
  background already had an identical SHA-256, so it is reused without duplication.
- Added optional shared color-scheme, typography, and default-brand-painter CompositionLocals while preserving the
  `AppTheme(darkTheme, dynamicColor, content)` API, Material Expressive, Dynamic Color fallback, Android green
  butterfly, and event-edition branding priority. The shared navigation scaffold gained an optional container color
  whose default preserves all existing hosts.
- Added the Windows indigo/emerald/amber translucent palette, Segoe UI Variable/Segoe UI plus Cascadia Mono/Consolas
  resolution, full-window butterfly background, transparent navigation host, and a fixed three-second cold-start
  overlay. Koin, Mesh service, and data initialization still start immediately; process-level state prevents a tray
  re-show from replaying the splash.
- Added desktop tests for Windows/non-Windows identity selection, application/notification IDs, exact splash phase
  boundaries, one-shot cold-launch consumption, and classpath resource availability. Updated the Windows notification
  sender test and rewrote `desktop/README.md` to describe the truthful current architecture and product boundary.
- Validation passed with full JBR JDK 21, Android SDK, and English locale: targeted `:desktop:test` (`BUILD SUCCESSFUL`,
  4m33s); required full `spotlessApply spotlessCheck detekt assembleDebug test allTests --continue
  --no-configuration-cache` (`BUILD SUCCESSFUL`, 11m; 1,589 actionable tasks); and `kmpSmokeCompile
  :app:lintFdroidDebug :app:lintGoogleDebug --continue --no-configuration-cache` (`BUILD SUCCESSFUL`, 5m17s; 920
  actionable tasks).
- Windows release packaging also passed with the complete JetBrains JDK 21 (`BUILD SUCCESSFUL`, 7m42s). It produced
  `NTsocial MeshLink-1.0.0.exe` and `.msi`; MSI properties read back as ProductName `NTsocial MeshLink`, Manufacturer
  `LiberaNt LLC`, and the planned UpgradeCode. The MSI start-menu directory is `NTsocial`, and both shortcuts are named
  `NTsocial MeshLink`. Install/upgrade/coexistence was verified structurally from installer metadata, not by changing
  the machine's installed applications.
- A dark-theme Windows smoke launch rendered the branded title, blue taskbar/app-bar mark, palette, and Bluetooth-only
  Connections screen without crash. Computer Use was stopped by the user before further interaction; 100/150/200%
  scaling, light-theme, tray re-show, and the final post-transparency visual should still receive manual release QA.

## 2026-07-23 - Android and Windows dual-track agent guidance
- Updated `AGENTS.md` so the repository explicitly has two first-class product tracks: Android `NTsocial MeshLink`
  in `app/` and Microsoft Windows `NTsocial MeshLink` in `desktop/`, with shared KMP changes requiring impact review
  against both hosts.
- Added separate product IDs, current status, architecture/integration boundaries, platform brand systems, Windows
  installer identity, automated validation, and manual release-QA requirements. The guide explicitly records that
  Windows branding and packaging are implemented while `NTsocial_Windows` IPC, Windows Service, Authenticator, code
  signing, and parent-App interoperability are not.
- Synchronized `.github/copilot-instructions.md` because the change affects day-to-day scope, naming, build commands,
  and release claims. This follow-up changed guidance only; it did not modify product code or rerun Gradle validation.

## 2026-07-23 - Windows Bluetooth discovery UI smoke test
- Launched the current Windows `NTsocial MeshLink` development application with `:desktop:run` on the user's laptop.
- The Bluetooth-only Connections UI automatically entered its scanning state and displayed the nearby
  `Meshtastic_fe66` node at approximately RSSI -81 dBm. The same node remained visible after a five-second refresh.
- This validates local Windows UI discovery of the nearby BLE advertisement in this environment. The session did not
  select/connect to the device, exchange Meshtastic data, alter radio configuration, or prove reconnect behavior.
- The application was left running on the Connections screen.

## 2026-07-23 - Windows BLE first-pairing blocker diagnosis
- Follow-up device selection failed before the Meshtastic handshake with Windows HRESULT `0x80650005`
  (`E_BLUETOOTH_ATT_INSUFFICIENT_AUTHENTICATION`). The nearby node is healthy and had just been disconnected from the
  user's phone; discovery remained functional.
- Root cause: the JVM `KableBluetoothRepository` always reports unbonded and implements `bond()` as a no-op, while the
  common and JVM Connections paths incorrectly assume Desktop Kable/Windows will pair automatically during GATT
  connection. Kable 0.42.0's JVM btleplug FFI exposes connect/read/write/subscribe but no pairing operation, so the
  app reaches a protected Meshtastic characteristic without triggering Windows pairing/PIN UI.
- The `unnamed-...` regression is separate: scan aggregation replaces the stored device whenever RSSI changes even if
  the newer advertisement has no name; `DeviceListEntry.Ble` then falls back to `unnamed-{address}`. Preserve the last
  non-blank name for an address when merging advertisements.
- No product code was changed in this diagnostic turn. A proper fix needs a Windows-only pairing service/API before
  selecting the radio, actionable authentication error mapping, stable scan-name merging, focused tests, and an
  on-device pairing/PIN/connection validation.

## 2026-07-23 - Windows BLE PairAsync root-cause follow-up
- Microsoft explicitly lists `DeviceInformationPairing.PairAsync` as unsupported in Desktop apps. The current
  fork-only `JvmDesktopBluetoothPairingService` invokes exactly that basic-pairing API from a hidden, non-interactive
  PowerShell desktop process, matching the observed `PAIRING_STATUS=Failed` result and absence of a PIN dialog.
- Upstream `main` still documents that BLE bonding is not supported on Desktop. Its JVM `isBonded()` remains false and
  `bond()` remains a no-op; Kable 0.42.0 in this fork and Kable 0.44.3 upstream expose no JVM pair/bond/PIN API.
  Therefore Windows first-pair/PIN was not an upstream-complete feature that branding broke.
- The fork's PowerShell/WinRT helper, fake tests, and fail-before-GATT integration were introduced together in
  `ce20e086c`; the prior `8ae1bd4e` state retained the upstream no-op behavior. The helper also discards the child
  process exit code and exception detail, has no custom-pairing/PIN UI state machine, and is retried indefinitely even
  though `BlePairingException` is classified as permanent.
- Focused validation passed on JDK 21:
  `:core:ble:jvmTest :core:network:allTests :feature:connections:allTests :desktop:test --no-configuration-cache`.
  These tests use a fake pairing process and no Windows hardware/WinRT ceremony, so they do not validate real
  first-pairing.
- Recommended implementation direction is a Windows-only custom-pairing bridge using
  `DeviceInformation.Pairing.Custom`, `PairingRequested`, and an ephemeral Compose pairing state/PIN flow, followed by
  real Windows Settings/reference-helper and Meshtastic hardware testing. A Windows Settings pre-pair flow is the
  lowest-risk interim workaround. No product code or OS Bluetooth state was changed in this investigation.

## 2026-07-26 - Android Gateway v2 native-channel unification MVP
- Kept Gateway v1 immutable and added protected v2 status, configured-channel catalog, bounded native broadcast-text
  changes, catalog/history wakeups, caller-bound route tokens, and route-aware raw NTsocial overlay commands.
  Capabilities deliberately report `native_text_send=0` and overlay send only; ordinary Meshtastic native-text send was
  not added.
- Captured stable channel/message identity when native text is persisted from live ingress, Store & Forward ingress,
  and MeshLink's own outgoing UI path. Outgoing local authors are resolved to the stable own-node ID and `!local`
  fails closed. Nonzero Meshtastic channel IDs survive rename/reorder. Zero-ID CLEAR/WELL_KNOWN channels derive a
  cross-install public ID from the resolved, locale-independently normalized channel name plus resolved public PSK, so
  empty default names converge with explicit `LongFast`. Zero-ID CUSTOM-PSK channels use an install-local HMAC key and
  intentionally do not converge between installs.
- Advanced Room to schema 42 with nullable captured gateway identity, non-unique lookup indexes, and a metadata table.
  Each active per-radio history database owns a durable random `history_epoch`; full-history clear rotates it
  atomically, and switching/replacing databases changes cursor domain even when the new sequence is lower. Status
  high-water includes stable rows and currently mappable legacy broadcast rows; legacy reads are bounded and read-only.
- Routed v2 command acceptance now persists the deterministic packet to Room, awaits unique WorkManager admission,
  then commits the bounded PENDING/ACCEPTED idempotency ledger before emitting accepted. SharedPreferences failures
  restore prior in-memory state. Acceptance proves local durable admission only, not firmware airtime, RF delivery, or
  remote receipt; overlay packets are not ordinary Meshtastic native-text messages.
- Automatic NTsocial channel provisioning still updates a canonical/same-PSK slot or uses a free secondary slot and
  now fails closed with `NO_SPACE` when all slots are occupied, preserving every user channel.
- Focused validation passed:
  `:core:model:jvmTest :core:repository:jvmTest :core:data:jvmTest :core:database:testAndroidHostTest
  :core:service:testAndroidHostTest :feature:messaging:compileAndroidMain :desktop:compileKotlin
  --no-configuration-cache` (`BUILD SUCCESSFUL`, 1m22s, 346 actionable tasks). This is source/host evidence only; the
  v2 Provider, current Room migration on a retained device database, connected-radio queue, and remote RF reception
  still need current-artifact device testing.

## 2026-08-19 - Meshtastic nRF52 XEdDSA size defect audit and firmware backport
- Confirmed that the published file named `firmware-nrf52_promicro_diy_xtal-2.8.0.7c6b85d.uf2` actually embeds
  `2.8.0.16831c5` (SHA-256 `4A7307...A30D5`), matching the field node metadata. Both the real `7c6b85d` and
  `16831c5` firmware sources retain the old XEdDSA payload-size heuristic and exclude upstream fix `0e84c1a`.
- Documented the deterministic PRIVATE_APP port-256 dead band: 165-byte payload is a signed exact fit, 166-168 bytes
  are signed then rejected as `TOO_LARGE(7)`, and 169+ becomes unsigned and succeeds for the current Data shape.
- Backported the upstream exact encoded-size sender/receiver policy in sibling firmware repo `faketec-RA-01SH-P` on
  local branch `codex/xeddsa-size-gate`, commit `f3fd3d4bf7550c83244a412fc034ca2d891c3a03`, including malformed partial
  signature rejection and PRIVATE_APP 165/166/167/168/169/180 regression coverage.
- Built `nrf52_promicro_diy_xtal` successfully. The unflashed UF2 embeds `2.8.0.f3fd3d4`, is 1,430,016 bytes, and has
  SHA-256 `F4C4352B49874FB314B5E6B473D87B188C51157E29B0B61443D22D3353844BA5`. No hardware was flashed or restarted.
- Added `MESHTASTIC_NRF52_XEDDSA_SIZE_DEFECT_AUDIT_AND_FIX_2026-08-19.md` at repo root. Native unit cases remain
  unexecuted on this Windows host because the native environment lacks `pkg-config`/POSIX shell; the production nRF52
  build succeeded, terminal output reported the warm-region/ISR guards clean, two source reviews found no P0 blocker,
  and `git diff --check` passed. Neither unit behavior nor two-node RF behavior is yet verified.

## 2026-08-30 - iOS USB device and parent-integration validation
- Tested only the iOS track at source HEAD `6a8a665d4781ea60cd91697cc42a0a422210d78a` on a wired iPhone 15 running
  iOS 26.6.1. The existing `NTsocial` parent `com.ntsocial.ios` 1.0.0 (1) and a diagnostic `NTsocial MeshLink`
  `com.ntsocial.meshlink.ios` 1.0.0 (1) were left installed and running.
- A normal MeshLink device build with source entitlements failed because the available local provisioning profile does
  not authorize `group.com.ntsocial.meshlink.gateway`. The installed parent is also a personal-team Developer App;
  its Debug configuration uses the empty `NTSocialAppPersonal.entitlements`, and its profile has no App Group. A
  diagnostic MeshLink build was installed only by command-line `CODE_SIGN_ENTITLEMENTS=` override. This proves boot
  behavior only and must never be cited as Apple Gateway, release-signing, TestFlight, or App Store evidence.
- Root cause is provisioning/capability state, not a reproduced source defect. CoreDevice App Group lookup fails with
  `ContainerLookupErrorDomain error 7`; the existing `AppleGatewayBootstrap` correctly clears Gateway configuration
  and fails closed when the App Group container or shared Keychain key is unavailable. No product code was changed.
- Limited physical checks passed: install/version readback, localized Bluetooth permission-card rendering, graceful
  terminate/relaunch, both parent/companion foreground orders with both processes retained, stable-PID routing of
  `ntsocial-meshlink://process`, post-restart private Room `integrity_check=ok` at schema 43 with retained
  `history_epoch_v2`, and zero MeshLink crash logs.
- The user was away and reported Bluetooth off. A Git-ignored XCUITest harness was attempted; after removing an old,
  rebuildable parent UI-test Runner to satisfy the free-profile three-App limit, two runs timed out while iOS enabled
  UI Automation before any test method ran. The temporary Runner was removed. Bluetooth permission acceptance,
  navigation, BLE scan/connection, Stage 2, connected-radio admission, RF, and remote receipt were not tested.
- Focused baseline passed 126/126: gateway 36, iOS runtime 19, radio-fleet 8, prefs 36, and parent
  `AppleGatewayAdapterTests` 27. Full report:
  `IOS_PARENT_INTEGRATION_USB_PHYSICAL_DEVICE_TEST_REPORT_2026-08-30.md`.
- Follow-up requires eligible-team profiles for both exact bundle IDs carrying the same App Group and shared Keychain
  group, parent built with full entitlements, Bluetooth enabled, and Meshtastic hardware. Only then rerun the mailbox,
  HMAC, Darwin hint/deep-link, missed-hint/restart, connected-radio, and two-radio RF matrix.

## 2026-08-31 - Four-phone clean Debug deployment
- Affected Android and iOS product tracks only; no product source was changed. Built from clean `multi_nodes_` HEAD
  `fe720752c20cef1a3b0ada8f45f932fda8831be5` after the mandatory project bootstrap with JDK 21, the configured
  Android SDK, initialized proto submodule, and en-US JVM settings.
- Rebuilt Google arm64 Debug `1.0.7 (8)` (`com.ntsocial.meshlink.google.debug`). The 52,961,456-byte APK has SHA-256
  `A577632DAF0622A509FB80DCE4CF07AFDBFF83B97D9368A38BB115600791C474`, passed the no-cloud manifest guard,
  Android Debug v2 signature verification, and 16 KiB zipalign.
- Clean-installed that exact APK on the connected Samsung SM-S9280 and OPPO CPH2695, both Android 16/arm64. Both
  installed `base.apk` hashes exactly matched the local artifact, cold launch returned `Status: ok`, the English /
  Traditional Chinese / Japanese selector was present, PIDs remained stable across the follow-up sample, and no
  package exit-info or matching fatal/ANR/Koin startup log was present.
- Built a signed arm64 iOS Debug App (`com.ntsocial.meshlink.ios` `1.0.0 (1)`, `get-task-allow=true`) from the same
  source and clean-installed it on both wired iPhone 15 devices running iOS 26.6.1. Both installs read back as
  Developer Apps, launched after the personal-team developer certificate was trusted on-device, retained stable PIDs,
  and produced no MeshLink crash log.
- The available iOS provisioning profile still omits the required App Group/shared-Keychain capabilities, so this
  deployment used the existing diagnostic `CODE_SIGN_ENTITLEMENTS=` build override. Apple Gateway cross-App behavior
  remains fail closed; this is Debug install/startup integrity only, not Apple Gateway, BLE/Stage-2, RF, release,
  TestFlight, App Store, Play, or remote-receipt evidence.

## 2026-08-31 - iOS simultaneous multi-node production-graph remediation
- Compared current iOS multi-node source against Android remediation commit
  `5b856b5fa468327f7fc9a24eebe5d36dfc86b2da` and confirmed the same Koin architecture defect: secondary iOS scopes
  retained constructor-reference registrations that could not preserve qualified `ProcessLifecycle`/`ServiceScope`
  or Kotlin `Lazy<T>` dependencies. The session also marked itself wired before the activation root resolved.
- Replaced every affected iOS secondary graph definition with an explicit scoped factory, preserving endpoint-local
  lifecycle/scope and dependency-cycle boundaries. Added `IosSecondaryGatewayRepository` so only the legacy-primary
  endpoint can own Apple Gateway, and moved the wired marker after successful graph resolution and buffer reset.
- Added a Kotlin/Native production-graph regression that creates a real secondary endpoint scope, resolves
  `MeshConnectionManagerImpl`, and verifies the fail-closed Gateway binding. Native test execution remains disabled by
  convention, so this regression is compile-only in current gates.
- Focused iOS validation passed 398 tasks (71 executed, 327 up-to-date): runtime Spotless/Detekt/JVM tests, Simulator
  and arm64 compilation, Simulator test compilation, and Debug framework link.
- The full required gate completed 2,018 tasks (186 executed, 1,832 up-to-date). Formatting, both Android Debug
  assemblies, tests/`allTests`, Desktop/JVM, KMP/iOS compilation, and both Android lints passed; exit 1 was solely the
  six existing findings in unmodified BLE (3), domain (1), model (1), and network (1) files.
- A fresh signing-disabled Simulator Debug Xcode host built, installed, and cold-launched on `Codex iPhone 17`
  (`E3249756-57AF-4D9C-AA2B-3332E9309529`) as PID 12071; the follow-up launch returned the same PID. No physical
  concurrent-radio, Stage-2/control, restoration/background, RF/remote-receipt, signing, TestFlight, or App Store
  evidence was produced.

## 2026-09-01 - iOS AppIcon visual-scale remediation
- Affected only the iOS product track. The original 1024 AppIcon was effectively the Android black-background store
  icon enlarged 2x, so it retained adaptive-icon safe padding that iOS does not crop away and looked undersized on
  SpringBoard.
- Rebuilt `iosApp/NTsocialMeshLink/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png` from the established shared
  green butterfly source on black with a centered 960-by-960 foreground canvas. The final asset is 1024-by-1024,
  8-bit RGB, no-alpha, embedded sRGB, SHA-256
  `528F504199FC4730993365992B43ED880D5AD96E688EAE78B75661B891F68B14`; the thresholded visible mark changed from
  548-by-426 to 798-by-614 pixels. Android and Desktop assets were not changed.
- A signing-disabled Simulator Debug Xcode build succeeded, generated the AppIcon catalog without icon warnings,
  installed and launched on `Codex iPhone 17`, and a SpringBoard screenshot confirmed the larger green butterfly is
  centered, uncropped, and visually comparable to the neighboring NTsocial butterfly icon.
- The JDK-21/en-US full/focused gate completed 1,782 tasks (100 executed, 1,682 up-to-date). Formatting, Android Debug
  assembly, tests/`allTests`, KMP smoke compilation, iOS JVM tests, Simulator/arm64 compilation, Simulator test-source
  compilation, and Debug framework link passed. Exit 1 was only the six existing Detekt findings in unmodified BLE
  (3), domain (1), model (1), and network (1) sources. No physical-device, signed archive, TestFlight, or App Store
  evidence was produced.

## 2026-09-01 - Four-phone clean Debug redeployment
- Removed only the exact MeshLink packages from the two connected Android phones and two connected iPhone 15
  devices, verified each package was absent, and then installed current-source Debug artifacts from scratch. This
  intentionally reset MeshLink app-container data; parent NTsocial Apps were not removed.
- Samsung SM-S9280 and OPPO CPH2695 now run `com.ntsocial.meshlink.google.debug` `1.0.8 (9)`. Both installed APK
  hashes exactly match the 51,640,587-byte local arm64 APK SHA-256
  `719589AB776C611A6FF52DF3B333725EFDE8A33B90612E97D242D522B00E5113`. Both cold launches returned `Status: ok`
  and retained their initial PIDs through the follow-up check.
- Both connected iPhone 15 devices now run `com.ntsocial.meshlink.ios` `1.0.0 (1)` from the current arm64 Debug host
  build containing the enlarged green AppIcon. Install, launch, and follow-up process lookup succeeded on both with
  stable PIDs. The build is signed with the existing diagnostic personal-team profile, has `get-task-allow=true`, and
  omits the unavailable App Group entitlement, so Apple Gateway remains fail closed.
- This is four-device clean-install and immediate-startup integrity evidence only. Physical AppIcon appearance, BLE
  permission/scan, concurrent radios, Stage 2, channel mutation, restoration/background, LoRa/RF/remote receipt,
  release signing, TestFlight/App Store, and Play delivery were not tested by this deployment.

## 2026-09-02 - Five-phone NTsocial-to-MeshLink channel-binding validation
- Affected Android and iOS product tracks only. No product source, radio configuration, PSK, pairing, or application
  data was cleared. Three Android NTsocial parents and two iOS NTsocial parents were launched concurrently twice;
  the final synchronized launch completed at 19:40:52 Asia/Taipei with all five commands successful. The two iOS
  parent and MeshLink processes and all three Android parent processes remained present at the final check.
- The connected Android 16 Samsung SM-S9280 ran NTsocial `1.5.8 (38)` and MeshLink `1.0.8 (9)`. MeshLink retained a
  foreground `MeshService` plus two encrypted, open Meshtastic GATT clients. The parent projected the current live
  catalog as two radio endpoints and 13 channels (five on one endpoint and eight on the other).
- Created one disposable private parent channel with an ephemeral random password and used the production LoRa route
  dialog to select exactly one current primary route on each endpoint. Save produced two schema-v3
  `MANUAL_NT_OVERLAY` bindings with distinct endpoint and stable source identities. Reopening the dialog showed
  `2/2`, both selected rows online, and one binding on each endpoint, proving the saved mapping resolved back to the
  current catalog instead of relying on the dialog closing alone.
- Deselected both routes, saved, and left the disposable parent channel. Final parent state had zero joined rows and
  zero bindings for the test channel; the ordinary leave flow intentionally retained one dormant local channel-name
  cache entry (and its private local metadata), with no membership, visible channel, route, or send. No message was
  sent, no Meshtastic channel/config was mutated, and the two MeshLink radio GATT sessions remained open. The parent
  and MeshLink log windows contained no fatal/ANR, invalid-route, channel-mismatch, Gateway-reject, or queue-failure
  event attributable to the test.
- The OPPO CPH2695 and Samsung SM-S9080 Android phones ran the same parent/MeshLink versions but had no current
  MeshLink radio session, so only launch/no-current-route inventory was applicable; no binding was fabricated there.
- Both physical iPhone 15 devices ran iOS 26.6.1 with NTsocial and MeshLink `1.0.0 (1)`. One MeshLink container had an
  active configured channel-set/database consistent with the user's connected-radio setup, but its parent projection
  contained zero joined/automatic/historical MeshLink channels. App Group container lookup failed on both phones
  because the installed diagnostic personal-team profiles still omit the shared App Group and Keychain entitlements.
  The Apple Gateway therefore correctly remained fail closed, and no arbitrary numeric-slot binding was saved or
  presented as proof. The second iPhone had no configured channel set/current radio evidence.
- Result boundary: connected-Android local stable channel binding passed. Five-phone cross-platform success did not:
  signed iOS profiles for both exact bundle IDs must first authorize the same App Group and shared Keychain group,
  then the iOS catalog/binding test must be rerun. This run proves neither radio queue admission nor RF airtime,
  remote reception, or end-to-end message delivery.

## 2026-09-02 - iOS Apple Gateway stable-channel binding and entitlement source correction
- Affected tracks are the legacy-primary Apple Gateway path in this MeshLink iOS repository and the separately signed
  NTsocial iOS parent in `../NTsocial_release`. Android is the behavioral reference and is not changed by this work;
  Desktop/Windows gains no IPC or Gateway behavior. Secondary iOS Meshtastic endpoint graphs remain intentionally
  fail closed for Apple Gateway.
- MeshLink Debug and Release select the same Apple Gateway entitlement source. Parent Debug now selects a Gateway-only
  entitlement source while parent Release retains its full capability file. Every source configuration declares App
  Group `group.com.ntsocial.meshlink.gateway` and Keychain access-group suffix
  `com.ntsocial.meshlink.gateway`. Source/build settings cannot authorize either capability: both exact bundle IDs still
  need eligible-team provisioning profiles carrying the same groups. The currently installed personal-team profiles do
  not, so their correct fail-closed device result remains authoritative until entitled Apps are installed.
- Parent Channel Info replaces arbitrary `0...255` MeshLink slot entry with current catalog choices that advertise
  `SEND_NTSOCIAL_ENVELOPE`. A manual logical-channel binding durably stores the opaque stable source identity; the
  numeric slot is only a last-known display/compatibility locator. At send time the adapter requires that source and
  resolves its current preferred catalog row, route token, capability, and radio generation. A removed source, expired
  route, generation mismatch, missing capability, or reused old slot with a different source fails before command
  publication; no numeric fallback fabricates success.
- Ordinary channel social overlays plus reconcile, channel-probe, overlay/delivery-receipt, and ATaK reply flows now
  preserve the stable source through the parent provider boundary. MeshLink renews only READY projections while its
  host is foreground-active, at half of the 120-second route TTL and serialized with command processing. Foreground
  exit stops renewal; it does not confer authority on a stale route.
- MeshLink Gateway/runtime focused tests passed 14/14. The JDK-21/en-US full gate completed 2,018 actionable tasks
  (100 executed, 1,918 up-to-date): formatting, Android Debug assembly, tests/`allTests`, shared KMP/iOS Simulator
  compilation, and both Android Debug lints passed; exit 1 was only the six recorded pre-existing Detekt findings.
  Two stale Compose test classes exposed by the first run were corrected test-only to use resource strings and the
  right semantics tree; complete messaging and settings JVM modules then passed 25/25 and 62/62. Signing-disabled
  clean Xcode Simulator Debug and generic iPhoneOS Release builds passed; the Release framework/app build completed
  243 Gradle tasks (48 executed, 195 up-to-date). The parent source-frozen parity gate passed all 13 steps in 681
  seconds, including 785/785 SwiftPM tests and its source-state audit. No signed physical two-App mailbox/HMAC round
  trip, connected-radio queue admission, RF airtime, remote receipt, multi-endpoint Apple Gateway, TestFlight, or App
  Store evidence follows from the source change.

## Golden Context (stable across sessions)
- Always check `.skills/compose-ui/strings-index.txt` before reading `strings.xml`.
- Run `python3 scripts/sort-strings.py` after adding strings to keep the index organized.
- Always check `gh run list` before pushing.
- Pre-commit hook `scripts/ai-guardrail.sh` protects against binary leaks (see script for install).

## 2026-09-03 - iOS restored BLE session recovery and physical Apple Gateway READY
- The user's reconnect regression was reproduced on the previously connected iPhone: CoreBluetooth retained the old
  MeshLink GATT link and the Meshtastic PhoneAPI stopped advertising, while Kable 0.42 rebuilt its wrapper as logically
  disconnected. The fallback scan was independently broken on Apple because it supplied unsupported `Filter.Address`.
- Saved CoreBluetooth identifiers now use direct peripheral reconstruction, protected FROMNUM verification, and exact
  handoff of the prepared peripheral/scope. A five-second restored-session probe releases/closes the stale wrapper,
  waits one second, and retries with a fresh wrapper when iOS does not replay `didConnect`. Apple service-only fallback
  scanning retains exact identifier comparison; Android/Desktop behavior is unchanged. Scanner connection handlers use
  `safeCatching` so coroutine cancellation does not surface as `Job was cancelled`.
- A final ownership review found and closed the handoff-cancellation gap: prepared sessions now carry exact device-object
  ownership, take/discard use identity-checked CAS, transport and pairing UI always discard unclaimed ownership under
  cancellation, and a 30-second lease releases any abandoned entry. A stale attempt cannot remove a newer owner.
  Saved-device recovery also re-reads the exact repository device after `bond`, preventing a concurrent same-address
  pairing winner from being shadowed by a second wrapper during transport handoff.
- Transport close now boundedly joins the active connection job before disconnect so a taken prepared peripheral cannot
  install after teardown. If the handoff exceeds five seconds, detached cleanup waits for completion and disconnects
  again; the pending job remains identity-owned so repeated close cannot cancel the fallback. Normal and six-second
  timeout/repeated-close regressions pass on JVM and Android host, and iOS arm64/Simulator compilation passes.
- The signed App-Group/Keychain-entitled Debug build was data-preserving installed on both iPhone 15 devices. The
  affected phone reconnected to its exact saved node, sustained reads/writes, recovered after a cold process restart,
  and later handled indications while suspended without crash/fatal. The parent stored two current same-generation
  READY projections (`MediumFast` primary, `NTsocial` secondary), both send-capable. The other phone correctly retained
  its no-radio state and settings.
- BLE JVM passed 47/47, Android BLE host passed 40/40, core-network known-device/cancelled-handoff regressions and iOS Simulator/arm64
  compilation passed, and the physical signed Xcode Debug build passed strict verification. The full one-worker gate
  completed 1,946 tasks; all formatting/build/test/KMP/lint work passed and only the six known pre-existing Detekt
  findings remained. An initial Compose dialog test-host flake passed 49/49 in isolation and under both en-US/zh-TW.
- Evidence does not include RF/remote receipt, concurrent multi-radio iPhone operation, manual binding to an ordinary
  joined NTsocial channel, Release/TestFlight/App Store, or a post-fix Channels-screen visual run. Full report:
  `IOS_RESTORED_BLE_SESSION_RECOVERY_REMEDIATION_REPORT_2026-09-03.md`.

## 2026-09-03 - iOS in-channel Meshtastic QR scanner
- The endpoint-scoped Channels page now launches a QR-only VisionKit scanner with the same mask, square reticle,
  corner dimensions, and close placement as Android. Results feed the selected endpoint's existing shared
  Add/secondary-replace/full-replace dialog and exact-session channel apply path.
- The iOS scanner capability is deliberately Channels-only. Nodes, Contacts, and Wi-Fi retain their prior unavailable
  scanner state so a selected secondary radio cannot fall through a root/legacy-primary dialog. Native request tokens
  reject duplicate/stale completions; camera teardown is synchronous and scanned content is not logged. Channel parse
  errors are redacted.
- Focused formatting, JVM tests, Simulator framework compilation, Xcode Debug, and the signed arm64 Debug host
  build passed. The final Debug App was data-preserving installed on both connected iPhone 15 devices. On the phone
  connected to a Meshtastic node, physical XCUITest opened the Channels scanner, verified its close control and visual
  reticle, closed it, and returned to Channels; the exact final installed build passed in 18.332 seconds. Both Apps launched.
- This evidence covers the physical scanner surface and navigation only. No optical QR decode, channel mutation or
  readback, RF/remote receipt, Release/TestFlight/App Store, or pre-A12 iPad claim is made.

## 2026-09-03 - iOS native outbound queue and two-way RF remediation
- Reproduced the user's one-way `1407` Android-to-`0809` iOS symptom from the active iPhone Room store without reading
  message bodies or PSKs. Android-origin text was present as `RECEIVED`, while all 15 locally composed native primary
  and direct-message rows remained `QUEUED`; 18 older Apple Gateway private-app rows were also pending.
- Root cause was iOS queue ownership inference: a non-null stable source identity was treated as proof that the row was
  Apple Gateway-owned even though normal primary-channel text also stores that identity. The first Gateway row waiting
  for its session gate stopped the entire time-ordered drain, so later native direct messages were head-of-line blocked.
- Added explicit durable Gateway provenance. Only the outbound private-app port or an Apple Gateway origin client ID
  requires the Gateway session; normal native text drains independently. Native work is ordered ahead of gated Gateway
  work, while all exact Gateway session/source validation remains intact and malformed Gateway rows fail closed.
- The focused iOS durable-queue suite passed 3/3, changed-scope formatting/Detekt passed, and the final signed arm64 Debug Xcode
  build succeeded. The exact build was installed data-preservingly on the iPhone connected to the `0809` radio.
- After launch, the former native backlog reached the radio: one primary row became `DELIVERED` and the burst remainder
  returned Meshtastic error 38 (`RATE_LIMIT_EXCEEDED`) instead of remaining App-side queued. Fresh rows then recorded
  three iOS primary broadcasts as `DELIVERED` and a fresh Android `!835d30ae` primary broadcast as `RECEIVED` on iOS.
  The user manually confirmed the requested two-way physical-phone message flow passed.
- Android code and radio/channel configuration were not changed. At the user's request, validation stopped after the
  focused regression, signed build/install, durable-state check, and real-device exchange; no root-wide matrix, soak,
  range, Release/TestFlight, or store test was run. Full report:
  `IOS_OUTBOUND_NATIVE_MESSAGE_QUEUE_REMEDIATION_REPORT_2026-09-03.md`.

## 2026-09-03 - Android message composer IME inset remediation
- Affected the Android message UX; the one-line `Modifier.imePadding()` correction is in the shared
  `MessageScreen` bottom bar, so both channel conversations and private-message conversations use the same fix.
  Desktop has no software-keyboard inset in the tested JVM path, and the focused iOS Simulator compilation passed.
- The focused JDK-21/en-US gate passed: messaging Spotless/Detekt, all 25 messaging JVM tests, messaging iOS
  Simulator compilation, and Google Debug assembly. Per the user's explicit request not to over-test this small UX
  correction, no root-wide gate, lint matrix, soak, RF, or delivery test was run.
- Built Google arm64 Debug `com.ntsocial.meshlink.google.debug` `1.0.8 (9)`. The 55,509,844-byte APK has SHA-256
  `8C2F0715F634255EE250D89B8657B6E84C5FF1AFC8777FC823C71E0C1CEA27EF` and a valid Android Debug v2 signature.
- Clean-uninstalled the old exact package and installed that same APK on USB OPPO CPH2695
  (`TWBYJJRWSGHIGU55`) and Wi-Fi ADB Samsung SM-S9280 (`192.168.1.105:38235`) plus SM-S9080
  (`192.168.1.108:36581`). All three installed base-APK hashes match the local artifact, completed the Traditional
  Chinese first-launch flow, launched successfully, and retained live App processes. No upstream Meshtastic or
  NTsocial parent package was removed.
- Re-selected `Meshtastic_5d6e` on the SM-S9280 and reached Connected. With the Samsung IME visible, both a primary
  channel conversation and a private-message conversation kept the focused input at `[23,859][1057,1096]` while
  the IME started at y=1332; entered test text and the send icon were fully visible. Hiding the keyboard returned the
  input to `[23,1732][1057,1969]` without retained empty space. Neither test message was sent. The other two phones
  had no radio session, so their evidence is clean install/onboarding/startup only; no RF or remote receipt is claimed.


## 2026-09-05 - Three-track development-state audit and agent guide reconciliation
- Audited source HEAD `99bc567d9777adb15e17133403afda630bcaaa56` (latest product change `6db8c6a8d`)
  against Android/iOS/Desktop production wiring, recent commits, dated reports, build conventions and CI.
  Initial worktree was clean. This task changes documentation/agent guidance only, with no product/CI fixes,
  parent-worktree edits, device installation, or message sending.
- Rebuilt AGENTS.md around current capability, open blockers and bounded evidence; preserved 35 old status
  paragraphs in `docs/archive/agent-status-history-through-2026-09-04.md`. Full Traditional Chinese report:
  `docs/development-status-audit-2026-09-05.md`. Updated Copilot/Claude/Gemini pointers, project-overview and
  testing-ci skills, and supersession notices on README/KMP status/roadmap.
- Source-confirmed Android v3 mismatch: READY secondary gateway sources advertise sends but receive scoped
  `SecondaryGatewayRepository`, whose durable native/overlay methods throw; receiver returns QUEUE_FAILED.
  v3 surface/token/endpoint ledger source exists, but production secondary sending cannot be claimed complete.
  Keep legacy-primary v1/v2 isolation in any future correction. Existing fake/source tests do not exercise
  successful production secondary dispatch; aggregate Sept3 device counts do not resolve this contradiction.
- Reconfirmed live history-clear epoch publication defect. Recorded Swift QR didAdd scanner-instance ownership
  as an un-reproduced review candidate, not an observed wrong-channel apply. Kept Windows first-pair/PIN limitation.
- CI test/Kover inventory omits core gateway/meshcore/radio-fleet, feature meshcore and iOS runtime; root smoke
  inventory is handwritten and misses radio-fleet directly (it still compiles transitively in the local gate).
  Native iOS test executable link and run remain disabled; app/test-source compilation is not native execution.
- Fresh macOS arm64/Homebrew OpenJDK 21.0.11/en-US full audit gate:
  `spotlessCheck detekt assembleDebug test allTests kmpSmokeCompile :app:lintFdroidDebug :app:lintGoogleDebug
  --continue --max-workers=1 --no-configuration-cache` completed in 4m19s, 1,946 actionable tasks:
  374 executed, 3 from cache, 1,569 up-to-date. Formatting, both Android Debug builds/lints, tests,
  Desktop/JVM and KMP/iOS Simulator source compilation passed. Exit 1 is five Detekt tasks / EIGHT findings:
  BLE 3, domain 1, model 1, network 1, plus core UI LocalBarcodeScannerProvider lines 44/55
  CompositionLocalAllowlist. The formerly repeated six-finding snapshot is superseded for this HEAD.
- No new Release/AAB, Xcode/arm64 framework, signed device, RF, Windows-device or store verification was run.
  Historical Sept3 signed iOS BLE/READY and user-confirmed native two-way messaging remain bounded evidence;
  iOS Room observed iOS-origin DELIVERED and Android-origin RECEIVED, not independently captured Android receipt.
  Search memory by date/topic: older entries were both prepended and appended, not globally sorted.
