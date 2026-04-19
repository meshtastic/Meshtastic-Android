# Settings Screen Validation Reference

This document describes the validation rules enforced on each radio configuration and module
configuration settings screen. Constraints are sourced from two layers:

1. **Protobuf schema** — field types, enums, and `max_size` annotations defined in the
   `meshtastic/protobufs` submodule.
2. **UI form layer** — additional range checks, conditional visibility, and interval pickers
   implemented in `feature/settings/src/commonMain/.../radio/component/`.

> **Reusable components** live in `core/ui/.../component/`. Each component enforces its own
> validation strategy (see [UI Component Validation](#ui-component-validation) at the bottom).

---

## Table of Contents

- [Device Config Screens](#device-config-screens)
  - [User](#user-configuserconfig)
  - [Device](#device-configdeviceconfig)
  - [Position](#position-configpositionconfig)
  - [Power](#power-configpowerconfig)
  - [Network](#network-confignetworkconfig)
  - [Display](#display-configdisplayconfig)
  - [LoRa](#lora-configloraconfig)
  - [Bluetooth](#bluetooth-configbluetoothconfig)
  - [Security](#security-configsecurityconfig)
- [Module Config Screens](#module-config-screens)
  - [MQTT](#mqtt-moduleconfigmqttconfig)
  - [Serial](#serial-moduleconfigserialconfig)
  - [External Notification](#external-notification-moduleconfigexternalnotificationconfig)
  - [Store & Forward](#store--forward-moduleconfigstoreforwardconfig)
  - [Range Test](#range-test-moduleconfigrangetestconfig)
  - [Telemetry](#telemetry-moduleconfigtelemetryconfig)
  - [Canned Message](#canned-message-moduleconfigcannedmessageconfig)
  - [Audio](#audio-moduleconfigaudioconfig)
  - [Remote Hardware](#remote-hardware-moduleconfigremotehardwareconfig)
  - [Neighbor Info](#neighbor-info-moduleconfigneighborinfoconfig)
  - [Ambient Lighting](#ambient-lighting-moduleconfigambientlightingconfig)
  - [Detection Sensor](#detection-sensor-moduleconfigdetectionsensorconfig)
  - [Paxcounter](#paxcounter-moduleconfigpaxcounterconfig)
  - [Status Message](#status-message-moduleconfigstatusmessageconfig)
  - [Traffic Management](#traffic-management-moduleconfigtrafficmanagementconfig)
  - [TAK](#tak-moduleconfigtakconfig)
- [Channel Config](#channel-config)
- [Interval Configurations](#interval-configurations)
- [UI Component Validation](#ui-component-validation)

---

## Device Config Screens

### User (`Config.UserConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `long_name` | String | maxSize: 39 bytes (proto max_size: 40) | Required (non-blank) |
| `short_name` | String | maxSize: 4 bytes (proto max_size: 5) | Required (non-blank) |
| `is_licensed` | Boolean | Toggle | — |
| `is_unmessagable` | Boolean | Toggle | Enabled only when device capability `canToggleUnmessageable` is true or role is unmessageable |

### Device (`Config.DeviceConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `role` | Enum | Dropdown: `DeviceConfig.Role` entries | Router/ROUTER_LATE/REPEATER roles trigger a confirmation dialog |
| `rebroadcast_mode` | Enum | Dropdown: `RebroadcastMode` entries | ALL, ALL_SKIP_DECODING, LOCAL_ONLY, KNOWN_ONLY, NONE, CORE_PORTNUMS_ONLY |
| `node_info_broadcast_secs` | Interval | Dropdown: `NODE_INFO_BROADCAST` intervals | See [Interval Configurations](#interval-configurations) |
| `double_tap_as_button_press` | Boolean | Toggle | — |
| `disable_triple_click` | Boolean | Toggle (inverted) | UI shows "enabled" when proto field is `false` |
| `led_heartbeat_disabled` | Boolean | Toggle (inverted) | UI shows "enabled" when proto field is `false` |
| `tzdef` | String | maxSize: 64 bytes (proto max_size: 65) | Clearable |
| `button_gpio` | Integer | Numeric input | — |
| `buzzer_gpio` | Integer | Numeric input | — |

### Position (`Config.PositionConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `position_broadcast_secs` | Interval | Dropdown: `POSITION_BROADCAST` intervals | — |
| `position_broadcast_smart_enabled` | Boolean | Toggle | Controls visibility of smart broadcast fields |
| `broadcast_smart_minimum_interval_secs` | Interval | Dropdown: `SMART_BROADCAST_MINIMUM` intervals | Visible only when smart broadcast enabled |
| `broadcast_smart_minimum_distance` | Integer | Numeric input | Visible only when smart broadcast enabled |
| `fixed_position` | Boolean | Toggle | Controls visibility of lat/lon/alt vs GPS fields |
| `latitude` | Double | Range: −90.0 to +90.0 | Visible only when `fixed_position = true` |
| `longitude` | Double | Range: −180.0 to +180.0 | Visible only when `fixed_position = true` |
| `altitude` | Integer | Numeric input | Visible only when `fixed_position = true` |
| `gps_mode` | Enum | Dropdown: `GpsMode` entries | Visible only when `fixed_position = false` |
| `gps_update_interval` | Interval | Dropdown: `GPS_UPDATE` intervals | Visible only when `fixed_position = false` |
| `position_flags` | Bitwise | Checkbox set: `PositionFlags` entries (excl. UNSET) | Bitwise OR of selected flags |
| `rx_gpio` | Integer | Dropdown: GPIO pins 0–48 | — |
| `tx_gpio` | Integer | Dropdown: GPIO pins 0–48 | — |
| `gps_en_gpio` | Integer | Dropdown: GPIO pins 0–48 | — |

### Power (`Config.PowerConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `is_power_saving` | Boolean | Toggle | — |
| `on_battery_shutdown_after_secs` | Interval | Dropdown: `ALL` intervals | — |
| `adc_multiplier_override` | Float | Must be > 0.0 when enabled | Checkbox + float input; float visible only when enabled |
| `wait_bluetooth_secs` | Interval | Dropdown: `NAG_TIMEOUT` intervals | 0s–60s |
| `sds_secs` | Interval | Dropdown: `ALL` intervals | — |
| `min_wake_secs` | Interval | Dropdown: `NAG_TIMEOUT` intervals | 0s–60s |
| `device_battery_ina_address` | Integer | Numeric input (I2C address) | — |

### Network (`Config.NetworkConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `wifi_enabled` | Boolean | Toggle | Controls visibility of Wi-Fi fields |
| `wifi_ssid` | String | maxSize: 32 bytes (proto max_size: 33) | Visible when `wifi_enabled = true` |
| `wifi_psk` | Password | maxSize: 64 bytes (proto max_size: 65) | Visible when `wifi_enabled = true` |
| `eth_enabled` | Boolean | Toggle | Visible only when device `hasEthernet = true` |
| `ntp_server` | String | maxSize: 32 bytes (proto max_size: 33) | — |
| `rsyslog_server` | String | maxSize: 32 bytes (proto max_size: 33) | — |
| `address_mode` | Enum | Dropdown: `AddressMode` (DHCP, STATIC) | Controls visibility of IPv4 fields |
| `ipv4_config.ip` | IPv4 | Regex: `\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}` | Visible when `address_mode = STATIC` |
| `ipv4_config.gateway` | IPv4 | Same regex | Visible when `address_mode = STATIC` |
| `ipv4_config.subnet` | IPv4 | Same regex | Visible when `address_mode = STATIC` |
| `ipv4_config.dns` | IPv4 | Same regex | Visible when `address_mode = STATIC` |

### Display (`Config.DisplayConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `screen_on_secs` | Interval | Dropdown: `DISPLAY_SCREEN_ON` intervals | 15s – Always On |
| `auto_screen_carousel_secs` | Interval | Dropdown: `DISPLAY_CAROUSEL` intervals | 0 (disabled) – 15 min |
| `compass_north_top` | Boolean | Toggle | — |
| `use_12h_clock` | Boolean | Toggle | — |
| `heading_bold` | Boolean | Toggle | — |
| `units` | Enum | Dropdown: `DisplayUnits` entries | Metric / Imperial |
| `wake_on_tap_or_motion` | Boolean | Toggle | — |
| `flip_screen` | Boolean | Toggle | — |
| `displaymode` | Enum | Dropdown: `DisplayMode` entries | — |
| `oled` | Enum | Dropdown: `OledType` entries | — |
| `compass_orientation` | Enum | Dropdown: `CompassOrientation` entries | — |

### LoRa (`Config.LoRaConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `region` | Enum | Dropdown: `RegionInfo` entries | Regional frequency plans |
| `use_preset` | Boolean | Toggle | Controls manual vs preset LoRa settings visibility |
| `modem_preset` | Enum | Dropdown: `ChannelOption` entries | Visible only when `use_preset = true` |
| `bandwidth` | Integer | Numeric input | Visible only when `use_preset = false` |
| `spread_factor` | Integer | Numeric input | Visible only when `use_preset = false` |
| `coding_rate` | Integer | Numeric input | Visible only when `use_preset = false` |
| `hop_limit` | Integer | Dropdown: 0–7 | — |
| `channel_num` | Integer | Numeric input; must be ≤ `numChannels` | — |
| `tx_enabled` | Boolean | Toggle | — |
| `tx_power` | Integer | Signed integer input (dBm) | — |
| `override_duty_cycle` | Boolean | Toggle | — |
| `override_frequency` | Float | MHz value | — |
| `sx126x_rx_boosted_gain` | Boolean | Toggle | — |
| `ignore_mqtt` | Boolean | Toggle | — |
| `config_ok_to_mqtt` | Boolean | Toggle | — |
| `pa_fan_disabled` | Boolean | Toggle | Visible only when device `hasPaFan = true` |

### Bluetooth (`Config.BluetoothConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | — |
| `mode` | Enum | Dropdown: `PairingMode` entries (excl. UNRECOGNIZED) | — |
| `fixed_pin` | String | Exactly 6 digits | — |

### Security (`Config.SecurityConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `public_key` | Base64 | 32 bytes; read-only | Auto-derived from private key |
| `private_key` | Base64 | 32 bytes | Key generation applies bit masking: `f[0] &= 0xF8`, `f[31] = (f[31] & 0x7F) \| 0x40` |
| `admin_key` | Base64 list | maxCount: 3; each entry 32 bytes | — |
| `serial_enabled` | Boolean | Toggle | — |
| `debug_log_api_enabled` | Boolean | Toggle | — |
| `admin_channel_enabled` | Boolean | Toggle | — |
| `is_managed` | Boolean | Toggle | Enabled only when `admin_key` list is non-empty |

---

## Module Config Screens

### MQTT (`ModuleConfig.MQTTConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | — |
| `address` | String | maxSize: 63 bytes (proto max_size: 64) | Non-blank required for connection test |
| `username` | String | maxSize: 63 bytes (proto max_size: 64) | — |
| `password` | Password | maxSize: 63 bytes (proto max_size: 64) | — |
| `encryption_enabled` | Boolean | Toggle | — |
| `json_enabled` | Boolean | Toggle | — |
| `tls_enabled` | Boolean | Toggle | Auto-enforced when using default server with proxy |
| `root` | String | maxSize: 31 bytes (proto max_size: 32) | MQTT root topic |
| `proxy_to_client_enabled` | Boolean | Toggle | — |
| `map_reporting_enabled` | Boolean | Toggle | Controls visibility of map report fields |
| `map_report_settings.publish_interval_secs` | Integer | Minimum: 3600 seconds (1 hour) | — |
| `map_report_settings.position_precision` | Integer | Slider: 12–15 bits | — |

### Serial (`ModuleConfig.SerialConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | — |
| `echo` | Boolean | Toggle | — |
| `rxd` | Integer | Numeric input (GPIO) | — |
| `txd` | Integer | Numeric input (GPIO) | — |
| `baud` | Enum | Dropdown: `Serial_Baud` entries | — |
| `timeout` | Integer | Numeric input | — |
| `mode` | Enum | Dropdown: `Serial_Mode` entries | — |
| `override_console_serial_port` | Boolean | Toggle | — |

### External Notification (`ModuleConfig.ExternalNotificationConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | — |
| `alert_message` | Boolean | Toggle | — |
| `alert_message_buzzer` | Boolean | Toggle | — |
| `alert_message_vibra` | Boolean | Toggle | — |
| `alert_bell` | Boolean | Toggle | — |
| `alert_bell_buzzer` | Boolean | Toggle | — |
| `alert_bell_vibra` | Boolean | Toggle | — |
| `output` | Integer | Dropdown: GPIO pins 0–48 | Shows `active` field when ≠ 0 |
| `active` | Boolean | Toggle | Visible when `output ≠ 0` |
| `output_buzzer` | Integer | Dropdown: GPIO pins 0–48 | Shows `use_pwm` field when ≠ 0 |
| `use_pwm` | Boolean | Toggle | Visible when `output_buzzer ≠ 0` |
| `output_vibra` | Integer | Dropdown: GPIO pins 0–48 | — |
| `output_ms` | Interval | Dropdown: `OUTPUT` intervals | 0s–10s |
| `nag_timeout` | Interval | Dropdown: `NAG_TIMEOUT` intervals | 0s–60s |
| `ringtone` | String | maxSize: 230 bytes | RTTTL format |
| `use_i2s_as_buzzer` | Boolean | Toggle | — |

### Store & Forward (`ModuleConfig.StoreForwardConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | — |
| `heartbeat` | Boolean | Toggle | — |
| `records` | Integer | Numeric input | — |
| `history_return_max` | Integer | Numeric input | — |
| `history_return_window` | Integer | Numeric input (seconds) | — |
| `is_server` | Boolean | Toggle | — |

### Range Test (`ModuleConfig.RangeTestConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | — |
| `sender` | Interval | Dropdown: `RANGE_TEST_SENDER` intervals | 0 (disabled) – 1 hour |
| `save` | Boolean | Toggle | — |

### Telemetry (`ModuleConfig.TelemetryConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `device_telemetry_enabled` | Boolean | Toggle | Visible only when `canToggleTelemetryEnabled` capability |
| `device_update_interval` | Interval | Dropdown: `BROADCAST_SHORT` intervals | 30 min – 72 hours |
| `environment_measurement_enabled` | Boolean | Toggle | — |
| `environment_update_interval` | Interval | Dropdown: `BROADCAST_SHORT` intervals | 30 min – 72 hours |
| `environment_screen_enabled` | Boolean | Toggle | — |
| `environment_display_fahrenheit` | Boolean | Toggle | — |
| `air_quality_enabled` | Boolean | Toggle | — |
| `air_quality_interval` | Interval | Dropdown: `BROADCAST_SHORT` intervals | 30 min – 72 hours |
| `power_measurement_enabled` | Boolean | Toggle | — |
| `power_update_interval` | Interval | Dropdown: `BROADCAST_SHORT` intervals | 30 min – 72 hours |
| `power_screen_enabled` | Boolean | Toggle | — |

### Canned Message (`ModuleConfig.CannedMessageConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | — |
| `rotary1_enabled` | Boolean | Toggle | — |
| `inputbroker_pin_a` | Integer | Numeric input (GPIO) | — |
| `inputbroker_pin_b` | Integer | Numeric input (GPIO) | — |
| `inputbroker_pin_press` | Integer | Numeric input (GPIO) | — |
| `inputbroker_event_press` | Enum | Dropdown: `InputEventChar` entries | — |
| `inputbroker_event_cw` | Enum | Dropdown: `InputEventChar` entries | — |
| `inputbroker_event_ccw` | Enum | Dropdown: `InputEventChar` entries | — |
| `updown1_enabled` | Boolean | Toggle | — |
| `allow_input_source` | String | maxSize: 63 bytes (proto max_size: 16) | — |
| `send_bell` | Boolean | Toggle | — |
| `messages` | String | maxSize: 200 bytes (proto max_size: 201) | Pipe-delimited canned messages |

### Audio (`ModuleConfig.AudioConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `codec2_enabled` | Boolean | Toggle | — |
| `ptt_pin` | Integer | Numeric input (GPIO) | — |
| `bitrate` | Enum | Dropdown: `Audio_Baud` entries | — |
| `i2s_ws` | Integer | Numeric input (GPIO) | — |
| `i2s_sd` | Integer | Numeric input (GPIO) | — |
| `i2s_din` | Integer | Numeric input (GPIO) | — |
| `i2s_sck` | Integer | Numeric input (GPIO) | — |

### Remote Hardware (`ModuleConfig.RemoteHardwareConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | — |
| `allow_undefined_pin_access` | Boolean | Toggle | — |
| `available_pins` | Pin list | maxCount: 4 | Each pin: gpio 0–255, name maxSize 14 bytes, type `RemoteHardwarePinType` enum |

### Neighbor Info (`ModuleConfig.NeighborInfoConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | — |
| `update_interval` | Integer | Numeric input (seconds) | — |
| `transmit_over_lora` | Boolean | Toggle | — |

### Ambient Lighting (`ModuleConfig.AmbientLightingConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `led_state` | Boolean | Toggle | — |
| `current` | Integer | Numeric input | — |
| `red` | Integer | Numeric input (0–255) | — |
| `green` | Integer | Numeric input (0–255) | — |
| `blue` | Integer | Numeric input (0–255) | — |

### Detection Sensor (`ModuleConfig.DetectionSensorConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | — |
| `minimum_broadcast_secs` | Interval | Dropdown: `DETECTION_SENSOR_MINIMUM` intervals | 0 (unset) – 72 hours |
| `state_broadcast_secs` | Interval | Dropdown: `DETECTION_SENSOR_STATE` intervals | 0 (unset) – 72 hours |
| `send_bell` | Boolean | Toggle | — |
| `name` | String | maxSize: 19 bytes (proto max_size: 20) | — |
| `monitor_pin` | Integer | Dropdown: GPIO pins 0–48 | — |
| `detection_trigger_type` | Enum | Dropdown: `TriggerType` entries | — |
| `use_pullup` | Boolean | Toggle | — |

### Paxcounter (`ModuleConfig.PaxcounterConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | — |
| `paxcounter_update_interval` | Interval | Dropdown: `PAX_COUNTER` intervals | 15 min – 72 hours |
| `wifi_threshold` | Integer | Signed integer (RSSI dBm) | Default: −80 |
| `ble_threshold` | Integer | Signed integer (RSSI dBm) | Default: −80 |

### Status Message (`ModuleConfig.StatusMessageConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `node_status` | String | maxSize: 80 bytes | Clearable; requires `supportsStatusMessage` capability |

### Traffic Management (`ModuleConfig.TrafficManagementConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | Requires `supportsTrafficManagementConfig` capability |
| `position_dedup_enabled` | Boolean | Toggle | — |
| `position_precision_bits` | Integer | Numeric input | — |
| `position_min_interval_secs` | Integer | Numeric input (seconds) | — |
| `nodeinfo_direct_response` | Boolean | Toggle | — |
| `nodeinfo_direct_response_max_hops` | Integer | Numeric input | — |
| `rate_limit_enabled` | Boolean | Toggle | — |
| `rate_limit_window_secs` | Integer | Numeric input (seconds) | — |
| `rate_limit_max_packets` | Integer | Numeric input | — |
| `drop_unknown_enabled` | Boolean | Toggle | — |
| `unknown_packet_threshold` | Integer | Numeric input | — |
| `exhaust_hop_telemetry` | Boolean | Toggle | — |
| `exhaust_hop_position` | Boolean | Toggle | — |
| `router_preserve_hops` | Boolean | Toggle | — |

### TAK (`ModuleConfig.TAKConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `team` | Enum | Dropdown with custom colors/labels | Only shown when device role is `TAK` or `TAK_TRACKER` |
| `role` | Enum | Dropdown with custom labels | Only shown when device role is `TAK` or `TAK_TRACKER` |

---

## Channel Config

Channel editing is handled by `EditChannelDialog`.

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `name` | String | maxSize: 11 bytes | Empty name falls back to modem preset name |
| `psk` | ByteString | Must be exactly 0, 16, or 32 bytes | Pre-shared key for encryption |

---

## Interval Configurations

Time-based fields use predefined interval sets from `IntervalConfiguration`. Each configuration
context restricts the available choices to a specific subset of `FixedUpdateIntervals`.

| Configuration | Range | Values (seconds) |
|---------------|-------|------------------|
| `ALL` | All entries | Every `FixedUpdateIntervals` value |
| `BROADCAST_SHORT` | 30 min – 72 hr | 1800, 3600, 7200, 10800, 14400, 18000, 21600, 43200, 64800, 86400, 129600, 172800, 259200 |
| `BROADCAST_MEDIUM` | 1 hr – 72 hr | 3600, 7200, 10800, 14400, 18000, 21600, 43200, 64800, 86400, 129600, 172800, 259200 |
| `BROADCAST_LONG` | 3 hr – 72 hr | 10800, 14400, 18000, 21600, 43200, 64800, 86400, 129600, 172800, 259200 |
| `NODE_INFO_BROADCAST` | 0 (unset), 3 hr – 72 hr | 0, 10800, 14400, 18000, 21600, 43200, 64800, 86400, 129600, 172800, 259200 |
| `NAG_TIMEOUT` | 0 – 60 s | 0, 1, 5, 10, 15, 30, 60 |
| `OUTPUT` | 0 – 10 s | 0, 1, 2, 3, 4, 5, 10 |
| `PAX_COUNTER` | 15 min – 72 hr | 900, 1800, 3600, 7200, 10800, 14400, 18000, 21600, 43200, 64800, 86400, 129600, 172800, 259200 |
| `POSITION` | 1 s – 1 hr | 1, 2, 5, 10, 15, 20, 30, 45, 60, 120, 300, 600, 900, 1800, 3600 |
| `POSITION_BROADCAST` | 0 (unset), 1 min – 72 hr | 0, 60, 90, 300, 900, 3600, 7200, 10800, 14400, 18000, 21600, 43200, 64800, 86400, 129600, 172800, 259200 |
| `GPS_UPDATE` | 0 (unset), 8 s – 24 hr | 0, 8, 20, 40, 60, 80, 120, 300, 600, 900, 1800, 3600, 21600, 43200, 86400 |
| `RANGE_TEST_SENDER` | 0 (disabled) – 1 hr | 0, 15, 30, 45, 60, 300, 600, 900, 1800, 3600 |
| `SMART_BROADCAST_MINIMUM` | 15 s – 1 hr | 15, 30, 45, 60, 300, 600, 900, 1800, 3600 |
| `DETECTION_SENSOR_MINIMUM` | 0 (unset), 15 s – 72 hr | 0, 15, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200, 10800, 14400, 18000, 21600, 43200, 64800, 86400, 129600, 172800, 259200 |
| `DETECTION_SENSOR_STATE` | 0 (unset), 15 min – 72 hr | 0, 900, 1800, 3600, 7200, 10800, 14400, 18000, 21600, 43200, 64800, 86400, 129600, 172800, 259200 |
| `DISPLAY_SCREEN_ON` | 15 s – Always On | 15, 30, 60, 300, 600, 900, 1800, 3600, 2147483647 |
| `DISPLAY_CAROUSEL` | 0 (disabled) – 15 min | 0, 15, 30, 60, 300, 600, 900 |

### Shared Constants

- **GPIO Pins:** 0–48 (inclusive)
- **Hop Limits:** 0–7 (inclusive)

---

## UI Component Validation

The reusable Compose components in `core/ui/.../component/` provide the actual enforcement
layer. Each component accepts validation parameters and rejects or flags invalid input.

### EditTextPreference

- **String mode:** Enforces `maxSize` in UTF-8 bytes via `encodeToByteArray().size <= maxSize`.
  Shows a live byte counter when focused.
- **Int mode:** Validates with `toIntOrNull()`; sets error icon on failure.
- **Float mode:** Validates with `toFloatOrNull()`.
- **Double mode:** Validates with `toDoubleOrNull()`; supports international decimal separators
  (`.`, `,`, `٫`, `、`, `·`).

### SignedIntegerEditTextPreference

- Validates with `toIntOrNull()`; shows error icon on invalid input.

### DropDownPreference

- Closed-set selection only — no free-form input.
- Automatically filters `UNRECOGNIZED` and deprecated enum entries.

### SwitchPreference

- Binary toggle; inherently valid. Disabled state shown at reduced opacity.

### SliderPreference

- Discrete steps only; values clamped with `coerceIn()` and rounded to the nearest valid index.

### EditIPv4Preference

- Regex validation: `\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}`.
- Stored as a little-endian `Int`; displayed as dotted-decimal.

### EditPasswordPreference

- Delegates to `EditTextPreference` with `maxSize` enforcement and password visibility toggle.

### EditBase64Preference

- Validates Base64 encoding on input.
- Flags all-zero 32-byte values as errors (empty/unset key).

### BitwisePreference

- Closed checkbox set; toggles individual bit flags via XOR.
- "Clear" button resets to 0.

### EditListPreference

- Enforces `maxCount` for list length.
- Per-element validation varies by type: `Int` range 0–255 for GPIO pins,
  `ByteString` via Base64 parsing, composite `RemoteHardwarePin` with nested field rules.

### PositionPrecisionPreference

- State machine with three modes: disabled (0), precision range (10–19), full precision (32).
- Slider constrained to 10–19 with 8 discrete steps.

### ConfigState

- Generic wrapper that tracks `isDirty` (current value ≠ initial value).
- Changes are only persisted on explicit save; cancel reverts to the initial snapshot.
