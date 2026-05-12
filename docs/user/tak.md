---
title: TAK Integration
nav_order: 10
aliases:
  - tak
  - atak
  - team-awareness-kit
---

# TAK Integration

Meshtastic integrates with the Team Awareness Kit (TAK) ecosystem, enabling interoperability between Meshtastic mesh devices and TAK applications like ATAK and WinTAK.

## Overview

The TAK module allows Meshtastic nodes to:
- Share position data in TAK-compatible CoT (Cursor on Target) format
- Appear as team members on TAK map displays
- Receive TAK PLI (Position Location Information) messages

## Setup

### Prerequisites

- ATAK (Android Team Awareness Kit) or WinTAK installed
- Meshtastic ATAK Plugin installed
- TAK module enabled on your Meshtastic radio

### Configuration

1. Navigate to **Settings → Module Config → TAK**.
2. Enable the TAK module.
3. Configure the TAK team/group settings:

| Setting | Description |
|---------|-------------|
| Enabled | Activate TAK interop |
| Mode | TAK-compatible output mode |

### ATAK Plugin Setup

1. Install the Meshtastic ATAK Plugin from the plugin repository.
2. Open ATAK and enable the Meshtastic plugin.
3. The plugin bridges messages between ATAK and your mesh network.

## TAK Roles

Nodes configured with TAK-related roles behave differently from standard clients:

| Role | Description |
|------|-------------|
| **TAK** | Full TAK interoperability — sends and receives CoT data, chat messages, and PLI updates. Functions as a standard client plus TAK bridge. |
| **TAK Tracker** | Position-only TAK output — automatically broadcasts PLI at regular intervals without user interaction. Optimized for unattended position beacons (vehicles, equipment, waypoints). Does not relay chat messages. |

> 💡 **Tip:** Use **TAK Tracker** for devices that only need to report position (e.g., a radio mounted in a vehicle). Use **TAK** for devices where users actively participate in TAK operations.

### CoT (Cursor on Target) Format

TAK messages use the Cursor on Target XML format — a military standard for sharing situational awareness data. Meshtastic converts its internal protobuf messages to CoT format when bridging to TAK systems, so no manual format conversion is needed.

## Usage with ATAK

Once configured:
- Meshtastic nodes appear as markers on the ATAK map with callsign labels
- Chat messages can bridge between mesh and TAK networks
- Position updates flow bidirectionally between Meshtastic and TAK
- TAK Tracker nodes broadcast PLI automatically — their positions appear on ATAK maps without any ATAK-side configuration

> ⚠️ **Note:** TAK integration requires specific node roles and module configuration. Standard client nodes don't automatically participate in TAK operations.

## Security Considerations

- TAK data shares your position and callsign information
- Ensure your channel encryption is configured when using TAK in sensitive environments
- The TAK module respects the same channel encryption as other Meshtastic messages

---

