---
title: MQTT
parent: Kasutusjuhend
nav_order: 11
last_updated: 2026-08-30
description: Silda oma võrk internetiga – MQTT maakleri seadistamine, krüpteerimiskihid ja kaardiaruandlus.
aliases:
  - mqtt
  - internet-bridge
  - broker
---

# MQTT

MQTT ühendab Meshtastic võrgu internetiga, võimaldades raadiolevi ulatusest kaugemale ulatuvat pikamaasidet.

## Ülevaade

MQTT moodul ühendab sinu sõlme MQTT vahendajaga, võimaldades:

- Sõnumid interneti kaudu erinevate füüsiliste võrkude sõlmedeni jõudmiseks
- Integration with home automation and monitoring systems
- Sõlmede asukoha avaldamine avalikul Meshtastic kaardil
- Custom data pipelines for logging and alerting

## Kuidas see toimib

```
[Your Node] → Radio → [Gateway Node with Wi-Fi] → MQTT Broker → [Remote Gateway] → Radio → [Remote Node]
```

A gateway node with internet access (Wi-Fi or Ethernet) publishes mesh messages to an MQTT topic. Sama teemaga liitunud kauglüüsid sisestavad need sõnumid oma kohalikku kärgvõrku.

## Sätted

### Luba MQTT

1. Navigate to **Settings → Module configuration → MQTT**.
2. Luba MQTT moodul.
3. Configure the broker connection:

| Sätted                      | Kirjeldus                                                                                                                                                                         | Vaikimisi                                                               |
| --------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| **Address**                 | MQTT vahendaja hostinimi                                                                                                                                                          | mqtt.meshtastic.org                     |
| **Username**                | Broker authentication                                                                                                                                                             | meshdev                                                                 |
| **Password**                | Broker authentication                                                                                                                                                             | large4cats                                                              |
| **Root topic**              | Base topic for messages                                                                                                                                                           | `msh`, which the radio rewrites to `msh/<REGION>` once you set a region |
| **Encryption enabled**      | Krüpteeri MQTT liiklus                                                                                                                                                            | Lubatud                                                                 |
| **JSON output enabled**     | Also publish and consume the `/2/json/` topic. Deprecated in the protobuf schema, but still the only toggle for this behavior — and the app's own proxy honors it | Keelatud                                                                |
| **TLS enabled**             | Secure connection to broker                                                                                                                                                       | Keelatud                                                                |
| **Map reporting**           | Teavita asukoht avalikul kaardil                                                                                                                                                  | Keelatud                                                                |
| **Proxy to client enabled** | Relay MQTT through the connected phone                                                                                                                                            | Keelatud                                                                |

### Connection Status and Test Connection

The top of the MQTT settings screen shows the status of the relay this phone runs —
**Connected**, **Connecting**, **Reconnecting**, **Disconnected**, or **Inactive**. It reads
**Inactive** whenever the phone is not relaying, which includes the normal case of a radio
reaching the broker over its own Wi-Fi or Ethernet. The radio's own connection to the broker is
not reported here.

**Test connection** probes the broker before you commit the settings to the radio, and
distinguishes the failure modes: the hostname not resolving, the TCP connection being refused,
TLS failing, the attempt timing out, or the broker rejecting your credentials with a reason.

### MQTT puhverserver sellel telefonil

If your radio has no internet access of its own, it can use the connected phone as its MQTT gateway: enable **MQTT** and **Proxy to client enabled** in the module config, and the app relays MQTT traffic between the radio and the broker over your phone's internet connection.

> ℹ️ **Note:** The proxy relay is mobile-only. On the Desktop app the MQTT settings are present, but no relay runs behind them.

The **MQTT proxy on this phone** toggle at the top of the MQTT settings screen shows whether this relay is running and lets you cut it off (or restart it) immediately — without editing and re-saving the radio's MQTT configuration.

### Meshtastic vaikemaakler

Kogukond haldab avaliku vahendajat aadressil `mqtt.meshtastic.org`. This is intended for general use and testing.

When this phone relays MQTT for the radio, connections to that broker always use TLS on port 8883 even if **TLS enabled** is off — the app forces the switch on and grays it out. A radio that reaches the broker over its own Wi-Fi or Ethernet forces nothing: turn **TLS enabled** on yourself, or it connects in the clear on port 1883. For any other broker the toggle decides in both cases (port 8883 with TLS, 1883 without).

> 🔒 **Privaatsus:** Avaliku vahendaja sõnumeid saavad lugeda kõik tellijad. Privaatse suhtluse jaoks kasuta alati kanali krüpteerimist.

### Private Broker

Parema privaatsuse ja kontrolli tagamiseks saad hallata oma MQTT maaklerit:

- Mosquitto (kerge, avatud lähtekoodiga)
- HiveMQ
- EMQX

Konfigureeri oma sõlm nii, et see osutaks sinu privaatsele maaklerile sobivate volitustega.

## Kaardiaruannete koostamine

When **Map reporting** is on, your node periodically publishes a map report to the broker. The report goes out unencrypted, whatever keys your channels use, and carries your node id, long and short name, approximate location, hardware model, role, firmware version, LoRa region, modem preset, and primary channel name.

Turning it on opens a consent card. Turn on **I agree.** and choose a **Map reporting interval (seconds)** of one hour or more — the screen will not save until you do. A slider sets the position precision, and the app shows the resulting accuracy as a ± distance, so you can publish an approximate location rather than an exact one.

Reports appear at [meshmap.net](https://meshmap.net) and similar community map services.

> 🔒 **Privacy:** A map report is readable by anyone subscribed to the broker. Leave **Map reporting** off if you do not want your approximate location published.

## Üleslink vs allalink

| Suund        | Kirjeldus                          |
| ------------ | ---------------------------------- |
| **Üleslink** | Sõnumid kärgvõrgust → MQTT maakler |
| **Allalink** | Sõnumid MQTT maaklerist → kärgvõrk |

Uplink and downlink are per-channel settings, not MQTT module settings. Open **Settings → Channels**, tap the channel, and use **MQTT Uplink Enabled** and **MQTT Downlink Enabled**. Every channel you want bridged out needs uplink on, and every channel you want MQTT traffic injected into needs downlink on.

## Sõnumivormingud

MQTT carries two payload formats:

| Vorming      | Kirjeldus                                   | Kasutusjuhtum                                                               |
| ------------ | ------------------------------------------- | --------------------------------------------------------------------------- |
| **Protobuf** | Binaarne Meshtastic protobuf kodeering      | Node-to-node mesh bridging                                                  |
| **JSON**     | Human-readable JSON on the `/2/json/` topic | Consumers outside the mesh (dashboards, home automation) |

> ℹ️ **Note:** `json_enabled` is marked deprecated in the protobuf schema, but it has not been
> replaced and it is not ignored. When it is on, the app's own MQTT proxy subscribes to the
> `/2/json/` topic and decodes those payloads.

## Encryption & Privacy

Understanding the layered encryption model:

1. **Kanali krüptimine** toimub kärgvõrgus _enne_ MQTT. Kui kanalil on PSK, on ​​MQTT liiklus juba krüptitud – maakler ja kõik tellijad näevad ainult šifriteksti.
2. **Encryption enabled** (the module setting) decides which copy of the packet the gateway publishes — it is not an extra layer. Leave it on and the broker receives the packet still encrypted with your channel key. Turn it off and the gateway publishes the decrypted packet, so anyone subscribed to the topic reads your messages in the clear. Turn it off only when you own the broker and want plain payloads for a dashboard.
3. **TLS** krüpteerib TCP ühenduse vahendaja endaga, takistades võrgutasandil pealtkuulamist.

> 🔒 **Security:** The default public channel has a well-known key. MQTT kaudu saadetud vaikekanalil olevad sõnumid on sisuliselt **krüpteerimata** – igaüks saab neid dekodeerida. Always use a custom PSK for private communications.

## Parimad tavad

- Kasuta kanali krüptimist (PSK), kanalitel mis on sillatud MQTT-ga
- Don't enable MQTT on nodes without internet access (the radio buffers unsendable messages and wastes memory)
- Use a private broker for sensitive deployments
- MQTT sõnumite allalaadimisel arvesta eetriaja kuluga – iga allalingitud sõnum tarbib sinu kohalikus võrgus raadioeetriaega
- Kaalu ainult üleslingi lubamist, kui sul on vaja oma kärgvõrku eemalt jälgida ilma sõnumeid tagasi tõmbamata

## Veaotsing

### MQTT ei ühendu

- **Check Wi-Fi** — the gateway node must have an active internet connection (Wi-Fi or Ethernet). MQTT ei tööta LoRa raadiolingi enda kaudu.
- **Verify credentials** — with incorrect credentials, most brokers fail silently — double-check for trailing spaces.
- **Firewall** — port 1883 (MQTT) or 8883 (MQTT over TLS) must be reachable. Some networks allow only web traffic (ports 80 and 443).
- **DNS-i lahendamine** – kui kasutad kohandatud maakleri hostinime, veenduge, et sõlm suudab seda lahendada. Try the broker's IP address directly.

### Messages Not Bridging

- **Kontrolli üleslingi/allalingi seadeid** — kui lubatud on ainult üleslink, liiguvad sõnumid võrgust MQTT-sse, aga mitte tagasi. Luba vastuvõtval lüüsil allalink.
- **Kanali mittevastavus** – mõlemad lüüsid peavad jagama sama kanalit sama PSK-ga. Vastuolu tähendab, et sõnumid on krüpteeritud erinevate võtmetega ja kuvatakse prügina.
- **Topic mismatch** — both gateways must use exactly the same root topic. Setting a region rewrites a default root to `msh/<REGION>` (for example `msh/US`), so gateways in different regions do not meet until you give both the same explicit root.
- **Ignore MQTT is on** — in a region with a duty-cycle limit, the radio turns on **Ignore MQTT** (LoRa config, **Advanced**) when you set the region, and then drops every packet that reached it via MQTT. Turn it off on the receiving nodes, not only on the gateway.
- **Ok to MQTT is off** — on a public broker a gateway uplinks other nodes' packets only when the sending node has **Ok to MQTT** (LoRa config, **Advanced**) on. Your own traffic bridges either way; your neighbors' does not until they opt in.

## Seotud teemad

- [Seaded — Moodulid ja administreerimine](settings-module-admin) — MQTT mooduli konfi viide
- [Sõnumid ja kanalid](messages-and-channels) — kanali krüptimine ja PSK seadistamine
- [MQTT integratsioonijuhend](https://meshtastic.org/docs/software/integrations/mqtt) — üksikasjalik MQTT dokumentatsioon aadressil meshtastic.org
