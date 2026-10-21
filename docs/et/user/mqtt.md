---
title: MQTT
nav_order: 11
last_updated: 2026-10-21
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

```text
[Your Node] → Radio → [Gateway Node with Wi-Fi] → MQTT Broker → [Remote Gateway] → Radio → [Remote Node]
```

A gateway node with internet access (Wi-Fi or Ethernet) publishes mesh messages to an MQTT topic. Sama teemaga liitunud kauglüüsid sisestavad need sõnumid oma kohalikku kärgvõrku.

## Sätted

### Luba MQTT

1. Navigate to **Settings → Module configuration → MQTT**.
2. Turn on **MQTT Enabled**.
3. Configure the broker connection:

| Sätted                  | Kirjeldus                                                                                                                                                   | Vaikimisi                                                                                              |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| **Address**             | MQTT broker hostname. Empty means the public broker                                                                                         | mqtt.meshtastic.org                                                    |
| **Username**            | Broker authentication                                                                                                                                       | meshdev                                                                                                |
| **Password**            | Broker authentication                                                                                                                                       | large4cats                                                                                             |
| **Encryption Enabled**  | Publish packets still encrypted with the channel key                                                                                                        | Lubatud                                                                                                |
| **JSON output enabled** | Shown only for a node on firmware older than 2.8, or while the setting is still on. See [Message Formats](#message-formats) | Keelatud                                                                                               |
| **TLS Enabled**         | Secure connection to broker                                                                                                                                 | Keelatud                                                                                               |
| **Root Topic**          | Base topic for messages                                                                                                                                     | `msh`, which the node rewrites to `msh/<REGION>` when you set a region while it uses the public broker |
| **MQTT Client Proxy**   | Relay MQTT through the connected app                                                                                                                        | Keelatud                                                                                               |
| **Map Reporting**       | Teavita asukoht avalikul kaardil                                                                                                                            | Keelatud                                                                                               |

### Connection Status and Test Connection

The top of the MQTT settings screen shows the status of the relay this app runs:
**Connected**, **Connecting…**, **Reconnecting…**, **Disconnected**, or **Inactive**. It reads
**Inactive** whenever the app is not relaying, which includes the normal case of a node
reaching the broker over its own Wi-Fi or Ethernet. The node's own connection to the broker is
not reported here.

When the broker accepts the connection but refuses a subscription, the status names the refused
topics and the broker's reason: _Connected, but the broker refused every topic_ when nothing was
granted, or _Connected, but the broker refused_ followed by the topics when only some were. A
broker's access-control list is the usual cause.

**Test connection** probes the broker before you commit the settings to the node, and
distinguishes the failure modes: the hostname not resolving, the TCP connection being refused,
TLS failing, the attempt timing out, or the broker rejecting your credentials with a reason.

### MQTT Proxy in This App

If your node has no internet access of its own, it can use the app as its MQTT gateway. Turn on **MQTT Enabled** and **MQTT Client Proxy** in the module config. The app then relays MQTT traffic between the node and the broker over your phone's or computer's internet connection.

The **MQTT proxy in this app** toggle at the top of the MQTT settings screen shows whether this relay is running and lets you cut it off (or restart it) immediately, without editing and re-saving the node's MQTT configuration.

### Meshtastic vaikemaakler

Kogukond haldab avaliku vahendajat aadressil `mqtt.meshtastic.org`. This is intended for general use and testing.

When this app relays MQTT for the node, connections to that broker always use TLS on port 8883 even if **TLS Enabled** is off. That upgrade is the app's own, and the switch says so. A node that reaches the broker over its own Wi-Fi or Ethernet forces nothing: it uses **TLS Enabled** as stored, so turn it on yourself or the node connects in the clear on port 1883. For any other broker the toggle decides in both cases. A port written in the address, as in `broker.example.com:8884`, is kept; without one, TLS uses port 8883 and plain connections use 1883.

> 🔒 **Privaatsus:** Avaliku vahendaja sõnumeid saavad lugeda kõik tellijad. Privaatse suhtluse jaoks kasuta alati kanali krüpteerimist.

### Private Broker

Parema privaatsuse ja kontrolli tagamiseks saad hallata oma MQTT maaklerit:

- Mosquitto (kerge, avatud lähtekoodiga)
- HiveMQ
- EMQX

Konfigureeri oma sõlm nii, et see osutaks sinu privaatsele maaklerile sobivate volitustega.

## Kaardiaruannete koostamine

When **Map Reporting** is on, your node periodically publishes a map report to the broker. The report goes out unencrypted, whatever keys your channels use, and carries your node id, long and short name, approximate location, hardware model, role, firmware version, LoRa region, modem preset, and primary channel name.

Turning it on opens a consent card. Turn on **I agree.** and choose a **Map Publish Interval** of one hour or more — the screen will not save until you do. A slider sets the position precision, and the app shows the resulting accuracy as a ± distance, so you can publish an approximate location rather than an exact one.

Reports appear at [meshmap.net](https://meshmap.net) and similar community map services.

> 🔒 **Privacy:** A map report is readable by anyone subscribed to the broker. Leave **Map Reporting** off if you do not want your approximate location published.

## Üleslink vs allalink

| Suund        | Kirjeldus                          |
| ------------ | ---------------------------------- |
| **Üleslink** | Sõnumid kärgvõrgust → MQTT maakler |
| **Allalink** | Sõnumid MQTT maaklerist → kärgvõrk |

Uplink and downlink are per-channel settings, not MQTT module settings. Open **Settings → Channels**, tap the channel, and use **MQTT Uplink Enabled** and **MQTT Downlink Enabled**. Every channel you want bridged out needs uplink on, and every channel you want MQTT traffic injected into needs downlink on.

## Sõnumivormingud

Gateways publish packets as binary Meshtastic protobufs on the `/2/e/` topic, which is what node-to-node mesh bridging uses.

Firmware 2.7 and older can also publish human-readable JSON on the `/2/json/` topic for consumers outside the mesh, such as dashboards and home automation, when `json_enabled` is on. Firmware 2.8 removed JSON support and ignores the field. The app shows **JSON output enabled** only for a node on older firmware, or while the setting is still on, and while it is on the app's own MQTT proxy also subscribes to the `/2/json/` topic and decodes those payloads.

## Encryption & Privacy

Understanding the layered encryption model:

1. **Kanali krüptimine** toimub kärgvõrgus _enne_ MQTT. Kui kanalil on PSK, on ​​MQTT liiklus juba krüptitud – maakler ja kõik tellijad näevad ainult šifriteksti.
2. **Encryption Enabled** (the module setting) decides which copy of the packet the gateway publishes — it is not an extra layer. Leave it on and the broker receives the packet still encrypted with your channel key. Turn it off and the gateway publishes the decrypted packet, so anyone subscribed to the topic reads your messages in the clear. Turn it off only when you own the broker and want plain payloads for a dashboard.
3. **TLS** krüpteerib TCP ühenduse vahendaja endaga, takistades võrgutasandil pealtkuulamist.

> 🔒 **Security:** The default public channel has a well-known key. MQTT kaudu saadetud vaikekanalil olevad sõnumid on sisuliselt **krüpteerimata** – igaüks saab neid dekodeerida. Always use a custom PSK for private communications.

## Parimad tavad

- Kasuta kanali krüptimist (PSK), kanalitel mis on sillatud MQTT-ga
- Don't enable MQTT on a node with no internet access of its own unless **MQTT Client Proxy** is on (the node queues messages it can't send and wastes memory)
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
- **Topic mismatch** — both gateways must use exactly the same root topic. On the public broker, setting a region rewrites a default root to `msh/<REGION>` (for example `msh/US`), so gateways in different regions do not meet until you give both the same explicit root. Firmware 2.8.0 and older also rewrite a root starting with `msh` on a private broker.
- **Ignore MQTT is on** — in a region with a duty-cycle limit, the node turns on **Ignore MQTT** (LoRa config, **Advanced**) when you set the region, and then drops every packet that reached it via MQTT. Turn it off on the receiving nodes, not only on the gateway.
- **OK to MQTT is off** — on a public broker a gateway uplinks other nodes' packets only when the sending node has **OK to MQTT** (LoRa config, **Advanced**) on. Your own traffic bridges either way; your neighbors' does not until they opt in.

## Seotud teemad

- [Seaded — Moodulid ja administreerimine](settings-module-admin) — MQTT mooduli konfi viide
- [Sõnumid ja kanalid](messages-and-channels) — kanali krüptimine ja PSK seadistamine
- [MQTT integratsioonijuhend](https://meshtastic.org/docs/software/integrations/mqtt) — üksikasjalik MQTT dokumentatsioon aadressil meshtastic.org
