---
title: TAK-integraatio
nav_order: 10
last_updated: 2026-10-21
description: ATAK:n ja WinTAK:n yhteentoimivuus — CoT-sijaintijako, TAK-roolit ja lisäosien käyttöönotto.
aliases:
  - tak
  - atak
  - team awareness kit
---

# TAK-integraatio

Meshtastic integrates with the Team Awareness Kit (TAK) ecosystem, enabling interoperability between Meshtastic nodes and TAK applications like ATAK and WinTAK.

## Yleiskatsaus

TAK-moduuli mahdollistaa Meshtastic-radioille:

- Sijaintidatan jakamisen TAK-yhteensopivassa CoT (Cursor on Target) -muodossa
- Näkyä tiimin jäseninä TAK-karttanäkymissä
- Vastaanottaa TAK PLI (Position Location Information) -viestejä

## Asetukset

### Edellytykset

- Asennettuna ATAK (Android Team Awareness Kit), iTAK tai WinTAK
- Your node's **Device Role** (Device configuration) set to **TAK** or **TAK Tracker** — this is what makes the
  TAK module appear in Module configuration at all
- Firmware 2.8.0 or newer on the node. Earlier firmware accepts a TAK config write and never
  stores it, so the app hides the TAK module entry below that version even when the role is set
  correctly. See [Firmware Updates](firmware)

> ⚠️ **Varoitus:** Vanha **Meshtastic ATAK Plugin** ei enää kuulu tähän kokonaisuuteen eikä toimi. It bridged through the cross-process AIDL API, which was removed in app 2.8.0; the mesh
> service runs in-process only. Älä asenna sitä. Interop runs over the app's own local
> TAK server plus the Mesh to CoT Converter, both described in the following sections, with stock ATAK/iTAK/WinTAK.

### Asetukset

Navigate to **Settings → Module configuration → TAK (ATAK)**. The module's own settings are your TAK identity —
there is no separate enable switch here, because the **Device Role** setting in Device configuration is what
turns TAK on. Radiosi lähettää tämän tunnuksen, joka näkyy TAK-kartoilla.

| Asetus | Kuvaus                                                                                                                         | Oletus                                   |
| ------ | ------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------- |
| Rooli  | Your operational role within the team: Team Member, Team Lead, HQ, Sniper, Medic, Forward Observer, RTO, or K9 | Default (Team Member) |
| Tiimi  | Your team color on the TAK map, one of the 14 standard TAK team colors such as Blue, Red, Cyan, or Green                       | Default (Cyan)        |

Your TAK callsign isn't a separate setting. The firmware uses your node's long name.

Team colors are the standard TAK team colors, so coordinate with your TAK team to use consistent team assignments.

### Paikallinen TAK-palvelin

The app can also run a **local TAK server** so ATAK on the **same phone**, or WinTAK on the same computer running the desktop app, can connect directly without a remote TAK server. Palvelin kuuntelee vain paikallisia yhteyksiä (127.0.0.1:8089) ja käyttää TLS:ää molemminpuolisella varmenteiden todentamisella.

Open **Settings → Advanced → TAK Server**. These are app settings stored on this phone, which is why they sit next to **Firmware Update** and **Debug Panel** rather than under the TAK module config — and why they need neither a TAK role nor firmware 2.8.0. On Android 17 and newer, turning the server on asks for the local network permission it needs to open its socket. If that permission is denied for good, the app turns the server back off and offers to open app settings:

![Paikallisen TAK-palvelimen asetukset, joissa näkyvät käyttöönotto ja vientitoiminto](../../assets/screenshots/tak_server_enabled.png)

- **Enable Local TAK Server** — starts the loopback-only mTLS server on port **8089** for ATAK connections from the same device.
- **Status** — under the toggle: _Off_, _Starting TAK Server_, _Listening on 127.0.0.1:8089 — waiting for ATAK_, the count of local client connections, or _Unable to start TAK Server. Turn it off and on to retry._
- **TAK-mesh-kanava** – valitsee, mitä Meshtastic-kanavaa käytetään lähtevään TAK-liikenteeseen (oletuksena ensisijainen kanava, indeksi 0) Saapuva TAK-liikenne hyväksytään miltä tahansa kanavalta. Vastaa iOS:n ja vanhan ATAK-liitännäisen vastaavaa asetusta.
- **Mesh to CoT Converter** — oletusarvoisesti pois käytöstä, ja näkyy palvelinkytkimen alla. Kun palvelin on käynnissä, se luo CoT-yhteystiedon jokaisesta radiosolmujen tietokannan radiosta, jolloin tavalliset Meshtastic-radiot näkyvät ATAK-kartalla yhteystietoina. **Tämä korvaa vanhan lisäosan näkyvyyden** — ilman tätä näkyvät vain TAK-roolin radiot. When the connected node runs
  firmware older than 2.8.0, a notice under the switch says that only location and chat messages
  bridge to ATAK.
- **Export TAK Data Package** — generates a `.zip` data package that ATAK can import to connect to this server. The share icon in the top bar does the same.

## TAK-roolit

TAK-rooleilla määritetyt radiot käyttäytyvät eri tavalla kuin tavalliset client-laitteet:

| Rooli           | Kuvaus                                                                                                                                                                                                                            |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **TAK**         | For a node paired with an ATAK device. Turns off many routine broadcasts to favor the CoT packet stream.                                                                                          |
| **TAK Tracker** | Broadcasts TAK PLI automatically, at the interval set by the position configuration, and turns off many routine broadcasts. Suited to unattended position beacons such as vehicles and equipment. |

> 💡 **Tip:** Use **TAK Tracker** for devices that only need to report position (e.g., a node mounted in a vehicle). Käytä **TAK**-tilaa laitteille, joissa käyttäjät osallistuvat aktiivisesti TAK-toimintaan.

### CoT (Cursor on Target) -muoto

TAK-viestit käyttävät Cursor on Target XML -muotoa — sotilaskäytössä olevaa standardia tilannetiedon jakamiseen. Meshtastic muuntaa sisäiset protobuf-viestinsä CoT-muotoon silloin, kun se välittää dataa TAK-järjestelmiin, joten manuaalista muunnosta ei tarvita.

## Wire Format (V1 / V2)

Meshtastic supports two TAK wire formats, chosen automatically based on the connected node's firmware — no manual configuration needed:

| Muoto                                | Yhteensopivuus                                                     | Ominaisuudet                                                                                                                                                                                                                                                              |
| ------------------------------------ | ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| V1 (vanha versio) | Laiteohjelmisto 2.7.x ja vanhemmat | Raaka protobuf-koodaus portissa 72. Tukee vain sijainnin jakamista (PLI) ja keskusteluja (GeoChat) — muodot, merkinnät, reitit ja muut tyypitetyt CoT-tapahtumat jätetään välittämättä                              |
| V2 (nykyinen)     | Laiteohjelmisto 2.8.0+             | Pakkausmuoto käyttää zstd-pakkausta portissa 78. Tukee V1:n ominaisuuksien lisäksi myös muotoja, merkintöjä, reittejä, ilma-aluksia, lääkintäevakuointeja (CASEVAC), hätätilanteita ja tehtävätyyppisiä CoT-tapahtumia |

Radio välittää edelleen vanhojen radioiden V1-paketteja myös käyttäessään itse V2:ta, joten eri laiteohjelmistoversioita käyttävät mesh-verkot toimivat keskenään.

## Käyttö ATAK:n kanssa

Kun asetukset on määritetty:

- Meshtastic-radiot näkyvät ATAK-kartalla merkkeinä kutsutunnuksineen
- Viestit voivat välittyä mesh-verkon ja TAK-verkon välillä
- Sijaintipäivitykset kulkevat kaksisuuntaisesti Meshtasticin ja TAK-järjestelmän välillä
- TAK Tracker -radiot lähettävät PLI-sijaintia automaattisesti — niiden sijainti näkyy ATAK-kartoilla ilman erillistä ATAK-asetusta
- On Android, routes received from the mesh are also saved as a data package (`.zip`) in **Downloads**; import it in ATAK to add the route. An update to the same route overwrites its file rather than adding a copy. After the app is reinstalled, the first update to a route saves one renamed copy, and later updates overwrite that. On Android 9 and older the file goes to the app's own folder under `Android/data` instead. The desktop app doesn't save route files

> ℹ️ **Huomautus:** TAK-integraatio edellyttää tiettyjä radiorooleja. Tavalliset Client-roolin radiot eivät osallistu automaattisesti TAK-toimintoihin — mutta kun **Mesh to CoT Converter** on käytössä, ne näkyvät silti ATAK-kartalla yhteystietoina.

## Vianetsintä

| Ongelma                                      | Syy                                                                                                                                   | Ratkaisu                                                                                                                                                                                                               |
| -------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Radio ei näy ATAK-kartalla                   | Wrong Device Role setting, or Mesh to CoT Converter off                                                                               | Set the node's **Device Role** to TAK or TAK Tracker. For ordinary (non-TAK-role) nodes to appear, also enable **Mesh to CoT Converter** under **Settings → Advanced → TAK Server** |
| Sijaintipäivitykset ovat vanhentuneita       | GPS-signaali katkennut tai lähetysväli liian pitkä                                                                                    | Tarkista GPS-tila; lyhennä sijaintilähetyksen väliä kohdassa Sijainnin asetukset                                                                                                                                       |
| ATAK näyttää tilan "Ei yhteyttä"             | Paikallinen TAK-palvelin ei ole käytössä tai ATAK on yhdistetty muualle                                                               | Varmista, että **Ota paikallinen TAK-palvelin käyttöön** on käytössä ja että ATAK muodostaa yhteyden osoitteeseen `127.0.0.1:8089` — tuo viety datapaketti uudelleen, jos et ole varma                                 |
| Muotoja, merkintöjä tai reittejä ei välitetä | Lähettävä radio käyttää vanhaa V1-protokollaa (laiteohjelmisto 2.7.x tai vanhempi) | Päivitä lähettävän radion laiteohjelmisto versioon 2.8.0 tai uudempaan, jotta V2-siirtomuoto on käytettävissä                                                                          |
| CoT-data ei kulje                            | Kanava ei täsmää                                                                                                                      | Kaikkien TAK-laitteiden täytyy olla samalla kanavalla ja yhteensopivalla salauksella                                                                                                                                   |

## Tietoturvahuomiot

> 🔒 **Tietosuoja:** TAK-tiedot sisältävät sijaintisi ja kutsutunnuksesi. TAK-moduuli käyttää samaa kanavasalausta kuin muutkin Meshtastic-viestit — arkaluonteisissa ympäristöissä käytä kanavaa, jonka avain ei ole oletusarvoinen.

## Aiheeseen liittyvät aiheet

- [Asetukset — Moduulit ja ylläpito](settings-module-admin) — TAK-moduulin asetukset
- [Radiot](nodes) — TAK- ja TAK Tracker -roolit radiolistassa
- [Kartta ja reittipisteet](map-and-waypoints) — radioiden sijainnit kartalla
