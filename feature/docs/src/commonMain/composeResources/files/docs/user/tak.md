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

Nodes configured with TAK-related roles:

| Role | Description |
|------|-------------|
| TAK | Full TAK interoperability |
| TAK Tracker | Position-only TAK output |

## Usage with ATAK

Once configured:
- Meshtastic nodes appear as markers on the ATAK map
- Chat messages can bridge between mesh and TAK networks
- Position updates flow bidirectionally

> ⚠️ **Note:** TAK integration requires specific node roles and module configuration. Standard client nodes don't automatically participate in TAK operations.

## Security Considerations

- TAK data shares your position and callsign information
- Ensure your channel encryption is configured when using TAK in sensitive environments
- The TAK module respects the same channel encryption as other Meshtastic messages

---

*Screenshots will be added when the screenshot automation pipeline is operational.*

