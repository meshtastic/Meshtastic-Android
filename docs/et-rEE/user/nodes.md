---
title: Sõlmed
parent: User Guide
nav_order: 4
last_updated: 2026-06-25
description: Browse, filter, and sort mesh nodes — view details, signal quality, roles, and quick actions.
aliases:
  - node-list
  - mesh-nodes
  - peers
---

# Sõlmed

The Nodes screen displays all devices visible on your mesh network.

## Node List

The node list shows every node your radio has heard, including:

- **Sõlme nimi** — kasutaja pandud pikk nimi
- **Short name** — 4-character identifier
- **Signal quality** — last heard signal strength
- **Last heard** — time since last communication
- **Distance** — estimated distance (if positions are shared)
- **Aku** — kaugsõlme aku tase (kui telemeetria on lubatud)

### Node Status Indicators

| Badge     | Meaning                             |
| --------- | ----------------------------------- |
| 🟢 Võrgus | Node heard within the last 2 hours  |
| ⚪ Offline | Node not heard for over 2 hours     |
| ⭐ Lemmik  | Node marked as favorite by the user |

A node is considered **online** if it was heard within the last 2 hours, and **offline** otherwise — there is no separate "away" tier.

### Node Roles

Sõlmedele saab määrata erinevaid rolle, mis mõjutavad nende kärgvõrgus käitumist:

| Roll                             | Kirjeldus                                                                                                                                                               |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Klient                           | Standard end-user device                                                                                                                                                |
| Klient-baas                      | Treats favorited-node traffic as Router Late priority; all other traffic as Client                                                                                      |
| Vaikne klient                    | Receives but doesn't retransmit                                                                                                                                         |
| Peidetud klient                  | Like Client Mute, plus hides from node list                                                                                                                             |
| Ruuter                           | Prioritizes message forwarding; stays awake to relay                                                                                                                    |
| Hiline ruuter                    | Infrastruktuurisõlm, mis levitab signaali ühe korra, kuid alles pärast kõiki teisi režiime (pakub täiendavat leviala)                                |
| ~~Router Client~~                | ⚠️ **Vananenud** (eemaldatud püsivara versioonis 2.3.15) — enam mitte valitav; kasuta hoopis ruuterint või kliendina |
| ~~Repeater~~                     | ⚠️ **Vananenud** (eemaldatud püsivara versioonis 2.7.11) — enam mitte valitav; kasuta hoopis ruuterina               |
| Jälgitav                         | Optimized for position reporting at regular intervals                                                                                                                   |
| Andur                            | Optimized for telemetry reporting                                                                                                                                       |
| TAK                              | Interoperates with TAK systems (sends/receives CoT)                                                                                                  |
| Jälgitav TAK                     | TAK position reporting only                                                                                                                                             |
| Lost & Found | Continuous position beacon for recovery                                                                                                                                 |

### Choosing a Role

Most users should keep the default **Client** role. Consider a different role when:

- **Router** — You have a node in a fixed, elevated location with reliable power (rooftop, hilltop). Routers stay awake continuously to relay messages for others and are essential for extending mesh coverage. Don't use Router on battery-powered handheld devices.
- **Ruuter hiline** – infrastruktuurisõlm, mis levitab pakette alati üks kord uuesti, aga alles pärast seda, kui kõik teised marsruutimisrežiimid on oma käigu teinud. Provides supplemental coverage for local clusters without competing with primary routers.
- **Client Base** — Treats traffic from/to your favorited nodes with Router Late priority (ensuring those messages get extra relay coverage) while handling everything else as a normal Client.
- **Client Mute** — You want to receive mesh traffic but not contribute to relaying. Useful for monitoring-only devices or to reduce congestion in dense areas.
- **Jälgimisseade** – järelevalveta seade, mille ainus eesmärk on oma GPS asukoha levitamine (nt sõiduk, lemmikloom või vara). Aku säästmiseks magab saadete vahel.
- **Sensor** — An unattended device reporting environmental telemetry (temperature, humidity, air quality). Sarnane võimsusprofiil jälgimisseadmele.
- **TAK / TAK jälgimisseade** — Vajalik ainult ATAK/WinTAK süsteemidega koostööl. See [TAK Integration](tak) for details.

> 💡 **Vihje:** Kärgvõrk töötab kõige paremini, kui enamik sõlmi on **klient** või **ruuter**. Too many Mute nodes reduces mesh resilience; too many Routers in a dense area can cause congestion. A good rule of thumb: one Router per 5–10 Clients in your area.

### Encryption Indicators

Nodes display encryption status icons next to their name:

| Icon            | Meaning                                                                                                             |
| --------------- | ------------------------------------------------------------------------------------------------------------------- |
| 🔒 Lukustatud   | Communication uses PKI (public key infrastructure) — end-to-end encrypted with verified identity |
| 🔓 Lukust lahti | Communication uses shared channel PSK — encrypted but identity not individually verified                            |
| ⚠️ Ebakõla      | Public key mismatch — the node's key has changed since last seen (investigate before trusting)   |

> 💡 **Vihje:** PKI krüpteering (püsivara 2,5+) pakub tugevamat turvalisust kui kanali PSK, kuna igal sõlmel on unikaalne võtmepaar. Kui näed võtme mittevastavuse hoiatust, võib sõlm olla lähtestatud või ohustatud.

## Quick Actions

From the node list, you can:

- **Puuduta** sõlmel, et vaadata üksikasjade lehte
- **Long-press** for quick actions:
  - Mark/remove favorite
  - Mute/unmute notifications
  - Send a direct message
  - Trace route
  - Ignore/unignore
  - Remove node

## Filtering & Sorting

### Text Search

Type in the search field to filter nodes by name or short name. Filter uueneb reaalajas kirjutamise ajal.

### Filter Toggles

| Filtreeri                  | Kirjeldus                                                                                      |
| -------------------------- | ---------------------------------------------------------------------------------------------- |
| **Only online**            | Show only nodes heard within the last 2 hours                                                  |
| **Only direct**            | Show only nodes with direct (non-relayed) connections                       |
| **Include unknown**        | Show nodes that haven't sent user info yet                                                     |
| **Exclude infrastructure** | Hide infrastructure-role nodes (Router, Repeater, Router Late, Client Base) |
| **Välista MQTT**           | Peida ainult MQTT internetisilla kaudu kuuldavad sõlmed                                        |
| **Show ignored**           | Show nodes you've previously dismissed or muted                                                |

### Sort Options

| Sort                                        | Kirjeldus                                                          |
| ------------------------------------------- | ------------------------------------------------------------------ |
| **Last heard** (default) | Most recently heard nodes first                                    |
| **Alphabetical**                            | Sorted by node long name                                           |
| **Distance**                                | Nearest nodes first (requires position sharing) |
| **Hüppe kaugusel**                          | Vähim vahendatud hüppeid esimesena                                 |
| **Channel**                                 | Grouped by channel index                                           |
| **Läbi MQTT**                               | Rühmitatud MQTT ver raadiost kuuldud järgi                         |
| **Favorites**                               | Favorited nodes first                                              |

## Node Detail

Sõlmel klõpsamine avab detailvaate koos põhjaliku teabega. See [Node Metrics](node-metrics) for full details on metrics and telemetry.

![Sõlme detailvaade](../../assets/screenshots/nodes_node_list.png)

The detail screen includes device info, position, and action buttons:

![Sõlme üksikasjade jaotis](../../assets/screenshots/nodes_detail_section.png)

Inline status indicators show key metrics at a glance:

| Indicator       | Screenshot                                                    |
| --------------- | ------------------------------------------------------------- |
| Signal quality  | ![Signal](../../assets/screenshots/nodes_signal_info.png)     |
| Battery level   | ![Battery](../../assets/screenshots/nodes_battery_info.png)   |
| Hüppete loendur | ![Hüpet](../../assets/screenshots/nodes_hops_info.png)        |
| Viimati kuuldud | ![Last heard](../../assets/screenshots/nodes_last_heard.png)  |
| Kaugus          | ![Distance](../../assets/screenshots/nodes_distance_info.png) |

### Device Links ("I want one")

When a node's hardware is recognized, the detail view shows a collapsible **"I want one"** section linking to places to buy or learn more about that device: the vendor's product page, product variants, and regional marketplace listings (such as AliExpress, Amazon, and supported retailers), filtered to your country. Each link opens through the `msh.to` redirect service. Devices with no matching links don't show the section.

A full, browsable directory of every link is also available under **Settings → Device Links**.

## Related Topics

- [Node Metrics](node-metrics) — detailed telemetry dashboards for each node
- [Messages & Channels](messages-and-channels) — send a direct message to a node
- [Map & Waypoints](map-and-waypoints) — view node positions geographically
- [Discovery](discovery) — traceroute and neighbor info for topology exploration
- [Signal Meter](signal-meter) — understand what the signal bars mean

---

