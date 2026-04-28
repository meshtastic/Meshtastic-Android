# Changelog

All notable changes to this project will be documented in this file.
The `[Unreleased]` section is automatically updated on every push to `main`.
See [GitHub Releases](https://github.com/meshtastic/Meshtastic-Android/releases) for the full history.

<!-- UNRELEASED_START -->
## [Unreleased]

### Unreleased (not yet in any build)

* fix(ci): skip macOS signing when secrets are missing (#5263) by @James Rich in [`4a29d3ce7`](https://github.com/meshtastic/Meshtastic-Android/commit/4a29d3ce7c7697aacb9a3dfc6bfb4ebf6e3a5b05)

### Internal (v2.7.14-internal.73)
Changes since [`v2.7.14-closed.17`](https://github.com/meshtastic/Meshtastic-Android/releases/tag/v2.7.14-closed.17):

* docs: update CHANGELOG.md (#5262) by @github-actions[bot] in [`fbca32539`](https://github.com/meshtastic/Meshtastic-Android/commit/fbca325390c307e4f3ec34246a3b7b32c4c88f67)
* fix(ci): grant promote.yml required permissions in release dispatch (#5261) by @James Rich in [`0e8c75e4d`](https://github.com/meshtastic/Meshtastic-Android/commit/0e8c75e4d8f5f8956cca73351314197311a5781d)
* docs: update CHANGELOG.md (#5260) by @github-actions[bot] in [`e97b44acb`](https://github.com/meshtastic/Meshtastic-Android/commit/e97b44acb9b9d13acc13039b2eba89549535ae81)
* feat(desktop): native OS notifications via libnotify/osascript/PowerShell (#5253) by @James Rich in [`b60f29d77`](https://github.com/meshtastic/Meshtastic-Android/commit/b60f29d7725f6989e2b5d8943a3698acfed58ce7)
* docs: update CHANGELOG.md (#5258) by @github-actions[bot] in [`3174493ca`](https://github.com/meshtastic/Meshtastic-Android/commit/3174493ca616bc43dbe56a330b80a2574599b6c7)
* feat(ci): segmented changelog with channel dedup and production squash (#5259) by @James Rich in [`e45135241`](https://github.com/meshtastic/Meshtastic-Android/commit/e451352412a46abb16777d9d036db01fb7963e4c)
* fix(ci): align release.yml labels with actual repo labels (#5257) by @James Rich in [`bfb51d00c`](https://github.com/meshtastic/Meshtastic-Android/commit/bfb51d00cf1a8487e0a1df8ef3390b6d60c81c87)
* feat(desktop): ship-readiness metadata & CI scaffolding (#5255) by @James Rich in [`ff3b77748`](https://github.com/meshtastic/Meshtastic-Android/commit/ff3b77748b57369028da4eb121aba783d5156cb1)
* fix(ci): use PRs instead of direct push for changelog updates (#5256) by @James Rich in [`a751a0a72`](https://github.com/meshtastic/Meshtastic-Android/commit/a751a0a7289bc8be207a160fbf46e3c5898f6948)
* feat(ci): overhaul changelog management and PR auto-labeling (#5254) by @James Rich in [`6306e92a7`](https://github.com/meshtastic/Meshtastic-Android/commit/6306e92a7b6bbd202ccdfeb79effd855e6054b84)
* fix(ci): revert jetbrains JDK for desktop release builds (#5252) by @James Rich in [`9f12cf7be`](https://github.com/meshtastic/Meshtastic-Android/commit/9f12cf7bef66dea5b84104831c05bf06d7181c38)
* fix(ci): use JetBrains JDK for desktop release builds (#5251) by @James Rich in [`a47f116d8`](https://github.com/meshtastic/Meshtastic-Android/commit/a47f116d89f715a4ddea466a13be77d6865a073a)
* chore: project-wide cruft cleanup and dead code removal (#5249) by @James Rich in [`247f34221`](https://github.com/meshtastic/Meshtastic-Android/commit/247f342210a91a406e97bdab146051107d5c5230)
* chore(deps): update datadog.gradle to v1.26.0 (#5248) by @renovate[bot] in [`79331bcc7`](https://github.com/meshtastic/Meshtastic-Android/commit/79331bcc77ab260aff22e899a898fde8829305cf)
* fix: remove slashes from strings.xml by @James Rich in [`0f2730d86`](https://github.com/meshtastic/Meshtastic-Android/commit/0f2730d86d1a4647469abe9fb5577494170c57ca)
* chore: Scheduled updates (Firmware, Hardware, Translations, Graphs) (#5247) by @James Rich in [`d4eadee50`](https://github.com/meshtastic/Meshtastic-Android/commit/d4eadee500c39e2e09f352a385ee7f3b2bd1a79f)
* feat(messaging): send message on Enter keypress (#5246) by @James Rich in [`534b4fde2`](https://github.com/meshtastic/Meshtastic-Android/commit/534b4fde2475c9ba77acdd74f095521888f05f90)
* fix(crashlytics): resolve beta 2.7.14 crash issues (#5245) by @James Rich in [`bbb7f8b23`](https://github.com/meshtastic/Meshtastic-Android/commit/bbb7f8b23fa3e19fb93d9f59f8ec998e6e66bf0c)
* chore: Scheduled updates (Firmware, Hardware, Translations, Graphs) (#5244) by @James Rich in [`a035fedcd`](https://github.com/meshtastic/Meshtastic-Android/commit/a035fedcdf974bb60a938d91723393ecdb27fbaf)
* chore(deps): update vico to v3.2.0-next.2 (#5243) by @renovate[bot] in [`b1c7902ec`](https://github.com/meshtastic/Meshtastic-Android/commit/b1c7902ec16253de82e6f012f50f4f5f5817bf2c)
* chore(deps): update io.nlopez.compose.rules:detekt to v0.5.8 (#5242) by @renovate[bot] in [`fb1092e3c`](https://github.com/meshtastic/Meshtastic-Android/commit/fb1092e3c70f6ed142967af5463a0aabc09bfa85)
* chore: Scheduled updates (Firmware, Hardware, Translations, Graphs) (#5241) by @James Rich in [`9100c583b`](https://github.com/meshtastic/Meshtastic-Android/commit/9100c583b6b67afd55fcd0e5217a56702e63aff0)
* chore(deps): update core/proto/src/main/proto digest to 249a808 (#5239) by @renovate[bot] in [`225889ad9`](https://github.com/meshtastic/Meshtastic-Android/commit/225889ad947bf44b1b0955e3345764fd8d64755e)
* chore: Scheduled updates (Firmware, Hardware, Translations, Graphs) (#5240) by @James Rich in [`91f4a17b4`](https://github.com/meshtastic/Meshtastic-Android/commit/91f4a17b4829c908458fd2ff136fc38b0615a50d)

### Closed Beta (v2.7.14-closed.17)
Changes since [`v2.7.13`](https://github.com/meshtastic/Meshtastic-Android/releases/tag/v2.7.13):

#### 🏗️ Features
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
#### 🛠️ Fixes
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
#### 📝 Other Changes
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

**Full Changelog**: https://github.com/meshtastic/Meshtastic-Android/compare/v2.7.13...unreleased
<!-- UNRELEASED_END -->

<!-- RELEASED_START -->
<!-- RELEASED_END -->
