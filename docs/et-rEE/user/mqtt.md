---
title: MQTT
parent: Kasutusjuhend
nav_order: 11
last_updated: 2026-08-29
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

1. Mine menüüsse **Seaded → Mooduli konfiguratsioon → MQTT**.
2. Luba MQTT moodul.
3. Configure the broker connection:

![MQTT module settings with the module enabled](../../assets/screenshots/settings_switch.png)

| Sätted                     | Kirjeldus                                                                                                                                                                         | Vaikimisi                                           |
| -------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| Server Address             | MQTT vahendaja hostinimi                                                                                                                                                          | mqtt.meshtastic.org |
| Kasutajatunnus             | Broker authentication                                                                                                                                                             | meshdev                                             |
| Parool                     | Broker authentication                                                                                                                                                             | large4cats                                          |
| Root Topic                 | Base topic for messages                                                                                                                                                           | msh                                                 |
| Encryption                 | Krüpteeri MQTT liiklus                                                                                                                                                            | Lubatud                                             |
| JSON Output                | Also publish and consume the `/2/json/` topic. Deprecated in the protobuf schema, but still the only toggle for this behavior — and the app's own proxy honors it | Keelatud                                            |
| TLS                        | Secure connection to broker                                                                                                                                                       | Keelatud                                            |
| Kaardiaruannete koostamine | Teavita asukoht avalikul kaardil                                                                                                                                                  | Keelatud                                            |

### Connection Status and Test Connection

The top of the MQTT settings screen shows the live broker connection — **Connected**,
**Connecting**, **Reconnecting**, **Disconnected**, or **Inactive**.

**Test connection** probes the broker before you commit the settings to the radio, and
distinguishes the failure modes: the hostname not resolving, the TCP connection being refused,
TLS failing, the attempt timing out, or the broker rejecting your credentials with a reason.

### MQTT puhverserver sellel telefonil

If your radio has no internet access of its own, it can use the connected phone as its MQTT gateway: enable **MQTT** and **Proxy to client enabled** in the module config, and the app relays MQTT traffic between the radio and the broker over your phone's internet connection.

> ℹ️ **Note:** The proxy relay is mobile-only. On the Desktop app the MQTT settings are present, but no relay runs behind them.

The **MQTT proxy on this phone** toggle at the top of the MQTT settings screen shows whether this relay is running and lets you cut it off (or restart it) immediately — without editing and re-saving the radio's MQTT configuration.

### Meshtastic vaikemaakler

Kogukond haldab avaliku vahendajat aadressil `mqtt.meshtastic.org`. This is intended for general use and testing. Connections to it always use TLS (port 8883), even if the TLS toggle is off; for any other broker, TLS is used only when you enable it (port 8883 with TLS, 1883 without).

> 🔒 **Privaatsus:** Avaliku vahendaja sõnumeid saavad lugeda kõik tellijad. Privaatse suhtluse jaoks kasuta alati kanali krüpteerimist.

### Private Broker

Parema privaatsuse ja kontrolli tagamiseks saad hallata oma MQTT maaklerit:

- Mosquitto (kerge, avatud lähtekoodiga)
- HiveMQ
- EMQX

Konfigureeri oma sõlm nii, et see osutaks sinu privaatsele maaklerile sobivate volitustega.

## Kaardiaruannete koostamine

Kui kaardiaruandlus on lubatud, avaldab sõlm oma asukoha Meshtasticu kogukonnakaardil:

- Nähtav aadressil [meshmap.net](https://meshmap.net) ja sarnastes kogukonna kaarditeenustes
- Only position and node info are shared
- Keela see valik, kui sa ei soovi, et sinu asukoht oleks avalikult nähtav

## Üleslink vs allalink

| Suund        | Kirjeldus                          |
| ------------ | ---------------------------------- |
| **Üleslink** | Sõnumid kärgvõrgust → MQTT maakler |
| **Allalink** | Sõnumid MQTT maaklerist → kärgvõrk |

Konfi iga kanali kohta, millised suunad on aktiivsed, et kontrollida sõnumivoogu ja eetriaega.

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
2. **MQTT krüptimine** (mooduli säte) lisab vahendajale edastamiseks täiendava krüptimiskihi. This protects metadata and routing information.
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
- **Teema mittevastavus** — veendu, et mõlemad lüüsid kasutaksid sama juurteemat. The default `msh` works for the public broker.

## Seotud teemad

- [Seaded — Moodulid ja administreerimine](settings-module-admin) — MQTT mooduli konfi viide
- [Sõnumid ja kanalid](messages-and-channels) — kanali krüptimine ja PSK seadistamine
- [MQTT integratsioonijuhend](https://meshtastic.org/docs/software/integrations/mqtt) — üksikasjalik MQTT dokumentatsioon aadressil meshtastic.org
