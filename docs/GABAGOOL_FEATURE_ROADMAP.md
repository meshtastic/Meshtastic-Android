# GABAGOOL RADIO — 15-feature implementation tracker

> Planning tracker, not a claim that features are implemented. Audit existing upstream functionality before each change. Maintain GPL and trademark attribution. Test on Android and Intel macOS.

| # | Feature | Scope | Status |
|---|---|---|---|
| 1 | Bot Mode | Opt-in commands, scheduled responses, airtime limits | Planned |
| 2 | Mesh Health Dashboard | Recent nodes, SNR, RSSI, telemetry | Planned |
| 3 | Advanced Offline Maps | Map downloads and node tracking | Planned |
| 4 | Smart Reconnect | BLE/USB reconnect with backoff | Planned |
| 5 | Signal History | Historical graphs and exports | Planned |
| 6 | Visual Traceroute | Supported route diagnostics | Planned |
| 7 | Emergency Mode | Confirmed SOS templates and opt-in location | Planned |
| 8 | Multi-Radio Manager | Radio selection and safe switching | Planned |
| 9 | Weather Alerts | Gateway with geographic filters and deduplication | Planned |
| 10 | Channel Wizard | Encrypted channel setup and QR sharing | Planned |
| 11 | Advanced Messaging | Search, templates and accurate delivery state | Planned |
| 12 | Network Analytics | Availability, traffic and coverage trends | Planned |
| 13 | Battery Monitoring | Telemetry-based alerts | Planned |
| 14 | Backup and Restore | Validated export/import with protected secrets | Planned |
| 15 | Jersey Personality | Optional sounds and Jersey phrases | Planned |

## Delivery gates

1. Fix Android APK CI and verify Intel macOS DMG launches on Tahoe.
2. Implement connectivity reliability, multi-radio management, messaging improvements and backup first.
3. Add dashboards, telemetry, analytics, offline maps and route visualization.
4. Add opt-in bot, weather gateway, emergency templates and Jersey mode.
5. Add automated tests, manual hardware verification, changelog and tagged releases.

## Safety and performance

- Avoid flooding low-bandwidth LoRa meshes: rate-limit all automated transmissions.
- Do not claim guaranteed emergency delivery, end-to-end encryption beyond protocol behavior, or hop details unavailable from the radio.
- Preserve user control over location, alerts, local storage, exports and telemetry retention.
- Confirm desktop-specific feature availability instead of assuming Android parity.
