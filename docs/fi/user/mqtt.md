---
title: MQTT
nav_order: 11
last_updated: 2026-10-21
description: Siltaa mesh-verkko internetiin — MQTT-välityspalvelimen käyttöönotto, salauskerrokset ja karttadatan välitys.
aliases:
  - mqtt
  - internet-silta
  - välityspalvelin
---

# MQTT

MQTT yhdistää Meshtastic-mesh-verkkosi internetiin ja mahdollistaa pitkän kantaman viestinnän radioalueen ulkopuolella.

## Yleiskatsaus

MQTT-moduuli yhdistää radion MQTT-välityspalvelimeen, mahdollistaen:

- Viestien välittymisen eri fyysisten mesh-verkkojen välillä internetin kautta
- Integraation kotiautomaatio- ja valvontajärjestelmiin
- Radioiden sijaintien julkaisemisen julkiseen Meshtastic-karttaan
- Mukautetut dataputket tiedonkeruuta ja hälytyksiä varten

## Kuinka se toimii

```text
[Oma radio] → Radio → [Yhdyskäytäväradio, jossa WiFi] → MQTT-välityspalvelin → [Etäyhdyskäytävä] → Radio → [Etäradio]
```

Internet-yhteydellä varustettu yhdyskäytäväradio (WiFi tai Ethernet) julkaisee mesh-verkon viestejä MQTT-aiheeseen. Etä-yhdyskäytävät, jotka ovat tilanneet saman aiheen, syöttävät viestit omaan paikalliseen mesh-verkkoonsa.

## Asetukset

### MQTT:n käyttöönotto

1. Navigate to **Settings → Module configuration → MQTT**.
2. Turn on **MQTT Enabled**.
3. Määritä välityspalvelimen yhteys:

| Asetus                  | Kuvaus                                                                                                                                                      | Oletus                                                                                                 |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| **Address**             | MQTT broker hostname. Empty means the public broker                                                                                         | mqtt.meshtastic.org                                                    |
| **Username**            | Välityspalvelimen tunnistautuminen                                                                                                                          | meshdev                                                                                                |
| **Password**            | Välityspalvelimen tunnistautuminen                                                                                                                          | large4cats                                                                                             |
| **Encryption Enabled**  | Publish packets still encrypted with the channel key                                                                                                        | Käytössä                                                                                               |
| **JSON output enabled** | Shown only for a node on firmware older than 2.8, or while the setting is still on. See [Message Formats](#message-formats) | Ei käytössä                                                                                            |
| **TLS Enabled**         | Yhteyden suojaaminen välityspalvelimeen                                                                                                                     | Ei käytössä                                                                                            |
| **Root Topic**          | Viestien perusaihe                                                                                                                                          | `msh`, which the node rewrites to `msh/<REGION>` when you set a region while it uses the public broker |
| **MQTT Client Proxy**   | Relay MQTT through the connected app                                                                                                                        | Ei käytössä                                                                                            |
| **Map Reporting**       | Sijainnin julkaisu julkiselle kartalle                                                                                                                      | Ei käytössä                                                                                            |

### Yhteyden tila ja testaa yhteys

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

### Oletus Meshtastic-välityspalvelin

Yhteisö ylläpitää julkista välityspalvelinta osoitteessa `mqtt.meshtastic.org`. Tämä on tarkoitettu yleiseen käyttöön ja testaukseen.

When this app relays MQTT for the node, connections to that broker always use TLS on port 8883 even if **TLS Enabled** is off. That upgrade is the app's own, and the switch says so. A node that reaches the broker over its own Wi-Fi or Ethernet forces nothing: it uses **TLS Enabled** as stored, so turn it on yourself or the node connects in the clear on port 1883. For any other broker the toggle decides in both cases. A port written in the address, as in `broker.example.com:8884`, is kept; without one, TLS uses port 8883 and plain connections use 1883.

> 🔒 **Tietosuoja:** Julkisen välityspalvelimen viestit ovat kaikkien tilaajien luettavissa. Käytä aina kanavasalausta yksityiseen viestintään.

### Oma välityspalvelin

Parempaa yksityisyyttä ja hallintaa varten voit käyttää omaa MQTT-välityspalvelinta:

- Mosquitto (kevyt, avoimen lähdekoodin)
- HiveMQ
- EMQX

Määritä radiosi osoittamaan omaan välityspalvelimeesi oikeilla tunnistetiedoilla.

## Karttaraportointi

When **Map Reporting** is on, your node periodically publishes a map report to the broker. The report goes out unencrypted, whatever keys your channels use, and carries your node id, long and short name, approximate location, hardware model, role, firmware version, LoRa region, modem preset, and primary channel name.

Turning it on opens a consent card. Turn on **I agree.** and choose a **Map Publish Interval** of one hour or more — the screen will not save until you do. A slider sets the position precision, and the app shows the resulting accuracy as a ± distance, so you can publish an approximate location rather than an exact one.

Reports appear at [meshmap.net](https://meshmap.net) and similar community map services.

> 🔒 **Privacy:** A map report is readable by anyone subscribed to the broker. Leave **Map Reporting** off if you do not want your approximate location published.

## Lähetys vs vastaanotto

| Suunta          | Kuvaus                                           |
| --------------- | ------------------------------------------------ |
| **Lähetys**     | Viestit mesh-verkosta → MQTT-välityspalvelimeen  |
| **Vastaanotto** | Viestit MQTT-välityspalvelimesta → mesh-verkkoon |

Uplink and downlink are per-channel settings, not MQTT module settings. Open **Settings → Channels**, tap the channel, and use **MQTT Uplink Enabled** and **MQTT Downlink Enabled**. Every channel you want bridged out needs uplink on, and every channel you want MQTT traffic injected into needs downlink on.

## Viestiformaatit

Gateways publish packets as binary Meshtastic protobufs on the `/2/e/` topic, which is what node-to-node mesh bridging uses.

Firmware 2.7 and older can also publish human-readable JSON on the `/2/json/` topic for consumers outside the mesh, such as dashboards and home automation, when `json_enabled` is on. Firmware 2.8 removed JSON support and ignores the field. The app shows **JSON output enabled** only for a node on older firmware, or while the setting is still on, and while it is on the app's own MQTT proxy also subscribes to the `/2/json/` topic and decodes those payloads.

## Salaus ja yksityisyys

Kerrostetun salausmallin ymmärtäminen:

1. **Kanavasalaus** tapahtuu meshissä _ennen_ MQTT:tä. Jos kanavallasi on PSK, MQTT-viestisisältö on jo salattu — välityspalvelin ja tilaajat näkevät vain salatun datan.
2. **Encryption Enabled** (the module setting) decides which copy of the packet the gateway publishes — it is not an extra layer. Leave it on and the broker receives the packet still encrypted with your channel key. Turn it off and the gateway publishes the decrypted packet, so anyone subscribed to the topic reads your messages in the clear. Turn it off only when you own the broker and want plain payloads for a dashboard.
3. **TLS** salaa TCP-yhteyden itse välityspalvelimeen estäen verkkotason salakuuntelun.

> 🔒 **Tietoturva:** Oletusjulkisella kanavalla on tunnettu avain. Oletuskanavan MQTT:n kautta lähetetyt viestit ovat käytännössä **salaamattomia** — kuka tahansa tilaaja voi purkaa ne. Käytä aina omaa PSK-avainta yksityiseen viestintään.

## Parhaat käytännöt

- Käytä kanavatasoista salausta (PSK) kanavissa, jotka yhdistetään MQTT:hen
- Don't enable MQTT on a node with no internet access of its own unless **MQTT Client Proxy** is on (the node queues messages it can't send and wastes memory)
- Käytä yksityistä välityspalvelinta arkaluonteisissa käyttöönotossa
- Ole tarkkana lähetysajan (downlink) käyttämisessä ruuhkaisissa MQTT-aiheissa — jokainen viesti kuluttaa radio-aikaa paikallisessa meshissä
- Harkitse lähetyssuunnan vaihtamista (uplink), jos haluat vain seurata verkkoa etänä ilman viestien lähettämistä verkkoon

## Vianetsintä

### MQTT ei yhdistä

- **Tarkista WiFi** — yhdyskäytäväradiolla on oltava toimiva internetyhteys (WiFi tai Ethernet). MQTT ei toimi LoRa-radiolinkin kautta.
- **Tarkista tunnistetiedot** — virheellisillä tunnistetiedoilla useimmat välityspalvelimet epäonnistuvat ilman virheilmoitusta — tarkista erityisesti lopussa olevat välilyönnit.
- **Palomuuri** — portin 1883 (MQTT) tai 8883 (MQTT TLS:n kautta) on oltava saavutettavissa. Joissakin verkoissa sallitaan vain verkkoliikenne (portit 80 ja 443).
- **DNS-ratkaisu** – jos käytät omaa välityspalvelimen isäntänimeä, varmista että laite pystyy ratkaisemaan sen. Kokeile välityspalvelimen IP-osoitetta suoraan.

### Viestit eivät välity

- **Tarkista lähetys ja vastaanotto-asetukset** – jos vain lähetys on käytössä, viestit kulkevat meshistä MQTT:hen mutta eivät takaisin. Ota vastaanotto käyttöön vastaanottavassa yhdyskäytävän laitteessa.
- **Kanava ei täsmää** – molempien gateway-laitteiden täytyy käyttää samaa kanavaa ja samaa PSK-avainta. Eroavaisuus tarkoittaa, että viestit on salattu eri avaimilla ja näyttävät roskalta.
- **Topic mismatch** — both gateways must use exactly the same root topic. On the public broker, setting a region rewrites a default root to `msh/<REGION>` (for example `msh/US`), so gateways in different regions do not meet until you give both the same explicit root. Firmware 2.8.0 and older also rewrite a root starting with `msh` on a private broker.
- **Ignore MQTT is on** — in a region with a duty-cycle limit, the node turns on **Ignore MQTT** (LoRa config, **Advanced**) when you set the region, and then drops every packet that reached it via MQTT. Turn it off on the receiving nodes, not only on the gateway.
- **OK to MQTT is off** — on a public broker a gateway uplinks other nodes' packets only when the sending node has **OK to MQTT** (LoRa config, **Advanced**) on. Your own traffic bridges either way; your neighbors' does not until they opt in.

## Aiheeseen liittyvät aiheet

- [Asetukset — Moduulit ja ylläpito](settings-module-admin) — MQTT-moduulin asetusten viite
- [Viestit ja kanavat](messages-and-channels) — kanavasalaus ja PSK-asetukset
- [MQTT-integraatio-opas](https://meshtastic.org/docs/software/integrations/mqtt) — yksityiskohtainen MQTT-dokumentaatio meshtastic.org -sivustolla
