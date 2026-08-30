---
title: TAK Integration
parent: User Guide
nav_order: 10
last_updated: 2026-08-29
description: Interoperate with ATAK and WinTAK — CoT position sharing, TAK roles, and plugin setup.
aliases:
  - tak
  - atak
  - team-awareness-kit
---

# TAK Integration

Meshtastic integrates with the Team Awareness Kit (TAK) ecosystem, enabling interoperability between Meshtastic radios and TAK applications like ATAK and WinTAK.

## Overview

The TAK module allows Meshtastic nodes to:

- Share position data in TAK-compatible CoT (Cursor on Target) format
- Appear as team members on TAK map displays
- Receive TAK PLI (Position Location Information) messages

## Setup

### Prerequisites

- ATAK (Android Team Awareness Kit), iTAK, or WinTAK installed
- Your node's **Role** (Device Config) set to **TAK** or **TAK Tracker** — this is what makes the
  TAK module appear in Module Config at all

> ⚠️ **Warning:** The old **Meshtastic ATAK Plugin** is no longer part of this path and cannot
> work. It bridged through the cross-process AIDL API, which was removed in app 2.8.0; the mesh
> service is now in-process only. Do not install it. Interop today runs over the app's own local
> TAK server plus the Mesh to CoT Converter, both described below, with stock ATAK/iTAK/WinTAK.

### Configuration

Navigate to **Settings → Module Config → TAK**. The module's own settings are your TAK identity —
there is no separate enable switch here, because the **Role** setting in Device Config is what
turns TAK on. Your node broadcasts this identity, which appears on TAK maps.

| Setting     | الوصف                                                                                                                    |
| ----------- | ------------------------------------------------------------------------------------------------------------------------ |
| Team Color  | Your team color on the TAK map (e.g., Blue, Red, Cyan, Green)         |
| Member Role | Your operational role within that team (Team Member, Team Lead, HQ, Medic, RTO, etc.) |

Your TAK callsign isn't a separate setting — it's derived automatically from your Meshtastic node
name.

> 💡 **Tip:** Team/role colors are the standard TAK affiliation colors. Coordinate with your TAK
> team to use consistent team assignments.

### Local TAK Server

The app can also run a **local TAK server** so ATAK/iTAK on the **same phone** can connect directly, without a remote TAK server. The server binds to localhost only (`127.0.0.1:8089`) and uses TLS with mutual certificate authentication (mTLS), so it is not reachable from other devices on the network. Open **Settings → Module Config → TAK → TAK Server**:

![Local TAK Server settings with enable toggle and export option](../../assets/screenshots/tak_server_enabled.png)

- **Enable Local TAK Server** — starts the loopback-only mTLS server on port **8089** for ATAK/iTAK connections from the same phone.
- **TAK Mesh Channel** — selects which Meshtastic channel outgoing TAK traffic is sent on (default: the primary channel, index 0). Incoming TAK traffic is accepted from any channel. Matches the equivalent setting on iOS and in the legacy ATAK plugin.
- **Mesh to CoT Converter** — off by default, and shown under the server toggle. With the server
  running, this synthesizes a CoT contact for every node in your node database, so ordinary
  Meshtastic nodes appear on the ATAK map as contacts. **This is what replaced the old plugin's
  node visibility** — without it, only TAK-role nodes show up.
- **Export TAK Data Package** — generates a `.zip` data package that ATAK/iTAK can import to connect to this server.

## TAK Roles

Nodes configured with TAK-related roles behave differently from standard clients:

| Role            | الوصف                                                                                                                                                                                                                                                                               |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **TAK**         | Full TAK interoperability — sends and receives CoT data, chat messages, and PLI updates. Functions as a standard client plus TAK bridge.                                                                                                            |
| **TAK Tracker** | Position-only TAK output — automatically broadcasts PLI at regular intervals without user interaction. Optimized for unattended position beacons (vehicles, equipment, waypoints). Does not relay chat messages. |

> 💡 **Tip:** Use **TAK Tracker** for devices that only need to report position (e.g., a radio mounted in a vehicle). Use **TAK** for devices where users actively participate in TAK operations.

### CoT (Cursor on Target) Format

TAK messages use the Cursor on Target XML format — a military standard for sharing situational awareness data. Meshtastic converts its internal protobuf messages to CoT format when bridging to TAK systems, so no manual format conversion is needed.

## Wire Format (V1 / V2)

Meshtastic supports two TAK wire formats, chosen automatically based on the connected radio's firmware — no manual configuration needed:

| Format                          | Compatibility                                            | Features                                                                                                                                                                                                           |
| ------------------------------- | -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| V1 (Legacy)  | Firmware 2.7.x and older | Bare protobuf encoding on port 72. Supports position sharing (PLI) and chat (GeoChat) only — shapes, markers, routes, and other typed CoT events are dropped |
| V2 (Current) | Firmware 2.8.0+          | Compact, zstd-compressed encoding on port 78. Adds shapes, markers, routes, aircraft, casevac, emergency, and task CoT types on top of everything V1 supports                                      |

A node still relays legacy V1 packets from older nodes even while running V2 itself, so mixed-firmware meshes keep working.

## Usage with ATAK

Once configured:

- Meshtastic nodes appear as markers on the ATAK map with callsign labels
- Chat messages can bridge between mesh and TAK networks
- Position updates flow bidirectionally between Meshtastic and TAK
- TAK Tracker nodes broadcast PLI automatically — their positions appear on ATAK maps without any ATAK-side configuration

> ℹ️ **Note:** TAK integration requires specific node roles. Standard client nodes don't automatically participate in TAK operations — though with **Mesh to CoT Converter** enabled they still appear on the ATAK map as contacts.

## Troubleshooting

| Problem                                 | Cause                                                                                                     | Solution                                                                                                                                                                                           |
| --------------------------------------- | --------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Node doesn't appear on ATAK map         | Wrong Role setting, or Mesh to CoT Converter off                                                          | Set the node's **Role** to TAK or TAK Tracker. For ordinary (non-TAK-role) nodes to appear, also enable **Mesh to CoT Converter** under the TAK Server settings |
| Position updates are stale              | GPS fix lost or interval too long                                                                         | Check GPS status; reduce position broadcast interval in Position Config                                                                                                                            |
| ATAK shows "disconnected"               | The local TAK server is off, or ATAK is pointed elsewhere                                                 | Check **Enable Local TAK Server** is on, and that ATAK is connecting to `127.0.0.1:8089` — re-import the exported data package if unsure                                                           |
| Shapes, markers, or routes not bridging | Sending node is on legacy V1 (firmware 2.7.x or older) | Update the sending node's firmware to 2.8.0+ for V2 wire format                                                                                                    |
| CoT data not flowing                    | Channel mismatch                                                                                          | All TAK nodes must be on the same channel with matching encryption                                                                                                                                 |

## Security Considerations

> 🔒 **Privacy:** TAK data shares your position and callsign information. The TAK module respects
> the same channel encryption as other Meshtastic messages — in sensitive environments, use a
> channel with a non-default key.

## Related Topics

- [Settings — Modules & Admin](settings-module-admin) — TAK module configuration
- [Nodes](nodes) — TAK and TAK Tracker roles in the node list
- [Map & Waypoints](map-and-waypoints) — node positions on the map
