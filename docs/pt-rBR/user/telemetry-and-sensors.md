---
title: Telemetry & Sensors
parent: User Guide
nav_order: 9
last_updated: 2026-05-13
description: Sensor data on the mesh — supported environment, air quality, and power sensors, plus configuration and viewing guides.
aliases:
  - sensors
  - environment
  - weather
  - power-metrics
---

# Telemetry & Sensors

Meshtastic nodes can collect and share sensor data across the mesh network.

## Overview

Telemetry allows nodes equipped with sensors to broadcast environmental, power, and device health information. This data is visible on the node detail screen and can be logged over time.

## Device Telemetry

All Meshtastic nodes report basic device telemetry:

| Metric              | Descrição                      | Typical Range                                                      |
| ------------------- | ------------------------------ | ------------------------------------------------------------------ |
| Battery Level       | Charge percentage              | 0–100%                                                             |
| Voltagem            | Battery voltage                | 3.0–4.2V (LiPo) |
| Utilização do Canal | % of airtime used locally      | 0–100%                                                             |
| Air Utilization TX  | % of airtime used by this node | 0–100%                                                             |
| Uptime              | Seconds since last boot        | Varies                                                             |

## Environment Sensors

Supported environmental sensors:

### Temperature & Humidity

| Sensor  | Temperatura | Umidade | Pressão | Notes                   |
| ------- | ----------- | ------- | ------- | ----------------------- |
| BME280  | ✓           | ✓       | ✓       | Recommended all-in-one  |
| BME680  | ✓           | ✓       | ✓       | Adds gas resistance/IAQ |
| SHT31   | ✓           | ✓       | —       | High accuracy           |
| MCP9808 | ✓           | —       | —       | Precision temperature   |
| LPS22   | —           | —       | ✓       | Pressure only           |

### Air Quality

| Sensor   | Metric                                             | Notes                      |
| -------- | -------------------------------------------------- | -------------------------- |
| BME680   | Gas Resistance / IAQ                               | Volatile organic compounds |
| PMSA003I | PM1.0, PM2.5, PM10 | Particulate matter         |
| SEN55    | PM, NOx, VOC, Temp, Humidity                       | Multi-sensor               |

### Light & UV

| Sensor   | Metric                                 |
| -------- | -------------------------------------- |
| OPT3001  | Ambient light (lux) |
| VEML7700 | Ambient light (lux) |
| LTR390   | UV index                               |

## Power Metrics

Nodes with INA-series power sensors can report:

| Metric      | Descrição                                 |
| ----------- | ----------------------------------------- |
| Bus Voltage | Supply rail voltage                       |
| Atual       | Power consumption (mA) |
| Energia     | Calculated power (mW)  |

Useful for monitoring solar charging or battery health on remote nodes.

## Configuring Telemetry

1. Navigate to **Settings → Module Config → Telemetry**.
2. Set reporting intervals:
   - **Device Metrics Interval** — how often to broadcast device metrics
   - **Environment Metrics Interval** — how often to broadcast sensor data
3. Enable specific sensor types as needed.

### Recommended Intervals

| Use Case                                   | Device (s) | Environment (s) |
| ------------------------------------------ | ----------------------------- | ---------------------------------- |
| Urban mesh (many nodes) | 3600                          | 3600                               |
| Rural mesh (few nodes)  | 900                           | 900                                |
| Weather station                            | 900                           | 300                                |
| Battery conservation                       | 7200                          | 7200                               |

> ⚠️ **Note:** Shorter intervals increase airtime usage and battery drain across the mesh.

## Viewing Telemetry

1. Navigate to **Nodes** and select a node.
2. Telemetry sections show on the detail screen:
   - Device Metrics (always available)
   - Environment Metrics (if sensors present)
   - Power Metrics (if INA sensor present)
3. Historical graphs show trends over time.

![Telemetry actions](../../assets/screenshots/node-metrics_telemetric_actions.png)

## Troubleshooting

- **No environment data showing?** The remote node needs a physical sensor connected (e.g., BME280 on I2C). Device telemetry (battery, uptime) is always available, but environment metrics require hardware.
- **Stale readings?** Check the reporting interval — very long intervals (7200s+) mean data updates infrequently. Also verify the remote node is still online.
- **Sensor conflict on I2C bus?** Some sensors share I2C addresses. If you have multiple sensors on the same bus, check for address collisions in the radio's serial debug output.

## Related Topics

- [Node Metrics](node-metrics) — view telemetry data on the node detail screen
- [Settings — Modules & Admin](settings-module-admin) — telemetry module configuration
- [Units & Locale](units-and-locale) — temperature and pressure display units

---

