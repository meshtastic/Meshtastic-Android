---
title: TAK integratsioon
nav_order: 10
last_updated: 2026-10-21
description: Koostöö ATAKi ja WinTAKiga — CoT asukoha jagamine, TAK rollid ja pluginate seadistamine.
aliases:
  - tak
  - atak
  - meeskonna teadlikkuse komplekt
---

# TAK integratsioon

Meshtastic integrates with the Team Awareness Kit (TAK) ecosystem, enabling interoperability between Meshtastic nodes and TAK applications like ATAK and WinTAK.

## Ülevaade

TAK moodul võimaldab Meshtastic sõlmedel:

- Jaga asukohaandmeid TAK ühilduvas CoT (Cursor on Target) vormingus
- Kuvatakse meeskonnaliikmetena TAK kaardil
- TAK PLI (asukohateabe) sõnumite vastuvõtmine

## Seadistamine

### Prerequisites

- ATAK (Android Team Awareness Kit), iTAK, or WinTAK installed
- Your node's **Device Role** (Device configuration) set to **TAK** or **TAK Tracker** — this is what makes the
  TAK module appear in Module configuration at all
- Firmware 2.8.0 or newer on the node. Earlier firmware accepts a TAK config write and never
  stores it, so the app hides the TAK module entry below that version even when the role is set
  correctly. See [Firmware Updates](firmware)

> ⚠️ **Warning:** The old **Meshtastic ATAK Plugin** is no longer part of this path and cannot
> work. It bridged through the cross-process AIDL API, which was removed in app 2.8.0; the mesh
> service runs in-process only. Do not install it. Interop runs over the app's own local
> TAK server plus the Mesh to CoT Converter, both described in the following sections, with stock ATAK/iTAK/WinTAK.

### Sätted

Navigate to **Settings → Module configuration → TAK (ATAK)**. The module's own settings are your TAK identity —
there is no separate enable switch here, because the **Device Role** setting in Device configuration is what
turns TAK on. Your node broadcasts this identity, which appears on TAK maps.

| Seadistamine | Kirjeldus                                                                                                                      | Vaikimisi                                |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------- |
| Roll         | Your operational role within the team: Team Member, Team Lead, HQ, Sniper, Medic, Forward Observer, RTO, or K9 | Default (Team Member) |
| Tiim         | Your team color on the TAK map, one of the 14 standard TAK team colors such as Blue, Red, Cyan, or Green                       | Default (Cyan)        |

Your TAK callsign isn't a separate setting. The firmware uses your node's long name.

Team colors are the standard TAK team colors, so coordinate with your TAK team to use consistent team assignments.

### Lokaalne TAK server

The app can also run a **local TAK server** so ATAK on the **same phone**, or WinTAK on the same computer running the desktop app, can connect directly without a remote TAK server. Server seostub ainult localhostiga (`127.0.0.1:8089`) ja kasutab TLS-i vastastikuse sertifikaadi autentimisega (mTLS), seega pole see võrgus olevatest seadmetest kättesaadav.

Open **Settings → Advanced → TAK Server**. These are app settings stored on this phone, which is why they sit next to **Firmware Update** and **Debug Panel** rather than under the TAK module config — and why they need neither a TAK role nor firmware 2.8.0. On Android 17 and newer, turning the server on asks for the local network permission it needs to open its socket. If that permission is denied for good, the app turns the server back off and offers to open app settings:

![Kohaliku TAK-serveri seaded koos lubamise lüliti ja ekspordi valikuga](../../assets/screenshots/tak_server_enabled.png)

- **Enable Local TAK Server** — starts the loopback-only mTLS server on port **8089** for ATAK connections from the same device.
- **Status** — under the toggle: _Off_, _Starting TAK Server_, _Listening on 127.0.0.1:8089 — waiting for ATAK_, the count of local client connections, or _Unable to start TAK Server. Turn it off and on to retry._
- **TAK kärgvõrgu kanal** — valib, millisel Meshtastic kanalil väljaminev TAK liiklus saadetakse (vaikimisi: peamine kanal, indeks 0). Sissetulevat TAK liiklust võetakse vastu igalt kanalilt. Vastab iOS-i ja pärand-ATAK-i pistikprogrammi samaväärsele sättele.
- **Mesh to CoT Converter** — off by default, and shown under the server toggle. With the server
  running, this synthesizes a CoT contact for every node in your node database, so ordinary
  Meshtastic nodes appear on the ATAK map as contacts. **This is what replaced the old plugin's
  node visibility** — without it, only TAK-role nodes show up. When the connected node runs
  firmware older than 2.8.0, a notice under the switch says that only location and chat messages
  bridge to ATAK.
- **Export TAK Data Package** — generates a `.zip` data package that ATAK can import to connect to this server. The share icon in the top bar does the same.

## TAK rollid

TAKiga seotud rollidega seadistatud sõlmed käituvad tavalistest klientidest erinevalt:

| Roll                  | Kirjeldus                                                                                                                                                                                                                         |
| --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **TAK**               | For a node paired with an ATAK device. Turns off many routine broadcasts to favor the CoT packet stream.                                                                                          |
| **TAK jälgimisseade** | Broadcasts TAK PLI automatically, at the interval set by the position configuration, and turns off many routine broadcasts. Suited to unattended position beacons such as vehicles and equipment. |

> 💡 **Tip:** Use **TAK Tracker** for devices that only need to report position (e.g., a node mounted in a vehicle). Kasutage **TAK** seadmete puhul, mille kasutajad osalevad aktiivselt TAK toimingutes.

### CoT (Cursor on Target) vorming

TAK- õnumid kasutavad kursori sihtmärgil XML-vormingut – see on sõjaline standard olukorrateadlikkuse andmete jagamiseks. Meshtastic teisendab oma sisemised protobuf-sõnumid TAK-süsteemidega ühendamisel CoT-vormingusse, seega pole käsitsi vormingu teisendamist vaja.

## Sõnumivorming (V1 / V2)

Meshtastic supports two TAK wire formats, chosen automatically based on the connected node's firmware — no manual configuration needed:

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
- On Android, routes received from the mesh are also saved as a data package (`.zip`) in **Downloads**; import it in ATAK to add the route. An update to the same route overwrites its file rather than adding a copy. After the app is reinstalled, the first update to a route saves one renamed copy, and later updates overwrite that. On Android 9 and older the file goes to the app's own folder under `Android/data` instead. The desktop app doesn't save route files

> ℹ️ **Note:** TAK integration requires specific node roles. Standard client nodes don't automatically participate in TAK operations — though with **Mesh to CoT Converter** enabled they still appear on the ATAK map as contacts.

## Veaotsing

| Probleem                                | Põhjus                                                                                                               | Lahendus                                                                                                                                                                                                               |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Sõlme ei kuvata ATAK kaardile           | Wrong Device Role setting, or Mesh to CoT Converter off                                                              | Set the node's **Device Role** to TAK or TAK Tracker. For ordinary (non-TAK-role) nodes to appear, also enable **Mesh to CoT Converter** under **Settings → Advanced → TAK Server** |
| Asukohavärskendused on aegunud          | GPS asukoht kadunud või intervall liiga pikk                                                                         | Kontrolli GPSi olekut; vähenda asukoha konfis asukoha levitamise intervalli                                                                                                                                            |
| ATAK shows "disconnected"               | The local TAK server is off, or ATAK is pointed elsewhere                                                            | Check **Enable Local TAK Server** is on, and that ATAK is connecting to `127.0.0.1:8089` — re-import the exported data package if unsure                                                                               |
| Shapes, markers, or routes not bridging | Saatja sõlm kasutab pärandversiooni V1 (püsivara 2.7.x või vanem) | Värskenda saatva sõlme püsivara versioonile 2.8.0+ sõnumivormingu V2 jaoks                                                                                                             |
| CoT andmed ei liigu                     | Kanali mittevastavus                                                                                                 | Kõik TAK-sõlmed peavad olema samal kanalil ja sama krüpteeringuga                                                                                                                                                      |

## Security Considerations

> 🔒 **Privacy:** TAK data shares your position and callsign information. The TAK module respects
> the same channel encryption as other Meshtastic messages — in sensitive environments, use a
> channel with a non-default key.

## Seotud teemad

- [Seaded — moodulid ja admin](settings-module-admin) — TAK mooduli konf
- [Sõlmed](nodes) — TAK ja TAK jälgimisseade rollid sõlmede loendis
- [Kaart ja teekonnapunktid](map-and-waypoints) — sõlmede asukohad kaardil
