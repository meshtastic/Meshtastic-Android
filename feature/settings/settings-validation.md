# Settings Screen Validation Reference

This document describes the validation rules enforced on each radio configuration and module
configuration settings screen. Constraints are sourced from three layers:

1. **Protobuf schema** — field types, enums, and `max_size` annotations from `meshtastic/protobufs`,
   which this app consumes as the `org.meshtastic:protobufs` artifact pinned in
   `gradle/libs.versions.toml`.
2. **Schema field metadata** — `since_firmware` / `deprecated_since` gates and `min_value` /
   `max_value` bounds that the same artifact carries per field. `Capabilities.offers()` hides a
   field the connected firmware does not have, or has retired, unless the node still holds a
   value in it; `FieldMetadata.intRange` turns declared bounds into an input range.
3. **UI form layer** — additional range checks, conditional visibility, and interval pickers
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
  - [TAK](#tak-moduleconfigtakconfig)
  - [Mesh Beacon](#mesh-beacon-moduleconfigmeshbeaconconfig)
- [Channel Config](#channel-config)
- [Interval Configurations](#interval-configurations)
- [UI Component Validation](#ui-component-validation)

---

## Device Config Screens

### User (`Config.UserConfig`)

The screen edits the node's `User` record. Save is disabled while the long name or short name is invalid.

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `long_name` | String | maxSize: 24 bytes on firmware 2.8.0 and later, 39 bytes before (`Capabilities.maxLongNameBytes`; proto max_size: 40) | Required (non-blank) |
| `short_name` | String | maxSize: 4 bytes (proto max_size: 5) | Required (non-blank) |
| `is_licensed` | Boolean | Toggle (`LicensedModeSetting`) | On the local node, licensed mode splits the long name into **Call sign** (maxSize 7 bytes) and **Long name** (maxSize 14 bytes), which firmware joins as `CALLSIGN//Long name` (`HamName`) |
| `is_unmessagable` | Boolean | Toggle | Enabled when the node reports the field or firmware is 2.6.9 or later (`canToggleUnmessageable`). On older firmware it reads as on for an unmessageable role |

### Device (`Config.DeviceConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `role` | Enum | Dropdown: `DeviceConfig.Role` entries | ROUTER, ROUTER_LATE, and REPEATER trigger a confirmation dialog |
| `rebroadcast_mode` | Enum | Dropdown: `RebroadcastMode` entries | ALL, ALL_SKIP_DECODING, LOCAL_ONLY, KNOWN_ONLY, NONE, CORE_PORTNUMS_ONLY |
| `node_info_broadcast_secs` | Interval | Dropdown: `NODE_INFO_BROADCAST` intervals | See [Interval Configurations](#interval-configurations) |
| `double_tap_as_button_press` | Boolean | Toggle | — |
| `disable_triple_click` | Boolean | Toggle (inverted) | UI shows "enabled" when proto field is `false` |
| `led_heartbeat_disabled` | Boolean | Toggle (inverted) | UI shows "enabled" when proto field is `false` |
| `tzdef` | String | maxSize: 64 bytes (proto max_size: 65) | Clearable; a button fills in the phone's time zone as a POSIX string |
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
| `rx_gpio` | Integer | Dropdown: GPIO pins 0–48 | Schema marks it `diy_only`: hidden on a board whose hardware model sets `oem_fixed_gps`, unless it already holds a nonzero value |
| `tx_gpio` | Integer | Dropdown: GPIO pins 0–48 | Same `diy_only` rule |
| `gps_en_gpio` | Integer | Dropdown: GPIO pins 0–48 | Same `diy_only` rule |

### Power (`Config.PowerConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `is_power_saving` | Boolean | Toggle | — |
| `on_battery_shutdown_after_secs` | Interval | Dropdown: `ALL` intervals | — |
| `adc_multiplier_override` | Float | Switch + decimal input | Turning the switch on sets 1.0 and off sets 0.0. The decimal field shows while the value is > 0, so entering 0 turns the override off |
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
| `enabled_protocols` | Bitwise | Toggle for the `UDP_BROADCAST` bit | Schema gate: firmware 2.6.0 and later. Other bits are preserved |
| `address_mode` | Enum | Dropdown: `AddressMode` (DHCP, STATIC) | Controls visibility of IPv4 fields |
| `ipv4_config.ip` | IPv4 | Regex: `\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b` | Visible when `address_mode = STATIC` |
| `ipv4_config.gateway` | IPv4 | Same regex | Visible when `address_mode = STATIC` |
| `ipv4_config.subnet` | IPv4 | Same regex | Visible when `address_mode = STATIC` |
| `ipv4_config.dns` | IPv4 | Same regex | Visible when `address_mode = STATIC` |

### Display (`Config.DisplayConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `screen_on_secs` | Interval | Dropdown: `DISPLAY_SCREEN_ON` intervals | 15s – Always On |
| `auto_screen_carousel_secs` | Interval | Dropdown: `DISPLAY_CAROUSEL` intervals | 0 (disabled) – 15 min |
| `compass_north_top` | Boolean | Toggle | Schema gate: deprecated from firmware 2.7.1, so shown only on older firmware or when set |
| `use_12h_clock` | Boolean | Toggle | Schema gate: firmware 2.5.22 and later |
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
| `region` | Enum | Dropdown: `RegionInfo` entries | Hides regions the connected firmware's region table lacks (`Capabilities.supportsRegion`), but always keeps the current selection |
| `use_preset` | Boolean | Toggle | Controls manual vs preset LoRa settings visibility |
| `modem_preset` | Enum | Dropdown: `ChannelOption` entries | Visible only when `use_preset = true`. Hides presets the firmware lacks (`supportsPreset`) and those not legal in the selected region, from the firmware's region preset map or, without one, a static fallback. A licensed-only region disables its presets unless the node is licensed. The current preset stays listed, disabled, if it is not legal |
| `coding_rate` (preset) | Integer | Dropdown: "Preset default" (0) or rates above the preset's own, up to 4/8 | Shown when `use_preset = true` on firmware 2.7.18 and later (`supportsCodingRateOverride`); disabled when the preset is already at 4/8 |
| `bandwidth` | Integer | Numeric input outside `LORA_24`; region/target-aware dropdown in `LORA_24` | Visible only when `use_preset = false`; protobuf default `0` is shown as the firmware's 812.5 kHz `LORA_24` default, while unsupported nonzero values remain visible and block Save until replaced |
| `spread_factor` | Integer | Range: 5–12 | Visible only when `use_preset = false`; out-of-range input is flagged and not applied |
| `coding_rate` (manual) | Integer | Range: 5–8 | Visible only when `use_preset = false`; out-of-range input is flagged and not applied |
| `hop_limit` | Integer | Dropdown: 0–7 | Range comes from the schema's `min_value` / `max_value` |
| `channel_num` | Integer | Numeric input; must be ≤ `numChannels` | Shows the automatic slot, labeled auto, while 0 and no frequency override is set |
| `tx_enabled` | Boolean | Toggle | — |
| `tx_power` | Integer | Signed integer input (dBm) | — |
| `override_duty_cycle` | Boolean | Toggle | — |
| `override_frequency` | Float | Decimal input (MHz) | Blank means 0 (automatic, shown with the slot's frequency); text that does not parse as a number is flagged |
| `sx126x_rx_boosted_gain` | Boolean | Toggle | — |
| `ignore_mqtt` | Boolean | Toggle | — |
| `config_ok_to_mqtt` | Boolean | Toggle | — |
| `pa_fan_disabled` | Boolean | Toggle | Visible only when device `hasPaFan = true` |

### Bluetooth (`Config.BluetoothConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | — |
| `mode` | Enum | Dropdown: `PairingMode` entries (excl. UNRECOGNIZED) | — |
| `fixed_pin` | Integer | Exactly 6 digits | Applied only once all six digits are entered; shown zero-padded |

### Security (`Config.SecurityConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `public_key` | Base64 | 32 bytes; read-only | Auto-derived from private key |
| `private_key` | Base64 | 32 bytes | Key generation applies bit masking: `f[0] &= 0xF8`, `f[31] = (f[31] & 0x7F) \| 0x40`. A remote node reports no private key, so the field shows as redacted there |
| `admin_key` | Base64 list | maxCount: 3 | Each entry must decode as Base64; proto max_size is 32 bytes, which the UI does not enforce |
| `serial_enabled` | Boolean | Toggle | — |
| `debug_log_api_enabled` | Boolean | Toggle | — |
| `is_managed` | Boolean | Toggle | Enabled only when `admin_key` list is non-empty |
| Lockdown passphrase | String | Non-empty, maxSize: 64 bytes | `LockdownModeSetting`, firmware 2.8.0 and later (`supportsLockdown`). Boots remaining is clamped to 1–255; hours until expiry and session minutes to ≥ 0 |

---

## Module Config Screens

### MQTT (`ModuleConfig.MQTTConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | — |
| `address` | String | maxSize: 63 bytes (proto max_size: 64) | Non-blank required for the **Test connection** button; empty means `mqtt.meshtastic.org` |
| `username` | String | maxSize: 63 bytes (proto max_size: 64) | — |
| `password` | Password | maxSize: 63 bytes (proto max_size: 32; the UI allows more) | — |
| `encryption_enabled` | Boolean | Toggle | — |
| `json_enabled` | Boolean | Toggle | Schema gate: deprecated from firmware 2.8.0, so shown only on older firmware or when set |
| `tls_enabled` | Boolean | Toggle | Stored and sent exactly as shown. For the public broker the summary notes that the phone's MQTT proxy upgrades to TLS itself |
| `root` | String | maxSize: 31 bytes (proto max_size: 32) | MQTT root topic |
| `proxy_to_client_enabled` | Boolean | Toggle | — |
| `map_reporting_enabled` | Boolean | Toggle | Controls visibility of map report fields. While on, Save requires the location-sharing consent and an interval of at least 3600 seconds |
| `map_report_settings.publish_interval_secs` | Interval | Dropdown: `BROADCAST_MEDIUM` intervals | 1 hour – 72 hours |
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
| `output_ms` | Duration | Dropdown: `FixedOutputDurations` (milliseconds) | 0, 100 ms, 500 ms, 1–5 s, 10 s |
| `nag_timeout` | Interval | Dropdown: `NAG_TIMEOUT` intervals | 0s–60s |
| `ringtone` | String | maxSize: 230 bytes (`RTTTLConfig.ringtone` max_size: 231) | RTTTL format; saved with its own admin message |
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

While the primary channel uses the default or no encryption key (PSK shorter than 2 bytes), the screen
can't turn Range Test on and saves it with `enabled = false`. An already-enabled module can still be
turned off.

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | See above |
| `sender` | Interval | Dropdown: `RANGE_TEST_SENDER` intervals | 0 (disabled) – 1 hour |
| `save` | Boolean | Toggle | — |

### Telemetry (`ModuleConfig.TelemetryConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `device_telemetry_enabled` | Boolean | Toggle | Schema gate: firmware 2.7.13 and later |
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
| `enabled` | Boolean | Toggle | Schema gate: deprecated from firmware 2.7.0, so shown only on older firmware or when set |
| `rotary1_enabled` | Boolean | Toggle | — |
| `inputbroker_pin_a` | Integer | Numeric input (GPIO) | — |
| `inputbroker_pin_b` | Integer | Numeric input (GPIO) | — |
| `inputbroker_pin_press` | Integer | Numeric input (GPIO) | — |
| `inputbroker_event_press` | Enum | Dropdown: `InputEventChar` entries | — |
| `inputbroker_event_cw` | Enum | Dropdown: `InputEventChar` entries | — |
| `inputbroker_event_ccw` | Enum | Dropdown: `InputEventChar` entries | — |
| `updown1_enabled` | Boolean | Toggle | — |
| `allow_input_source` | String | maxSize: 63 bytes (proto max_size: 16; UI intentionally allows more) | Schema gate: deprecated from firmware 2.7.0, so shown only on older firmware or when set |
| `send_bell` | Boolean | Toggle | — |
| `messages` | String | maxSize: 200 bytes (`CannedMessageModuleConfig.messages` max_size: 201) | Pipe-delimited canned messages; saved with its own admin message |

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
| `available_pins` | Pin list | maxCount: 4 | Each pin: gpio 0–255, name maxSize 14 bytes, type `RemoteHardwarePinType` enum (excl. UNKNOWN) |

### Neighbor Info (`ModuleConfig.NeighborInfoConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `enabled` | Boolean | Toggle | — |
| `update_interval` | Integer | Numeric input (seconds) | — |
| `transmit_over_lora` | Boolean | Toggle | — |

### Ambient Lighting (`ModuleConfig.AmbientLightingConfig`)

Bounded fields use `BoundedIntEditTextPreference`: an out-of-range value is flagged and not applied.

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `led_state` | Boolean | Toggle | — |
| `current` | Integer | Range: 0–31 | From the schema's `min_value` / `max_value` |
| `red` | Integer | Range: 0–255 | From the schema's `min_value` / `max_value` |
| `green` | Integer | Range: 0–255 | From the schema's `min_value` / `max_value` |
| `blue` | Integer | Range: 0–255 | From the schema's `min_value` / `max_value` |

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
| `wifi_threshold` | Integer | Signed integer (RSSI dBm) | Firmware default: −80 |
| `ble_threshold` | Integer | Signed integer (RSSI dBm) | Firmware default: −80 |

### Status Message (`ModuleConfig.StatusMessageConfig`)

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `node_status` | String | maxSize: 80 bytes | Clearable; requires `supportsStatusMessage` capability and a node that doesn't exclude the module. Edited on the User screen, not a module screen |

### TAK (`ModuleConfig.TAKConfig`)

The screen is listed only when the firmware supports TAK configuration (`supportsTakConfig`, 2.8.0
and later) and the device role is `TAK` or `TAK_TRACKER`.

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `team` | Enum | Dropdown with custom colors/labels | — |
| `role` | Enum | Dropdown with custom labels | — |

### Mesh Beacon (`ModuleConfig.MeshBeaconConfig`)

Gated behind the `supportsMeshBeacon` capability (firmware 2.8.0 and later). The editor reads via
`AdminMessage.getModuleConfigRequest(MESHBEACON_CONFIG)` plus the LoRa config and channel 0
(the region gate and the channel pickers need both on a remote node) and writes via
`AdminMessage.setModuleConfig`. Flag edits are read-modify-write so unknown bits survive. Over
remote admin only the primary channel is read, so the pickers offer the primary only there.

The screen is disabled until the node's LoRa region is set. The region, the offered preset and the
offered frequency slot are never user-chosen: on save they are stamped from the node's own LoRa
config, and every target's region follows the node's. While the node runs custom LoRa parameters
(`use_preset = false`), the broadcast fields are read-only, Save keeps the stored broadcast fields
unchanged, and broadcast can only be turned off.

Save requires a connection and, when the node uses a preset, at least one channel and (while
broadcast is on) an interval of at least one hour.

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `flags` (`FLAG_LISTEN_ENABLED`) | Boolean | Toggle | Bit flag; listen for beacon invitations |
| `flags` (`FLAG_BROADCAST_ENABLED`) | Boolean | Toggle | Bit flag; enables interval validation below |
| `broadcast_message` | String | maxSize: 60 bytes (proto max_size: 61) | — |
| `broadcast_interval_secs` | Interval | Dropdown: `MESH_BEACON_BROADCAST` intervals | 1 hour – 72 hours. A stored value outside the list stays shown, disabled; Save is blocked below 3600 while broadcast is on |
| `broadcast_offer_channel` | Channel | Dropdown: the node's own channels | Defaults to the primary. Empty secondary slots are left out. Only the channel's name and PSK are saved; a stored channel the node no longer has stays shown, disabled |
| `broadcast_targets` | Target list | Rows added/removed via buttons; at least one row | Per row: channel (Default or one of the node's channels) and preset (Default, or a preset legal in the node's region; the conservative ≤250 kHz list without a region preset map). Picking a channel preselects the node's preset once. An empty stored list is shown as one Default row, which is what firmware sends: one beacon on the running preset over the primary channel |

---

## Channel Config

Channel editing is handled by `EditChannelDialog`.

| Field | Type | Validation | Notes |
|-------|------|------------|-------|
| `name` | String | maxSize: 11 bytes (proto max_size: 12) | Empty name falls back to modem preset name. Naming a newly added channel that still has the default key generates a random PSK, unless the PSK was edited; clearing the name again restores the default key |
| `psk` | ByteString | Must be 0, 16, or 32 bytes once the 1-byte default-key shorthand is expanded | Pre-shared key for encryption; a button generates a random key |
| `uplink_enabled` | Boolean | Toggle | — |
| `downlink_enabled` | Boolean | Toggle | — |
| `module_settings.position_precision` | Integer | `PositionPrecisionPreference` | See [UI Component Validation](#ui-component-validation) |

---

## Interval Configurations

Time-based fields use predefined interval sets from `IntervalConfiguration` (in
`feature/settings/.../util/FixedUpdateIntervals.kt`). Each configuration context restricts the
available choices to a specific subset of `FixedUpdateIntervals`.

| Configuration | Range | Values (seconds) |
|---------------|-------|------------------|
| `ALL` | 0 – 2147483647 | 0, 1, 2, 3, 4, 5, 8, 10, 15, 20, 30, 40, 45, 60, 80, 90, 120, 300, 600, 900, 1800, 3600, 7200, 10800, 14400, 18000, 21600, 43200, 64800, 86400, 129600, 172800, 259200, 2147483647 |
| `BROADCAST_SHORT` | 30 min – 72 hr | 1800, 3600, 7200, 10800, 14400, 18000, 21600, 43200, 64800, 86400, 129600, 172800, 259200 |
| `BROADCAST_MEDIUM` | 1 hr – 72 hr | 3600, 7200, 10800, 14400, 18000, 21600, 43200, 64800, 86400, 129600, 172800, 259200 |
| `BROADCAST_LONG` | 3 hr – 72 hr | 10800, 14400, 18000, 21600, 43200, 64800, 86400, 129600, 172800, 259200 |
| `MESH_BEACON_BROADCAST` | 1 hr – 72 hr | 3600, 7200, 10800, 14400, 18000, 21600, 43200, 64800, 86400, 129600, 172800, 259200 |
| `NODE_INFO_BROADCAST` | 0 (unset), 3 hr – 72 hr | 0, 10800, 14400, 18000, 21600, 43200, 64800, 86400, 129600, 172800, 259200 |
| `NAG_TIMEOUT` | 0 – 60 s | 0, 1, 5, 10, 15, 30, 60 |
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

`ExternalNotificationConfig.output_ms` is in milliseconds, so it uses its own set,
`FixedOutputDurations`: 0, 100, 500, 1000, 2000, 3000, 4000, 5000, 10000.

### Shared Constants

- **GPIO Pins:** 0–48 (inclusive), `gpioPins` in `util/SettingsIntervals.kt`
- **Hop Limits:** 0–7 (inclusive), from the `hop_limit` field metadata

---

## UI Component Validation

The reusable Compose components in `core/ui/.../component/` provide the actual enforcement
layer. Each component accepts validation parameters and rejects or flags invalid input.

### EditTextPreference

- **String mode:** Enforces `maxSize` in UTF-8 bytes via `encodeToByteArray().size <= maxSize`;
  longer input is not applied. Shows a byte counter while focused, or, when the caller sets
  `counterVisibleWithinBytes`, once the value is that close to the limit.
- **Int mode:** Unsigned; parses with `toUIntOrNull()` and flags text that does not round-trip.
- **Float and Double modes:** Parse with `NumberFormatter.parseDecimalOrNull()`, which accepts the
  international decimal separators `.`, `,`, `٫`, `、`, and `·`.

### SignedIntegerEditTextPreference

- Validates with `toIntOrNull()`; shows error icon on invalid input. Uses a signed number keyboard.

### BoundedIntEditTextPreference

- Lives in `feature/settings/.../radio/component/`. Bounds an integer by its schema field metadata
  (`FieldMetadata.intRange`); an out-of-range value is flagged and not applied. A field with no
  declared bounds accepts any value.

### DropDownPreference

- Closed-set selection only — no free-form input.
- Filters `UNRECOGNIZED` and deprecated enum entries, but keeps a deprecated value the node
  holds so the field never shows blank.

### SwitchPreference

- Binary toggle; inherently valid. Disabled state shown at reduced opacity.

### SliderPreference

- Discrete steps only; values clamped with `coerceIn()` and rounded to the nearest valid index.

### EditIPv4Preference

- Regex validation: `\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b`.
- Stored as a little-endian `Int`; displayed as dotted-decimal.

### EditPasswordPreference

- Delegates to `EditTextPreference` with `maxSize` enforcement and password visibility toggle.

### EditBase64Preference

- Validates Base64 encoding on input; text that does not decode is flagged and not applied, and
  the error icon restores the last valid value.
- Flags all-zero 32-byte values as errors (empty/unset key).

### BitwisePreference

- Closed checkbox set; toggles individual bit flags via XOR.
- "Clear" button resets to 0.

### EditListPreference

- Enforces `maxCount` for list length.
- Per-element validation varies by type: `Int` elements use the unsigned integer input,
  `ByteString` elements use `EditBase64Preference`, and a `RemoteHardwarePin` takes gpio 0–255,
  a name of at most 14 bytes, and a `RemoteHardwarePinType` other than UNKNOWN.

### PositionPrecisionPreference

- State machine with three modes: disabled (0), precision range (10–19), full precision (32).
- Slider constrained to 10–19 with 8 discrete steps.

### ConfigState

- Generic wrapper that tracks `isDirty` (current value ≠ initial value).
- Changes are only persisted on explicit save; cancel reverts to the initial snapshot.
