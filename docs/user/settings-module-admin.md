---
title: Settings — Modules & Admin
nav_order: 8
last_updated: 2026-05-12
aliases:
  - modules
  - module-config
  - administration
---

# Settings — Modules & Admin

Configure optional feature modules and perform device administration.

## Module Configuration

Modules extend Meshtastic with additional capabilities. Each module can be independently enabled or disabled.

### MQTT Module

Bridges mesh messages to/from an MQTT broker for internet connectivity.

| Setting | Description |
|---------|-------------|
| Enabled | Toggle MQTT bridge |
| Server | MQTT broker address |
| Username | Authentication username |
| Password | Authentication password |
| Encryption | Encrypt MQTT payloads |
| JSON Output | Also publish in JSON format |
| TLS | Use secure connection |
| Root Topic | Base MQTT topic path |
| Map Report | Publish position for public map |

See [MQTT](mqtt) for detailed usage guide.

### Serial Module

Enables serial port communication for external integrations.

### External Notification Module

Controls buzzer, LED, or vibration alerts:

| Setting | Description |
|---------|-------------|
| Enabled | Activate notifications |
| Alert Message | Notify on messages |
| Alert Bell | Notify on bell character |
| Output (GPIO) | Pin for notification output |
| Active | High or Low active |
| Duration (ms) | Notification length |

### Store & Forward Module

Buffers messages for nodes that were temporarily offline:

| Setting | Description |
|---------|-------------|
| Enabled | Activate store & forward |
| Heartbeat (s) | Announcement interval |
| Records | Maximum stored messages |
| History Return (max) | Max messages to replay |
| History Return (window) | Time window for replay |

### Range Test Module

Automated range testing tool for evaluating link quality.

### Telemetry Module

Controls what telemetry data your node shares:

| Setting | Description |
|---------|-------------|
| Device Metrics Interval | How often to report device metrics |
| Environment Metrics Interval | How often to report environment sensors |
| Air Quality Enabled | Report particulate sensor data |
| Power Metrics Enabled | Report power usage |

### Canned Message Module

Pre-configured messages accessible from the device buttons (for devices with input hardware).

### Audio Module

Codec2 audio support for voice communication (experimental).

### Remote Hardware Module

GPIO control over the mesh network.

### Neighbor Info Module

Broadcasts information about directly heard neighbors for topology mapping.

### Ambient Lighting Module

Controls onboard RGB LEDs.

### Detection Sensor Module

Motion/door sensor alerts transmitted over the mesh.

### Paxcounter Module

People counter using WiFi/BLE probe requests (ESP32 devices).

### TAK Module

Team Awareness Kit integration. See [TAK](tak) for details.

## Administration

### Remote Administration

Remotely configure nodes that share your admin key:

1. Select the target node in the node list.
2. Navigate to **Settings** for that node.
3. Modify configuration.
4. Tap **Save** — changes are sent over the mesh.

> ⚠️ **Requires:** Admin key configured on both your node and the target node.

### Clean Node Database

Removes stale nodes from your local database that haven't been heard in a configurable time window.

### Factory Reset

Resets all settings to factory defaults. **This cannot be undone.**

### Reboot

Remotely reboot a connected or administered node.

### Debug Panel

View detailed diagnostic information:
- Protocol buffers debug output
- Mesh packet log
- Connection state details

---

