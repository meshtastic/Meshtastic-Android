---
title: TAK Integration
parent: User Guide
nav_order: 10
last_updated: 2026-05-13
description: Koostöö ATAKi ja WinTAKiga — CoT positsiooni jagamine, TAK rollid ja pluginate seadistamine.
aliases:
  - tak
  - atak
  - meeskonna teadlikkuse komplekt
---

# TAK Integration

Meshtastic lõimub Team Awareness Kit (TAK) ökosüsteemiga, võimaldades Meshtastic kärgvõrgu seadmete ja TAK-rakenduste (nt ATAK ja WinTAK) koostalitlusvõimet.

## Overview

TAK moodul võimaldab Meshtastic sõlmedel:

- Share position data in TAK-compatible CoT (Cursor on Target) format
- Kuvatakse meeskonnaliikmetena TAK kaardil
- Receive TAK PLI (Position Location Information) messages

## Setup

### Prerequisites

- ATAK (Android Team Awareness Kit) või WinTAK on paigaldatud
- Meshtastic ATAK plugin on paigaldatud
- TAK moodul on sinu Meshtastic raadios lubatud

### Sätted

1. Mine menüüsse **Seaded → Mooduli konfiguratsioon → TAK**.
2. Luba TAK moodul.
3. TAK meeskonna/grupi seadistamine:

![Mooduli lüliti](/assets/screenshots/settings_switch.png)

| Sätted  | Kirjeldus                  |
| ------- | -------------------------- |
| Lubatud | Activate TAK interop       |
| Mode    | TAK-compatible output mode |

### ATAK plugina sätted

1. Paigalda pluginate hoidlast Meshtastic ATAK plugin.
2. Ava ATAK ja luba Meshtastic plugin.
3. Plugin sildab sõnumeid ATAKi ja kärgvõrgu vahel.

### Local TAK Server

The app can also run a **local TAK server** so ATAK/iTAK clients on the same device or network can connect directly, without a remote TAK server. Open **Settings → Module Config → TAK → TAK Server**:

![Local TAK Server settings with enable toggle and export option](../../assets/screenshots/tak_server_enabled.png)

- **Enable Local TAK Server** — starts a local TLS server on port **8089** for ATAK/iTAK connections.
- **Export TAK Data Package** — generates a `.zip` data package that ATAK/iTAK can import to connect to this server.

## TAK Roles

TAKiga seotud rollidega seadistatud sõlmed käituvad tavalistest klientidest erinevalt:

| Roll                  | Kirjeldus                                                                                                                                                                                                                                                                                       |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **TAK**               | Täielik TAK koostalitlusvõime – saadab ja võtab vastu CoT andmeid, vestlussõnumeid ja PLI-uuendusi. Functions as a standard client plus TAK bridge.                                                                                                             |
| **TAK jälgimisseade** | Ainult asukohapõhine TAK väljund – levitab PLI automaatselt regulaarsete intervallidega ilma kasutaja sekkumiseta. Optimized for unattended position beacons (vehicles, equipment, waypoints). Does not relay chat messages. |

> 💡 **Vihje:** Kasuta **TAK jälgimisseadet** seadmete puhul, mis peavad edastama ainult asukohta (nt sõidukisse paigaldatud raadio). Use **TAK** for devices where users actively participate in TAK operations.

### CoT (Cursor on Target) Format

TAK messages use the Cursor on Target XML format — a military standard for sharing situational awareness data. Meshtastic teisendab oma sisemised protobuf-sõnumid TAK-süsteemidega ühendamisel CoT-vormingusse, seega pole käsitsi vormingu teisendamist vaja.

## TAK Identity

TAK rollide kasutamisel levitab sinu sõlm identiteediteavet, mis kuvatakse TAK kaartidel:

| Sätted  | Kirjeldus                                                                                            |
| ------- | ---------------------------------------------------------------------------------------------------- |
| Tiim    | Sinu meeskonna värv TAK kaardil (nt sinine, punane, tsüaansinine, roheline)       |
| Roll    | Sinu operatiivne roll (meeskonnaliige, meeskonnajuht, peakorter, meedik, RTO jne) |
| Kutsung | Sinu TAK kutsung (vaikimisi sinu Meshtastic pikk nimi)                            |

Need sätted kuvatakse menüüs **Seaded → Mooduli konfiguratsioon → TAK**, kui TAK moodul on lubatud.

> 💡 **Vihje:** Meeskonna/rolli värvid on TAK standardsed kuuluvusvärvid. Koordineeri oma TAK meeskonnaga järjepidevat meeskonnatöö jagamist.

## Wire Format (V1 / V2)

Meshtastic toetab kahte TAK wire-formaati:

| Format                          | Compatibility                                                   | Features                                                  |
| ------------------------------- | --------------------------------------------------------------- | --------------------------------------------------------- |
| V1 (Legacy)  | ATAK plugin v1.x, vanem püsivara                | Basic CoT position sharing only                           |
| V2 (Current) | ATAK plugin v2.x, püsivara 2.3+ | Full CoT support including chat, routes, zstd compression |

The app automatically selects V2 when both sides support it. Manuaalset konfigureerimist pole vaja – TAK-moodul lepib vormingu kokku püsivara võimaluste põhjal.

## Kasutamine koos ATAKiga

Kui on seadistatud:

- Meshtastic sõlmed ilmuvad ATAK kaardil markeritena koos kutsungi nimega
- Chat messages can bridge between mesh and TAK networks
- Asukohavärskendused liiguvad Meshtasticu ja TAKi vahel kahesuunaliselt
- TAK jälgimisseadme sõlmed levitavad PLId automaatselt – nende asukohad kuvatakse ATAK kaartidel ilma ATAK poolse konfita

> ⚠️ **Märkus:** TAK-i integratsioon nõuab spetsiifilisi sõlmerolle ja mooduli konfiguratsiooni. Standard client nodes don't automatically participate in TAK operations.

## Troubleshooting

| Problem                                     | Cause                                 | Solution                                                                           |
| ------------------------------------------- | ------------------------------------- | ---------------------------------------------------------------------------------- |
| Sõlme ei kuvata ATAK kaardile               | TAK moodul on keelatud või vale roll  | Veendu, et TAK moodul on lubatud ja sõlme roll on TAK või TAK jälgimisseade        |
| Asukohavärskendused on aegunud              | GPS fix lost or interval too long     | Kontrolli GPSi olekut; vähenda asukoha konfis asukoha levitamise intervalli        |
| ATAK plugin kuvab teadet „ühendus katkenud” | BLE connection lost or plugin crashed | Ühenda Meshtastic rakenduses sinihammas uuesti ja seejärel taaskäivita ATAK plugin |
| Chat messages not bridging                  | V1 format doesn't support chat        | Ensure both nodes run firmware 2.3+ for V2 wire format             |
| CoT data not flowing                        | Channel mismatch                      | All TAK nodes must be on the same channel with matching encryption                 |

## Security Considerations

- TAK andmed jagavad sinu asukohta ja kutsungit
- TAKi kasutamisel tundlikes keskkondades veendu, et kanali krüpteerimine on seadistatud
- TAK moodul arvestab sama kanali krüptimist nagu teised Meshtasticu sõnumid

## Related Topics

- [Seaded — moodulid ja admin](settings-module-admin) — TAK mooduli konf
- [Sõlmed](nodes) — TAK ja TAK jälgimisseade rollid sõlmede loendis
- [Map & Waypoints](map-and-waypoints) — node positions on the map
- [ATAK plugin juhend](https://meshtastic.org/docs/software/integrations/atak-plugin) — üksikasjalik ATAK seadistamine aadressil meshtastic.org

---

