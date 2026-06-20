---
title: MQTT
parent: User Guide
nav_order: 11
last_updated: 2026-05-13
description: Silda oma võrk internetiga – MQTT maakleri seadistamine, krüpteerimiskihid ja kaardiaruandlus.
aliases:
  - mqtt
  - internet-bridge
  - broker
---

# MQTT

MQTT ühendab teie Meshtastic võrgu internetiga, võimaldades raadiolevi ulatusest kaugemale ulatuvat pikamaasidet.

## Overview

MQTT moodul ühendab teie sõlme MQTT vahendajaga, võimaldades:

- Messages to reach nodes on different physical meshes via the internet
- Integration with home automation and monitoring systems
- Publishing node positions to the public Meshtastic map
- Custom data pipelines for logging and alerting

## How It Works

```
[Teie sõlm] → Raadio → [WiFi-ga lüüsisõlm] → MQTT vahendaja → [Kauglüüs] → Raadio → [Kaugsõlm]
```

Internetiühendusega (WiFi või Ethernet) lüüsisõlm jagab võrgusõnumeid MQTT. Remote gateways subscribed to the same topic inject those messages into their local mesh.

## Sätted

### Luba MQTT

1. Mine menüüsse **Seaded → Mooduli konfiguratsioon → MQTT**.
2. Luba MQTT moodul.
3. Configure the broker connection:

![MQTT lüliti](/assets/screenshots/settings_switch.png)

| Sätted           | Kirjeldus                                                                                     | Vaikimisi                                           |
| ---------------- | --------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| Server Address   | MQTT vahendaja hostinimi                                                                      | mqtt.meshtastic.org |
| Kasutajatunnus   | Broker authentication                                                                         | meshdev                                             |
| Parool           | Broker authentication                                                                         | large4cats                                          |
| Root Topic       | Base topic for messages                                                                       | msh                                                 |
| Encryption       | Krüpteeri MQTT liiklus                                                                        | Lubatud                                             |
| ~~JSON väljund~~ | ⚠️ **Deprecated** — JSON packet support has been removed from firmware; this field is ignored | Keelatud                                            |
| TLS              | Secure connection to broker                                                                   | Keelatud                                            |
| Map Reporting    | Report position to public map                                                                 | Keelatud                                            |

### Default Meshtastic Broker

Kogukond haldab avaliku vahendajat aadressil `mqtt.meshtastic.org`. This is intended for general use and testing.

> 🔒 **Privacy:** Messages on the public broker are readable by anyone subscribed. Always use channel encryption for private communications.

### Private Broker

Parema privaatsuse ja kontrolli tagamiseks saad hallata oma MQTT maaklerit:

- Mosquitto (lightweight, open-source)
- HiveMQ
- EMQX

Configure your node to point to your private broker with appropriate credentials.

## Map Reporting

When Map Reporting is enabled, your node publishes its position to the Meshtastic community map:

- Visible at [meshmap.net](https://meshmap.net) and similar community map services
- Only position and node info are shared
- Disable this if you don't want your location publicly visible

## Üleslink vs allalink

| Direction    | Kirjeldus                          |
| ------------ | ---------------------------------- |
| **Üleslink** | Sõnumid kärgvõrgust → MQTT maakler |
| **Allalink** | Sõnumid MQTT maaklerist → kärgvõrk |

Configure per-channel which directions are active to control message flow and airtime usage.

## Message Formats

MQTT kasutab protobuf-sõnumivormingut:

| Format       | Kirjeldus                           | Use case                   |
| ------------ | ----------------------------------- | -------------------------- |
| **Protobuf** | Binary Meshtastic protobuf encoding | Node-to-node mesh bridging |

> ⚠️ **Note:** JSON output support was removed from firmware. The `json_enabled` setting is still visible in the app for legacy compatibility but has no effect on current firmware versions.

## Encryption & Privacy

Understanding the layered encryption model:

1. **Kanali krüptimine** toimub kärgvõrgus _enne_ MQTT. Kui teie kanalil on PSK, on ​​MQTT liiklus juba krüptitud – maakler ja kõik tellijad näevad ainult šifriteksti.
2. **MQTT krüptimine** (mooduli säte) lisab vahendajale edastamiseks täiendava krüptimiskihi. This protects metadata and routing information.
3. **TLS** encrypts the TCP connection to the broker itself, preventing network-level eavesdropping.

> 🔒 **Important:** The default public channel has a well-known key. MQTT kaudu saadetud vaikekanalil olevad sõnumid on sisuliselt **krüpteerimata** – igaüks saab neid dekodeerida. Always use a custom PSK for private communications.

## Best Practices

- Kasuta kanali krüptimist (PSK), kanalitel mis on sillatud MQTT-ga
- Ära luba MQTT internetiühenduseta sõlmedel (see puhverdab ja raiskab mälu)
- Use a private broker for sensitive deployments
- MQTT sõnumite allalaadimisel arvesta eetriaja kuluga – iga allalingitud sõnum tarbib sinu kohalikus võrgus raadioeetriaega
- Kaalu ainult üleslingi lubamist, kui sul on vaja oma kärgvõrku eemalt jälgida ilma sõnumeid tagasi tõmbamata

## Troubleshooting

### MQTT ei ühendu

- **Check WiFi** — the gateway node must have an active internet connection (WiFi or Ethernet). MQTT ei tööta LoRa raadiolingi enda kaudu.
- **Verify credentials** — incorrect username or password will silently fail on most brokers. Double-check for trailing spaces.
- **Tulemüür** — port 1883 (MQTT) või 8883 (MQTT+TLS) peab olema avatud. Some networks block non-standard ports.
- **DNS resolution** — if using a custom broker hostname, verify the node can resolve it. Try the broker's IP address directly.

### Messages Not Bridging

- **Kontrolli üleslingi/allalingi seadeid** — kui lubatud on ainult üleslink, liiguvad sõnumid võrgust MQTT-sse, aga mitte tagasi. Luba vastuvõtval lüüsil allalink.
- **Channel mismatch** — both gateways must share the same channel with the same PSK. A mismatch means messages are encrypted with different keys and appear as garbage.
- **Topic mismatch** — ensure both gateways use the same root topic. The default `msh` works for the public broker.

## Related Topics

- [Seaded — Moodulid ja administreerimine](settings-module-admin) — MQTT mooduli konfi viide
- [Messages & Channels](messages-and-channels) — channel encryption and PSK setup
- [MQTT integratsioonijuhend](https://meshtastic.org/docs/software/integrations/mqtt) — üksikasjalik MQTT dokumentatsioon aadressil meshtastic.org

---

