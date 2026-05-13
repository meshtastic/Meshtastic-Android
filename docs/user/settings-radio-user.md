---
title: Settings — Radio & User
nav_order: 7
last_updated: 2026-05-13
description: Configure your radio hardware, LoRa presets, user profile, position sharing, power management, and security.
aliases:
  - settings
  - radio-config
  - user-config
  - lora
---

# Settings — Radio & User

Configure your radio hardware and user identity parameters.

## User Settings

### User Profile

| Setting | Description |
|---------|-------------|
| Long Name | Your display name (up to 39 characters) |
| Short Name | 4-character abbreviated name |
| Licensed Operator | Enable if you hold an amateur radio license (enables higher power) |

### Applying Changes

After modifying settings, tap **Save** to write the configuration to your radio. The device may reboot to apply changes.

![Settings appearance section](/assets/screenshots/settings-radio-user_lora_config.png)

## Radio Configuration

### Device Config

| Setting | Description | Default |
|---------|-------------|---------|
| Role | Node behavior (Client, Router, etc.) | Client |
| Serial Output | Enable serial console output | Disabled |
| Debug Log | Enable verbose debug logging | Disabled |
| Rebroadcast Mode | How the node retransmits messages | All |
| Node Info Broadcast (s) | Interval for broadcasting node info | 10800 |
| Double-tap Button | Action for double-tap button press | Disabled |

### LoRa Config

| Setting | Description | Default |
|---------|-------------|---------|
| Region | Regulatory region for frequency bands | Unset (must configure) |
| Modem Preset | Speed/range tradeoff | LongFast |
| Hop Limit | Maximum retransmit hops | 3 |
| TX Power | Transmission power (dBm) | 0 (max for region) |
| Frequency Offset | Fine-tune frequency (MHz) | 0 |
| Channel Bandwidth | Bandwidth setting | Default for preset |

> ⚠️ **Important:** You **must** set your region before transmitting. Operating without the correct region may violate local radio regulations. See the [region configuration guide](https://meshtastic.org/docs/getting-started/initial-config) on meshtastic.org for details.

### Modem Presets

| Preset | Range | Speed | Use Case |
|--------|-------|-------|----------|
| Short Turbo | Shortest | Fastest | Dense urban |
| Short Fast | Short | Fast | Urban areas |
| Medium Fast | Medium | Fast | Suburban |
| Long Fast | Long | Moderate | General use (default) |
| Long Moderate | Longer | Slower | Rural |
| Long Slow | Longest | Slowest | Maximum range |
| Very Long Slow | Very Long | Very Slow | Extreme range |

### Display Config

| Setting | Description |
|---------|-------------|
| Screen Timeout | Time before display sleeps |
| Display Units | Metric or Imperial |
| GPS Format | DMS, Decimal, UTM, MGRS, OLC |
| OLED Type | Auto, SSD1306, SH1106, SH1107 |
| Compass North | True North or Magnetic North |

### Position Config

| Setting | Description |
|---------|-------------|
| GPS Enabled | Enable/disable GPS |
| GPS Update Interval | How often to acquire GPS fix |
| Position Broadcast (s) | How often to share position |
| Smart Position | Enable movement-based broadcasting |
| Fixed Position | Use a manually set position |

### Power Config

| Setting | Description |
|---------|-------------|
| Power Saving | Enable low-power sleep mode |
| Shutdown After (s) | Auto-shutdown idle timer |
| ADC Multiplier | Battery voltage calibration factor |
| Wait Bluetooth (s) | Time to wait for BLE connection at boot |
| Mesh SDS Timeout (s) | Super-deep-sleep timeout |

### Network Config

| Setting | Description |
|---------|-------------|
| WiFi Enabled | Enable WiFi radio (ESP32 devices) |
| WiFi SSID | Network name to connect to |
| WiFi PSK | Network password |
| NTP Server | Time synchronization server |
| Syslog Server | Remote logging server |

### Bluetooth Config

| Setting | Description |
|---------|-------------|
| Bluetooth Enabled | Enable/disable BLE radio |
| Pairing Mode | Fixed PIN, Random PIN, or No PIN |
| Fixed PIN | PIN code for pairing (default: 123456) |

### Security Config

| Setting | Description |
|---------|-------------|
| Public Key | Your node's public key (read-only) |
| Admin Key | Key for remote administration |
| Private Key | Your node's private key (handle securely) |
| Admin Channel Enabled | Allow admin commands via channel |
| Managed Mode | Restrict non-admin channel changes |

Settings use standard preference controls — dropdowns, toggles, and sliders:

| Control | Screenshot |
|---------|------------|
| Dropdown | ![Dropdown](/assets/screenshots/settings_dropdown.png) |
| Toggle | ![Toggle](/assets/screenshots/settings_switch.png) |
| Slider | ![Slider](/assets/screenshots/settings_slider.png) |

## Related Topics

- [Settings — Modules & Admin](settings-module-admin) — optional feature modules and device administration
- [Signal Meter](signal-meter) — how modem presets affect signal quality thresholds
- [LoRa configuration](https://meshtastic.org/docs/configuration/radio/lora) — detailed LoRa settings reference on meshtastic.org
- [Initial configuration](https://meshtastic.org/docs/getting-started/initial-config) — region setup guide on meshtastic.org

---

