---
title: TAK integratsioon
parent: Kasutusjuhend
nav_order: 10
last_updated: 2026-08-29
description: Koostöö ATAKi ja WinTAKiga — CoT asukoha jagamine, TAK rollid ja pluginate seadistamine.
aliases:
  - tak
  - atak
  - meeskonna teadlikkuse komplekt
---

# TAK integratsioon

Meshtastic integrates with the Team Awareness Kit (TAK) ecosystem, enabling interoperability between Meshtastic radios and TAK applications like ATAK and WinTAK.

## Ülevaade

TAK moodul võimaldab Meshtastic sõlmedel:

- Jaga asukohaandmeid TAK ühilduvas CoT (Cursor on Target) vormingus
- Kuvatakse meeskonnaliikmetena TAK kaardil
- TAK PLI (asukohateabe) sõnumite vastuvõtmine

## Seadistamine

### Prerequisites

- ATAK (Android Team Awareness Kit), iTAK, or WinTAK installed
- Your node's **Role** (Device Config) set to **TAK** or **TAK Tracker** — this is what makes the
  TAK module appear in Module Config at all

> ⚠️ **Warning:** The old **Meshtastic ATAK Plugin** is no longer part of this path and cannot
> work. It bridged through the cross-process AIDL API, which was removed in app 2.8.0; the mesh
> service is now in-process only. Do not install it. Interop today runs over the app's own local
> TAK server plus the Mesh to CoT Converter, both described below, with stock ATAK/iTAK/WinTAK.

### Sätted

Mine menüüsse **Seaded → Mooduli konfiguratsioon → TAK**. The module's own settings are your TAK identity —
there is no separate enable switch here, because the **Role** setting in Device Config is what
turns TAK on. Your node broadcasts this identity, which appears on TAK maps.

| Seadistamine   | Kirjeldus                                                                                                                |
| -------------- | ------------------------------------------------------------------------------------------------------------------------ |
| Meeskonna värv | Sinu meeskonna värv TAK kaardil (nt sinine, punane, tsüaansinine, roheline)                           |
| Liikme roll    | Your operational role within that team (Team Member, Team Lead, HQ, Medic, RTO, etc.) |

Your TAK callsign isn't a separate setting — it's derived automatically from your Meshtastic node
name.

> 💡 **Vihje:** Meeskonna/rolli värvid on TAK standardsed kuuluvusvärvid. Coordinate with your TAK
> team to use consistent team assignments.

### Lokaalne TAK server

The app can also run a **local TAK server** so ATAK/iTAK on the **same phone** can connect directly, without a remote TAK server. Server seostub ainult localhostiga (`127.0.0.1:8089`) ja kasutab TLS-i vastastikuse sertifikaadi autentimisega (mTLS), seega pole see võrgus olevatest seadmetest kättesaadav. Ava **Seaded → Mooduli konfiguratsioon → TAK → TAK Server**:

![Kohaliku TAK-serveri seaded koos lubamise lüliti ja ekspordi valikuga](../../assets/screenshots/tak_server_enabled.png)

- **Enable Local TAK Server** — starts the loopback-only mTLS server on port **8089** for ATAK/iTAK connections from the same phone.
- **TAK kärgvõrgu kanal** — valib, millisel Meshtastic kanalil väljaminev TAK liiklus saadetakse (vaikimisi: peamine kanal, indeks 0). Sissetulevat TAK liiklust võetakse vastu igalt kanalilt. Vastab iOS-i ja pärand-ATAK-i pistikprogrammi samaväärsele sättele.
- **Mesh to CoT Converter** — off by default, and shown under the server toggle. With the server
  running, this synthesizes a CoT contact for every node in your node database, so ordinary
  Meshtastic nodes appear on the ATAK map as contacts. **This is what replaced the old plugin's
  node visibility** — without it, only TAK-role nodes show up.
- **Ekspordi TAK andmepakett** — genereerib `.zip`-andmepaketi, mille ATAK/iTAK saab selle serveriga ühenduse loomiseks importida.

## TAK rollid

TAKiga seotud rollidega seadistatud sõlmed käituvad tavalistest klientidest erinevalt:

| Roll                  | Kirjeldus                                                                                                                                                                                                                                                                                                |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **TAK**               | Täielik TAK koostalitlusvõime – saadab ja võtab vastu CoT andmeid, vestlussõnumeid ja PLI-uuendusi. Toimib tavalise kliendi ja TAK sillana.                                                                                                                              |
| **TAK jälgimisseade** | Ainult asukohapõhine TAK väljund – levitab PLI automaatselt regulaarsete intervallidega ilma kasutaja sekkumiseta. Optimeeritud järelevalveta asukohamajakate (sõidukid, seadmed, teekonnapunktid) jaoks. Ei vahenda vestlussõnumeid. |

> 💡 **Vihje:** Kasuta **TAK jälgimisseadet** seadmete puhul, mis peavad edastama ainult asukohta (nt sõidukisse paigaldatud raadio). Kasutage **TAK** seadmete puhul, mille kasutajad osalevad aktiivselt TAK toimingutes.

### CoT (Cursor on Target) vorming

TAK- õnumid kasutavad kursori sihtmärgil XML-vormingut – see on sõjaline standard olukorrateadlikkuse andmete jagamiseks. Meshtastic teisendab oma sisemised protobuf-sõnumid TAK-süsteemidega ühendamisel CoT-vormingusse, seega pole käsitsi vormingu teisendamist vaja.

## Sõnumivorming (V1 / V2)

Meshtastic toetab kahte TAK sõnumivormingut, mis valitakse ühendatud raadio püsivara põhjal automaatselt – käsitsi konfigureerimist pole vaja:

| Vorming                                | Compatibility                                           | Omadused                                                                                                                                                                                                                      |
| -------------------------------------- | ------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| V1 (pärandversioon) | Püsivara 2.7.x ja vanem | Bare protobuf encoding on port 72. Toetab ainult asukoha jagamist (PLI) ja vestlust (GeoChat) – kujundid, markerid, marsruudid ja muud tüüpi CoT-sündmused eemaldatakse |
| V2 (praegune)       | Püsivara 2.8.0+         | Compact, zstd-compressed encoding on port 78. Lisab lisaks kõigele, mida V1 toetab, kujundeid, markereid, marsruute, õhusõidukeid, tsiviillennukeid, hädaolukordi ja ülesannete CoT tüüpe                     |

Sõlm edastab pärand sõlmedelt pärit V1 pakette isegi V2 kasutamise ajal, seega sega-püsivaraga võrgud töötavad edasi.

## Kasutamine koos ATAKiga

Kui on seadistatud:

- Meshtastic sõlmed ilmuvad ATAK kaardil markeritena koos kutsungi nimega
- Vestlussõnumid võivad ühendada kärgvõrgu ja TAK võrke
- Asukohavärskendused liiguvad Meshtasticu ja TAKi vahel kahesuunaliselt
- TAK jälgimisseadme sõlmed levitavad PLId automaatselt – nende asukohad kuvatakse ATAK kaartidel ilma ATAK poolse konfita

> ℹ️ **Note:** TAK integration requires specific node roles. Standard client nodes don't automatically participate in TAK operations — though with **Mesh to CoT Converter** enabled they still appear on the ATAK map as contacts.

## Veaotsing

| Probleem                                | Põhjus                                                                                                               | Lahendus                                                                                                                                                                                           |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Sõlme ei kuvata ATAK kaardile           | Wrong Role setting, or Mesh to CoT Converter off                                                                     | Set the node's **Role** to TAK or TAK Tracker. For ordinary (non-TAK-role) nodes to appear, also enable **Mesh to CoT Converter** under the TAK Server settings |
| Asukohavärskendused on aegunud          | GPS asukoht kadunud või intervall liiga pikk                                                                         | Kontrolli GPSi olekut; vähenda asukoha konfis asukoha levitamise intervalli                                                                                                                        |
| ATAK shows "disconnected"               | The local TAK server is off, or ATAK is pointed elsewhere                                                            | Check **Enable Local TAK Server** is on, and that ATAK is connecting to `127.0.0.1:8089` — re-import the exported data package if unsure                                                           |
| Shapes, markers, or routes not bridging | Saatja sõlm kasutab pärandversiooni V1 (püsivara 2.7.x või vanem) | Värskenda saatva sõlme püsivara versioonile 2.8.0+ sõnumivormingu V2 jaoks                                                                                         |
| CoT andmed ei liigu                     | Kanali mittevastavus                                                                                                 | Kõik TAK-sõlmed peavad olema samal kanalil ja sama krüpteeringuga                                                                                                                                  |

## Security Considerations

> 🔒 **Privacy:** TAK data shares your position and callsign information. The TAK module respects
> the same channel encryption as other Meshtastic messages — in sensitive environments, use a
> channel with a non-default key.

## Seotud teemad

- [Seaded — moodulid ja admin](settings-module-admin) — TAK mooduli konf
- [Sõlmed](nodes) — TAK ja TAK jälgimisseade rollid sõlmede loendis
- [Kaart ja teekonnapunktid](map-and-waypoints) — sõlmede asukohad kaardil
