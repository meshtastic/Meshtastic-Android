# Changelog

All notable changes to this project will be documented in this file.
The `[Unreleased]` section is automatically updated on every push to `main`.
See [GitHub Releases](https://github.com/meshtastic/Meshtastic-Android/releases) for the full history.

<!-- UNRELEASED_START -->
## [Unreleased]

### Unreleased (not yet in any build)

#### 📝 Other Changes
* docs: second-pass audit of docs/en — fix drift and document new features by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6157

### Internal (v2.8.0-internal.19)
Changes since [`v2.8.0-closed.6`](https://github.com/meshtastic/Meshtastic-Android/releases/tag/v2.8.0-closed.6):

#### 🏗️ Features
* feat(map): Site Planner coverage integration — import + estimate by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6136
* feat(node): show CO₂ sensor temperature & humidity on Air Quality page by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6143
* feat(node): histogram of nodes per hop distance (#5745) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6146
* feat(map): F-Droid map-layer parity — share layer UI + logic in common source (#6138) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6148
#### 🛠️ Fixes
* fix(discovery): ship a fresh LoRaConfig when switching to a beacon's preset by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6135
* fix(runtime): harden BLE profile, OTA setup, and DB access by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/6126
* fix(messaging): use stored contact_key to avoid duplicate LazyColumn key crash (#6131) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6142
* fix(metrics): air-quality chart legend follows plotted data, not selection by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6145

### Closed Beta (v2.8.0-closed.6)
Changes since [`v2.7.14`](https://github.com/meshtastic/Meshtastic-Android/releases/tag/v2.7.14):

#### 🏗️ Features
* feat(export): add hop start and relay node columns to CSV export by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5822
* feat(mqtt): add phone-local MQTT proxy cutoff control by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5823
* feat(node): show our node shortname chip on the Nodes tab by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5820
* feat(settings): add remote "Set time" admin action by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5821
* feat(network): on-device capture-replay transport + ingestion fuzzing/hardening by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5846
* perf(node): add stable keys and contentType to telemetry chart lists by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5869
* feat(connections): list only BLE devices visible via scan by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5877
* feat(ui): use modem-preset-relative SNR thresholds for signal quality by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5903
* feat(firmware): link OTAFIX bootloader from slow-DFU success screen by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5917
* feat(node): add GPX export to position log screen by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5919
* feat: offline-first event firmware metadata (JSON schema + bundled asset) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5920
* feat(firmware): drive event firmware branding from bundled metadata by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5929
* feat(lora): consume region→preset compatibility map + TINY presets (protobufs #951) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5834
* feat(lockdown): firmware lockdown mode (provision / unlock / lock-now) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5939
* feat(lora): gate region→preset map + TINY presets on firmware capability by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5941
* feat(security): surface XEdDSA packet signing in node & messaging UI by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5976
* fix(security): make XEdDSA signing shield green & prominent by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5980
* Prevent Range Test from running on public/default channel by @dubsector in https://github.com/meshtastic/Meshtastic-Android/pull/5986
* feat(network): migrate TcpTransport to ktor-network (commonMain) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5995
* feat(ui): StatusSurface AA legibility + node-details signing/transport polish by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5985
* feat: NFC tag writing for shared contacts and channels by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6030
* feat: Waypoint geofences (editor, map overlays, alert engine)  by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6014
* feat(lora): default US region to LongTurbo preset by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6009
* feat(connections): add deep link to trigger a connection by address by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6036
* feat(desktop): add Flathub screenshots to metainfo.xml by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6042
* feat(testing): debug-only skip_onboarding intent extra for AI/CI tooling by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6044
* feat(discovery): surface received Mesh Beacon invitations by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6043
* feat(firmware): nRF52 legacy BLE DFU — stock-bootloader fixes + stranded-device recovery by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6041
* feat(settings): wire is_unmessagable/is_licensed into DeviceProfile export/import by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6065
* Style GeoJSON overlays from simplestyle-spec (fill/stroke) by @garthvh in https://github.com/meshtastic/Meshtastic-Android/pull/6088
* feat(discovery): Mesh Beacon client with iOS 014-mesh-beacons parity by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6097
* feat(messaging): translate chat messages in-place with on-device ML Kit (google flavor only) by @thebentern in https://github.com/meshtastic/Meshtastic-Android/pull/6103
* feat(database): unify device database across transports by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6096
* feat: compute EPA NowCast AQI from PM2.5 history by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6102
* Add secure key backup/restore/delete for security config by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6105
* feat(messaging): @mention with deep-link to node detail (#6098) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6108
* feat(node): label power channels and fix pressure axis scale by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6111
* Show outgoing message status text by @RCGV1 in https://github.com/meshtastic/Meshtastic-Android/pull/6121
#### 🖥️ Desktop
* fix(data): stale firmware/hardware caches — stop cancelling slow API refreshes, prune pulled releases, seed from newer bundles by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6060
* fix(geofence): restrict crossing alerts to creator, add per-geofence opt-in by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6117
#### 🛠️ Fixes
* fix(mqtt): make the MQTT client-id unique per connection by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5755
* fix(ble): Harden BLE connection lifecycle by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5795
* fix(build): isolate ML Kit GenAI to the Google flavor (fix F-Droid rb-check) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5824
* fix(notifications): open node detail when tapping 'New Node Seen' notification by @LesterCheng in https://github.com/meshtastic/Meshtastic-Android/pull/5752
* fix(appfunctions): keep AppSearch document-factory constructors under R8 full mode by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5829
* fix(service): resolve selected-device startup race by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5828
* fix(database): defer FTS backfill on cold start and enforce single-connection pool by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5841
* fix(ble): retrigger connection when bonding is interrupted by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5849
* fix(desktop): terminate process on exit; quit on close when no tray by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5858
* fix(settings): crash opening Position radio-config screen by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5862
* fix(settings): gate Traffic Management config at firmware v2.8.0 by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5864
* fix(network): retry transient connection/IO failures to api.meshtastic.org by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5870
* fix(ui): recognize VPN and all networks for network scan availability by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5882
* fix(data): separate refresh timeouts from Room persistence by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5881
* fix(service): recover stalled WiFi/TCP handshakes by cycling active transport by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5856
* fix(ui): prevent duplicate LazyColumn keys in node metrics logs by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5890
* fix(network): preserve TCP reconnect backoff on short sessions by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5893
* fix(connections): coordinate BLE and TCP scan lifecycle by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5887
* fix(ui): show Wi-Fi unavailable banner only during active network scan by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5892
* fix(network): migrate to mqtt-client 0.4.0 (IP-literal TLS fix) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5895
* fix(ble): require fresh advertisement for auto-reconnect by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5912
* fix(firmware): harden ESP32 OTA + nRF DFU update paths (hardware-validated) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5915
* fix(firmware): batch of P3 OTA/DFU cleanups from the #5915 audit by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5916
* fix(firmware): render chirpy mascot via painterResource in update dialog by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5925
* fix(usb): Add serial presence recovery for USB replug by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5923
* fix(data): Persist TAK module config by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5933
* fix(usb): Suppress expected serial close warnings by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5932
* refactor(connections): Derive DeviceType from InterfaceId by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5935
* fix(usb): Surface permission denial as permanent disconnect by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5943
* refactor(ble): Make Kable connect fallback explicitly bounded by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5944
* refactor(connections): Show one active transport pane by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5956
* fix(ble): Restore bounded bonded reconnect fallback by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5960
* fix(docs): preserve #anchor when rewriting sibling links for Docusaurus by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5963
* fix(ble): Bound Android bonding wait by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5967
* fix(ble): Avoid duplicate bonding retries after pairing failure by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5969
* fix(ble): Stop transport connect after failed bonding by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5973
* fix(ble): Fail bonding promptly when polled state returns none by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5982
* fix(car): wire notifications & emergency, fix TabTemplate crash, pin car-app to stable by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5997
* fix(qr): Serialize channel import writes by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/5999
* fix(ui): stop node signal pill from wrapping; restore full-width spread by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6007
* fix(car): suppress INVISIBLE_MEMBER in CarScreensTest for fdroid build by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6010
* fix(docs): stop builds from churning tracked docs screenshots by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6012
* fix(qr): Preserve incoming channels when adding from QR by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/6013
* fix(car): notification-only car messaging for production; park templated behind flag by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6015
* fix(firmware): repair nRF USB firmware update and post-update reconnect by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6018
* fix(ble): Handle scan registration failure by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/6019
* fix(discovery): show disabled reason below Start Analysis button by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6027
* fix(qr): Stabilize scanner lifecycle and imports by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/6040
* fix(connections): label the connecting-card button "Stop Connecting" by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6046
* fix(messages): Refresh channel placeholders after updates by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/6054
* fix(qr): Filter duplicate ADD imports by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/6056
* fix(ci): rename skip_author to ignore_usernames in .coderabbit.yaml by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6069
* fix(qr): Apply channel replacements reliably by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/6072
* fix(logs): Allow access to DebugPanel Logs while disconnected by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6074
* fix: harden against adversarial mesh-fuzz findings (crash, GC-thrash, unbounded growth, OOM) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6093
* fix: remove satellite-count chip from node-list metrics row by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6100
* fix: derive phone-UI units from OS locale, not radio display config by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6101
* fix(ui): align AdaptiveTwoPane split to the adaptive directive breakpoint by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6112
* fix(settings): Apply manual channel writes in order by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/6077
* fix(settings): Generate fresh PSK for named manual channels by @jeremiah-k in https://github.com/meshtastic/Meshtastic-Android/pull/6076
* fix(discovery): let default-channel users start a scan by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/6120
#### 📝 Other Changes
* refactor(takserver): commonize TAK SDK pipeline, drop redundant zstd/xpp3 deps by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5871
* refactor(settings): remove Traffic Management module config by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5878
* refactor(firmware): dedupe BLE/DFU OTA transport + handler boilerplate by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5918
* refactor(data): consolidate bundled-asset loading behind BundledAssetReader by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5921
* refactor(core:ui): drop redundant SinglePaneSceneStrategy from NavDisplay by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5934
* refactor: drop two over-engineered seams (enum + stdlib Base64) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5945
* refactor(ui): migrate MapView dialog to Compose M3 + drop legacy material dependency by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5988
* refactor(settings): replace SimpleDateFormat with kotlinx-datetime by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5992
* refactor(car): drop dead FuzzyNodeNameResolver duplicate by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5994

## New Contributors
* @LesterCheng made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/5752
* @dubsector made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/5986
* @garthvh made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/6088
<!-- UNRELEASED_END -->

<!-- RELEASED_START -->

## [2.7.14] - 2026-06-03

### 🏗️ Features
* refactor(ble): Centralize BLE logic into a core module by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4550
* feat(ble): Add support for `FromRadioSync` characteristic by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4609
* feat(widget): Add Local Stats glance widget by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4642
* chore(deps): bump deps to take advantage of new functionality by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4658
* feat(maps): Google maps improvements for network and offline tilesources by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4664
* feat: Improve edge-to-edge and display cutout handling by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4669
* feat: upcoming support for tak and trafficmanagement configs, device hw by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4671
* feat: settings rework by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4678
* feat: settings rework part 2, domain and usecase abstraction, tests by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4680
* feat: service decoupling by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4685
* refactor: migrate :core:database to Room Kotlin Multiplatform by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4702
* refactor(ble): improve connection lifecycle and enhance OTA reliability by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4721
* refactor: migrate preferences to DataStore and decouple core:domain for KMP by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4731
* refactor: migrate core modules to Kotlin Multiplatform and consolidat… by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4735
* feat: Migrate project to Kotlin Multiplatform (KMP) architecture by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4738
* refactor: migrate from Hilt to Koin and expand KMP common modules by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4746
* refactor: migrate core UI and features to KMP, adopt Navigation 3 by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4750
* feat: introduce Desktop target and expand Kotlin Multiplatform (KMP) architecture by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4761
* build(desktop): enable ProGuard for release builds by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4772
* feat(desktop): implement DI auto-wiring and validation by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4782
* feat(desktop): expand supported native distribution formats by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4783
* feat: Complete ViewModel extraction and update documentation by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4817
* refactor: Replace Nordic, use Kable backend for Desktop and Android with BLE support by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4818
* feat: Integrate notification management and preferences across platforms by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4819
* feat: service extraction by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4828
* feat: build logic by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4829
* feat: Desktop USB serial transport by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4836
* Add "Exclude MQTT" filter to Nodes view. by @VictorioBerra in https://github.com/meshtastic/Meshtastic-Android/pull/4825
* feat: mqtt by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4841
* feat: Integrate Mokkery and Turbine into KMP testing framework by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4845
* feat: Complete app module thinning and feature module extraction by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4844
* feat: Enhance test coverage  by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4847
* feat: Implement KMP ServiceDiscovery for TCP devices by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4854
* feat: Add KMP URI handling, import, and QR code generation support by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4856
* feat: KMP Debug Panel Migration and Update Documentation by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4859
* feat: Migrate to Room 3.0 and update related documentation and tracks by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4865
* feat: Implement iOS support and unify Compose Multiplatform infrastructure by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4876
* Add InlineMap implementation for F-Droid build by @theKorzh in https://github.com/meshtastic/Meshtastic-Android/pull/4877
* refactor(desktop): remove native MenuBar from main window by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4888
* feat: Migrate networking to Ktor and enhance multiplatform support by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4890
* refactor: adaptive UI components for Navigation 3 by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4891
* feat: Integrate AlertHost into desktop application and add UI tests by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4893
* feat: implement global SnackbarManager and consolidate common UI setup by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4909
* feat: implement unified deep link routing for Kotlin Multiplatform by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4910
* refactor: BLE transport and UI for Kotlin Multiplatform unification by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4911
* Refactor map layer management and navigation infrastructure by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4921
* feat: migrate to Material 3 Expressive APIs by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4934
* Refactor nav3 architecture and enhance adaptive layouts by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4944
* feat(tak): introduce built-in Local TAK Server and mesh integration by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4951
* feat(analytics): expand DataDog RUM integration and align with iOS parity by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4970
* feat(wifi): introduce BLE-based WiFi provisioning for nymea-compatible devices by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4968
* feat(wifi-provision): add mPWRD-OS branding and disclaimer banner by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4978
* feat(charts): adopt Vico best practices, add sensor data, and migrate TracerouteLog by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5026
* refactor(icons): migrate to self-hosted VectorDrawable XMLs via MeshtasticIcons by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5030
* feat(messaging): add IME Send action to message input by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5047
* feat(metrics): redesign position log with SelectableMetricCard and add CSV export to all metrics screens by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5062
* feat(core/ui): add safeLaunch, UiState, KMP permissions, and CMP lifecycle modernization by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5118
* feat(desktop): add entitlements and wire MeshConnectionManager into orchestrator by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5127
* feat(environment): add 1-Wire multi-thermometer (DS18B20) display support by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5130
* feat: add high-contrast theme with accessible message bubbles by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5135
* feat(mqtt): migrate to MQTTastic-Client-KMP by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5165
* feat(mqtt): adopt mqttastic-client-kmp 0.2.0 — disconnect reasons + Test Connection by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5181
* feat(firmware): nRF52 BLE Legacy DFU support by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5209
* feat(service): send polite ToRadio(disconnect=true) before transport close by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5210
* feat(node): smoother remote-admin UX with per-node session tracking by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5217
* fix(ble): unblock reconnect + kable audit (logging, priority, backoff, StateFlow) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5222
* feat: Enhance mPWRD-os WiFi provisioning success state and UI components by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5225
* feat(messaging): add entry points for filter settings by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5229
* feat(messaging): send message on Enter keypress by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5246
* feat(desktop): native OS notifications via libnotify/osascript/PowerShell by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5253
* feat(auto): enable Android Auto messaging notifications by @riddlemd in https://github.com/meshtastic/Meshtastic-Android/pull/5265
* fix: update emoji catalog metadata and improve picker synchronization by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5292
* fix: update notification icon by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5293
* feat(connections): connection sorting & conversation empty channel ranking by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5295
* fix(connections): improve BLE scan reliability and UI lifecycle by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5329
* feat: event firmware easter egg with ambient branding by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5354
* feat: align theme with Design Standards v1.3, remove contrast setting by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5355
* feat(desktop): fix mac notifications, new desktop icons by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5403
* Update notification intents and deep link URI format by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5408
* fix: clarify position precision as ± radius  by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5428
* feat: TAK v2 protocol integration with zstd compression and full CoT type support by @thebentern in https://github.com/meshtastic/Meshtastic-Android/pull/5434
* feat(flatpak): reconstruct standard maven filenames from local Gradle cache by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5538
* fix: use single-shot low battery notifications by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5550
* feat: align node list context menu to canonical 6-item order by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5548
* feat: enable WAL connection pool for parallel reads by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5372
* feat: node list density switching with compact layout and field toggles by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5444
* feat(ai): upgrade Chirpy on-device AI with proper APIs, download UX, and streaming by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5579
* feat: replace LoRa bandwidth text input with constrained dropdown by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5687
* feat: Save unsent chat message as draft by @Copilot in https://github.com/meshtastic/Meshtastic-Android/pull/5686
### 🖥️ Desktop
* fix(desktop): keep Vico package to prevent bytecode verification errors by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5424
### 🛠️ Fixes
* fix(strings): replace plurals by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4596
* fix: replace fdroid map_style_selection string by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4598
* refactor(test): Introduce MeshTestApplication for robust testing by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4602
* fix: spotless by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4604
* feat(build): Implement flavor-specific barcode scanning and build improvements by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4611
* fix(qr): add channels as key to remember block to fix add-channel rac… by @nreisbeck in https://github.com/meshtastic/Meshtastic-Android/pull/4607
* chore(ble): Add Proguard rules for Nordic BLE library by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4618
* ci(release): Use wildcards for APK paths in release workflow by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4619
* chore(ci): Use wildcard for APK paths in release workflow by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4622
* chore(ci): Refine analytics task filtering and improve release debugging by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4624
* Fix/splits by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4626
* Align FDroid MapView constructor with Google version (Issue #4576) by @ujade in https://github.com/meshtastic/Meshtastic-Android/pull/4630
* refactor(analytics): reduce tracking footprint by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4649
* fix(map): location perms and button visibility, breadcrumb taps by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4651
* fix(strings): Correct capitalization of Ham by @alecperkins in https://github.com/meshtastic/Meshtastic-Android/pull/4620
* ci: Split Google artifact attestations and ensure F-Droid uploads by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4665
* fix: Replace strings.xml with app_name resource by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4666
* Disable generate_release_notes in release workflow by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4668
* fix: ui tweaks by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4696
* refactor: simplify traceroute tracking and unify cooldown button logic by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4699
* feat: Add "Mark all as read" and unread message count indicators by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4720
* fix(widget): ensure local stats widget gets updates by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4722
* refactor(ble): increase default timeout for BLE profiling by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4728
* refactor: enhance handshake stall guard and extend coverage to Stage 2 by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4730
* build(ci): optimize release workflow and update Room configuration by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4775
* Disable ProGuard for desktop release and add application icon by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4776
* fix(ble): implement scanning for unbonded devices in common connections ui by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4779
* fix: fix animation stalls and update dependencies for stability by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4784
* build(desktop): include `java.net.http` module in native distribution by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4787
* build: remove PKG from desktop distribution targets by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4788
* build: Update desktop app icons, versioning, and packaging configuration by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4789
* refactor(settings): improve destination node handling in RadioConfigViewModel by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4790
* feat(desktop): add enter-to-send functionality in messaging by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4793
* feat: enhance map navigation and waypoint handling by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4814
* build: fix license generation and analytics build tasks by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4820
* fix: resolve crashes and debug filter issues in Metrics and MapView by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4824
* fix(map, settings): allow null IDs and implement request timeout by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4851
* docs: Unify notification channel management and migrate unit tests by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4867
* fix: Implement reconnection logic and stabilize BLE connection flow by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4870
* fix: Update messaging feature with contact item keys and MQTT limits by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4871
* fix: specify jetbrains in gradle-daemon-jvm.properties by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4872
* fix(settings): remove redundant regex option in DebugViewModel by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4881
* refactor(service): update string formatting for local stats notif by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4885
* refactor(messaging): fix contact key derivation in ContactsViewModel by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4887
* feat: optimistically persist local configs and channels by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4898
* refactor(di): specify disk cache directory for ImageLoader by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4899
* refactor: null safety, update date/time libraries, and migrate tests by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4900
* refactor: remove demoscenario and enhance BLE connection stability by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4914
* refactor(ui): remove labels from navigation suite items by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4924
* build: enable `-Xjvm-default=all` compiler flag by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4929
* fix(ci): update APP_VERSION_NAME output reference in workflows by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4935
* fix(strings): Fix public key description by @Klavionik in https://github.com/meshtastic/Meshtastic-Android/pull/4957
* feat: implement XModem file transfers and enhance BLE connection robustness by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4959
* Refactor navigation to use NodeDetail route and fix radio settings by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4960
* Refactor and unify firmware update logic across platforms by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4966
* fix: improve PKI message routing and resolve database migration racecondition by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4996
* fix: resolve correct node public key in sendSharedContact and favoriteNode by @Copilot in https://github.com/meshtastic/Meshtastic-Android/pull/5005
* fix: resolve bugs across connection, PKI, admin, packet flow, and stability subsystems by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5011
* fix(tak): resolve frequent TAK client disconnections by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5015
* fix(service): resolve MeshService crash from eager notification channel init by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5034
* style: update ic_no_cell and ic_place vector drawables by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5040
* fix(build): prevent DataDog asset transform from stripping fdroid release assets by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5044
* fix(icons): replace outline (FILL=0) pathData with filled (FILL=1) from upstream Material Symbols by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5056
* fix(charts): hoist rememberVicoZoomState above vararg layers to prevent ClassCastException by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5060
* fix(ui): add missing @ParameterName annotations on actual rememberReadTextFromUri declarations by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5072
* fix(settings): hide Status Message config until firmware v2.8.0 by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5070
* fix(transport): Kable BLE audit + thread-safety, MQTT, and logging fixes across transport layers by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5071
* fix(build): remove Compose BOM to resolve compileSdk 37 conflict by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5088
* fix(connections): show device name during connecting state by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5085
* fix(build): add explicit compose-multiplatform-animation dependency by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5095
* fix(nav): restore broken traceroute map navigation by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5104
* fix(build): overhaul R8 rules and DRY up build-logic conventions by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5109
* fix(proguard): disable shrinking for Compose animation classes  by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5116
* fix(icons): audit and correct icon migration regressions from #5030 #5040 #5056 by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5136
* fix: align BLE connection handshake with firmware protocol expectations by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5141
* fix(app): add R8 keep rules for Compose animation/runtime/ui by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5146
* perf(messaging): batch node + reply lookups in message loading by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5149
* fix(app): disable R8 optimization to fix Compose animation freeze by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5150
* fix(node): don't recreate Vico CartesianChartModelProducer on channel switch by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5160
* refactor: use injected ioDispatcher and ApplicationCoroutineScope by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5167
* fix: redact MeshLog proto secrets and centralize Compose keep-rules by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5166
* fix(ui): stable LazyColumn keys, semantic roles, and content descriptions by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5168
* fix(ui): finish accessibility roles and action labels for clickable surfaces by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5170
* fix(widget): drive updates via debounced state observer by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5185
* fix(transport): improve BLE / TCP / USB reconnect and handshake resilience by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5196
* fix(fdroid): prevent NotImplementedError crash on firmware release fetch by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5197
* fix(compass): stop coarse network fixes from clobbering GPS fixes by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5200
* fix(canned-messages): enable multiline text editing for long message lists by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5203
* fix(settings): restore Import/Export button functionality in #4913 by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5204
* refactor: eliminate Accompanist permissions library by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5211
* fix: MQTT proxy connection and probe test failures by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5215
* fix(ble): ensure GATT cleanup runs under NonCancellable on cancellation by @jdogg172 in https://github.com/meshtastic/Meshtastic-Android/pull/5207
* fix(ble): cleanup races discovered while reviewing #5207 by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5221
* fix(ui): make footer buttons expand downwards by @zt64 in https://github.com/meshtastic/Meshtastic-Android/pull/5226
* fix(desktop): suppress Vico ColorScale ProGuard warnings by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5232
* fix(desktop): unbreak release crash via correct ProGuard rules by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5236
* fix(crashlytics): resolve beta 2.7.14 crash issues by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5245
* fix: Resolve top Crashlytics issues for 29320633 beta release by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5278
* fix: persist language switching and correctly map locales by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5287
* fix: ensure snackbar respects safe drawing padding over host modifiers by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5290
* fix(ui): align Cancel and Send enabled state by @elagin in https://github.com/meshtastic/Meshtastic-Android/pull/5284
* fix(data): default new-node notifications off for event firmware by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5323
* fix(network): resolve empty MQTT address and enforce TLS on default server by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5333
* fix(mqtt): harden TLS enforcement, add user CA trust, and improve error diagnostics by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5365
* fix: clamp future lastHeard timestamps to current time on ingestion by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5418
* revert: Update retry settings in gradle-wrapper.properties by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5430
* fix: update screenshots by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5435
* fix(database): make withDb retry logic resilient to varying close messages by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5474
* fix(settings): add input validation for BLE PIN, LoRa modem, and ambient lighting by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5477
* fix(nav): remote admin nodenum + Nav3 consolidation and improvements by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5478
* fix(database): update @Relation annotations for Room 3.0.0-alpha05 by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5507
* fix: prevent node details hang when device hardware API is unreachable by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5514
* fix(settings): remote admin always showed local node config by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5560
* fix: hide battery indicator when level is 0 (never reported) by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5595
* fix: consistent column width for compact node list items by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5596
* fix(emoji): enable androidResources for core:ui to package emoji-data.json by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5597
* fix(proto): prune TakTalkMessage and TakTalkRoomData from Wire codegen by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5624
* fix(database): stabilize flaky DatabaseManagerWithDbRetryTest by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5635
* fix(ble): stop BLE scan on background and downgrade connection priority by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5644
* fix: remove Android Auto manifest entry causing Play Store rejection by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5662
* fix(takserver): emit *:-1:stcp contact endpoint so directed TAK-Talk/GeoChat routes over the mesh by @thebentern in https://github.com/meshtastic/Meshtastic-Android/pull/5661
* fix(takserver): drop CoT the mesh delivers more than once by @thebentern in https://github.com/meshtastic/Meshtastic-Android/pull/5667
* fix: address top Crashlytics crashes in beta 2.7.14 by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5672
* fix(flatpak): source desktop metadata from in-repo packaging dir by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5673
* Rename Desktop application to 'Meshtastic Desktop' by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5677
* fix: address top Crashlytics crashes and non-fatals for build 29320984 by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5684
* fix: show loading overlay immediately for remote config sub-screens by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5694
* fix(node): restore view-tree owners on map dispose so node-list popups aren't invisible by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5699
* fix(firmware): surface error state when BLE OTA connection attempts are exhausted by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5700
* fix(map): replace MarkerComposable with Canvas-rendered bitmaps by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5702
* fix(map): remove manual ViewTree lifecycle owner workarounds by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5704
* fix(map): scope cluster-renderer ViewTreeLifecycleOwner to map host view by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5708
* fix(map): initialize Maps SDK before building marker bitmap descriptors by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5709
* fix(map): eliminate cluster-renderer FATAL and harden black-map paths by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5715
* fix(map): revert app-side Maps SDK init to library-idiomatic, fix inline-map crash by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5719
* fix(map): render cluster markers in-scope to kill ClusterRenderer FATAL by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5723
* fix(map): apply kotlinx-serialization compiler plugin to androidApp by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5726
* fix(map): keep compass icon visible while following bearing by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5728
### 📝 Other Changes
* refactor(ui): compose resources, domain layer by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4628
* Add per-message transport method icons for new message format by @Kealper in https://github.com/meshtastic/Meshtastic-Android/pull/4643
* build: apply instrumented test dependencies conditionally by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4698
* docs: summarize KMP migration progress and architectural decisions by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4770
* ci(release): pass app version to desktop build via environment variable by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4774
* ai: Establish conductor documentation and governance framework by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4780
* fix: fix wrong getChannelUrl() call causing loss of "add" flag and un… by @skobkin in https://github.com/meshtastic/Meshtastic-Android/pull/4809
* chore: Enhance CI coverage reporting and add main branch workflow by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4873
* build(desktop): enable ProGuard minification and tree-shaking by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4904
* build: update Compose Multiplatform and migrate lifecycle dependencies by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4932
* chore: standardize resources and update documentation for Navigation 3 by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/4961
* feat(settings): add DNS support and fix UDP protocol toggle by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5013
* fix: use payload labels in pr_enforce_labels.yml to avoid rate limiting by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5018
* fix: scope labeler trigger to reduce rate limiting and fix bugfix typo by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5020
* test(prefs): migrate DataStore tests from androidHostTest to commonTest by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5092
* fix(resources): add resourcePrefix to KMP + widget modules, rename prefixed resources by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5111
* fix(charts): apply Vico 3.1.0 best-practice audit fixes by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5138
* refactor(di): adopt @KoinApplication with startKoin<T>() compiler plugin API by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5152
* test: migrate MigrationTest to runTest and add missing repository fakes by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5171
* refactor: consolidate metric formatting through MetricFormatter by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5169
* chore(r8): remove redundant keep rules covered by consumer rules by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5172
* Revert "diag(r8): disable minify for release builds (animation-freeze diagnostic)" by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5176
* Fix node-details remove action to preserve confirmation flow by @Copilot in https://github.com/meshtastic/Meshtastic-Android/pull/5192
* Change default ContrastLevel from STANDARD to MEDIUM by @somenice in https://github.com/meshtastic/Meshtastic-Android/pull/5325
* Extract node list display settings to dedicated screen by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5580
* Upgrade takpacket-sdk to version 0.3.0 by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5621
* repo: Add Meshtastic Desktop icon SVGs by @vidplace7 in https://github.com/meshtastic/Meshtastic-Android/pull/5623
* Enhance TAKTALK support with message and room handling, update SDK to v0.3.2 by @thebentern in https://github.com/meshtastic/Meshtastic-Android/pull/5634
* Revert "feat: replace LoRa bandwidth text input with constrained dropdown" by @jamesarich in https://github.com/meshtastic/Meshtastic-Android/pull/5691

## New Contributors
* @nreisbeck made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/4607
* @ujade made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/4630
* @alecperkins made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/4620
* @skobkin made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/4809
* @VictorioBerra made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/4825
* @theKorzh made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/4877
* @Klavionik made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/4957
* @jdogg172 made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/5207
* @zt64 made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/5226
* @riddlemd made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/5265
* @elagin made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/5284
* @somenice made their first contribution in https://github.com/meshtastic/Meshtastic-Android/pull/5325

<!-- RELEASED_END -->
