---
title: TAK Integration
parent: User Guide
nav_order: 10
last_updated: 2026-05-13
description: Interoperate with ATAK and WinTAK — CoT position sharing, TAK roles, and plugin setup.
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
- TAK moodul on teie Meshtastic raadios lubatud

### Sätted

1. Mine menüüsse **Seaded → Mooduli konfiguratsioon → TAK**.
2. Luba TAK moodul.
3. TAK meeskonna/grupi seadistamine:

![Mooduli lüliti](/assets/screenshots/settings_switch.png)

| Sätted  | Kirjeldus                  |
| ------- | -------------------------- |
| Lubatud | Activate TAK interop       |
| Mode    | TAK-compatible output mode |

### ATAK Plugin Setup

1. Paigalda pluginate hoidlast Meshtastic ATAK plugin.
2. Ava ATAK ja luba Meshtastic plugin.
3. The plugin bridges messages between ATAK and your mesh network.

## TAK Roles

Nodes configured with TAK-related roles behave differently from standard clients:

| Roll                  | Kirjeldus                                                                                                                                                                                                                                                                                       |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **TAK**               | Täielik TAK koostalitlusvõime – saadab ja võtab vastu CoT andmeid, vestlussõnumeid ja PLI-uuendusi. Functions as a standard client plus TAK bridge.                                                                                                             |
| **TAK jälgimisseade** | Ainult asukohapõhine TAK väljund – levitab PLI automaatselt regulaarsete intervallidega ilma kasutaja sekkumiseta. Optimized for unattended position beacons (vehicles, equipment, waypoints). Does not relay chat messages. |

> 💡 **Vihje:** Kasuta **TAK jälgimisseadet** seadmete puhul, mis peavad edastama ainult asukohta (nt sõidukisse paigaldatud raadio). Use **TAK** for devices where users actively participate in TAK operations.

### CoT (Cursor on Target) Format

TAK messages use the Cursor on Target XML format — a military standard for sharing situational awareness data. Meshtastic teisendab oma sisemised protobuf-sõnumid TAK-süsteemidega ühendamisel CoT-vormingusse, seega pole käsitsi vormingu teisendamist vaja.

## TAK Identity

TAK rollide kasutamisel levitab teie sõlm identiteediteavet, mis kuvatakse TAK kaartidel:

| Sätted  | Kirjeldus                                                                                            |
| ------- | ---------------------------------------------------------------------------------------------------- |
| Tiim    | Sinu meeskonna värv TAK kaardil (nt sinine, punane, tsüaansinine, roheline)       |
| Roll    | Teie operatiivne roll (meeskonnaliige, meeskonnajuht, peakorter, meedik, RTO jne) |
| Kutsung | Sinu TAK kutsung (vaikimisi sinu Meshtastic pikk nimi)                            |

Need sätted kuvatakse menüüs **Seaded → Mooduli konfiguratsioon → TAK**, kui TAK moodul on lubatud.

> 💡 **Vihje:** Meeskonna/rolli värvid on TAK standardsed kuuluvusvärvid. Koordineeri oma TAK meeskonnaga järjepidevat meeskonnatöö jagamist.

## Wire Format (V1 / V2)

Meshtastic toetab kahte TAK wire-formaati:

| Format                          | Compatibility                                                   | Features                                                  |
| ------------------------------- | --------------------------------------------------------------- | --------------------------------------------------------- |
| V1 (Legacy)  | ATAK Plugin v1.x, older firmware                | Basic CoT position sharing only                           |
| V2 (Current) | ATAK Plugin v2.x, firmware 2.3+ | Full CoT support including chat, routes, zstd compression |

The app automatically selects V2 when both sides support it. Manuaalset konfigureerimist pole vaja – TAK-moodul lepib vormingu kokku püsivara võimaluste põhjal.

## Usage with ATAK

Once configured:

- Meshtastic sõlmed ilmuvad ATAK kaardil markeritena koos kutsungi nimega
- Chat messages can bridge between mesh and TAK networks
- Asukohavärskendused liiguvad Meshtasticu ja TAKi vahel kahesuunaliselt
- TAK jälgimisseadme sõlmed levitavad PLId automaatselt – nende asukohad kuvatakse ATAK kaartidel ilma ATAK poolse konfita

> ⚠️ **Märkus:** TAK-i integratsioon nõuab spetsiifilisi sõlmerolle ja mooduli konfiguratsiooni. Standard client nodes don't automatically participate in TAK operations.

## Troubleshooting

| Problem                          | Cause                                 | Solution                                                                           |
| -------------------------------- | ------------------------------------- | ---------------------------------------------------------------------------------- |
| Node doesn't appear on ATAK map  | TAK moodul on keelatud või vale roll  | Veendu, et TAK moodul on lubatud ja sõlme roll on TAK või TAK jälgimisseade        |
| Asukohavärskendused on aegunud   | GPS fix lost or interval too long     | Kontrolli GPSi olekut; vähenda asukoha konfis asukoha levitamise intervalli        |
| ATAK plugin shows "disconnected" | BLE connection lost or plugin crashed | Ühenda Meshtastic rakenduses sinihammas uuesti ja seejärel taaskäivita ATAK plugin |
| Chat messages not bridging       | V1 format doesn't support chat        | Ensure both nodes run firmware 2.3+ for V2 wire format             |
| CoT data not flowing             | Channel mismatch                      | All TAK nodes must be on the same channel with matching encryption                 |

## Security Considerations

- TAK andmed jagavad teie asukohta ja kutsungit
- Ensure your channel encryption is configured when using TAK in sensitive environments
- TAK moodul arvestab sama kanali krüptimist nagu teised Meshtasticu sõnumid

## Related Topics

- [Seaded — moodulid ja admin](settings-module-admin) — TAK mooduli konf
- [Sõlmed](nodes) — TAK ja TAK jälgimisseade rollid sõlmede loendis
- [Map & Waypoints](map-and-waypoints) — node positions on the map
- [ATAK plugin juhend](https://meshtastic.org/docs/software/integrations/atak-plugin) — üksikasjalik ATAK seadistamine aadressil meshtastic.org

---

