---
title: Getting Started
parent: User Guide
nav_order: 1
last_updated: 2026-07-08
description: Esimese käivitamise seadistus — õigused, sissejuhatav voog ja järgmised sammud pärast raadio ühendamist.
aliases:
  - first-launch
  - seadistamine
  - intro
---

# Getting Started

Tere tulemast Meshtasticusse! See juhend juhendab sind Meshtastic Androidi rakenduse esmasel seadistamisel.

## First Launch

When you open the app for the first time, you'll be guided through an introductory flow that helps configure essential permissions and settings. Each step can be completed in order, or you can skip and configure permissions later in Android settings.

### Tervituskuva

Tervituskuval tutvustatakse Meshtasticut ja selle põhifunktsioone:

- Off-grid mesh communication
- No cellular or internet required
- End-to-end encrypted messaging

Puuduta **Alusta** seadistusvoo jätkamiseks.

![Tervituskuva](../../assets/screenshots/onboarding_welcome.png)

## Permissions

Rakendus küsib seadistamise ajal mitmeid lube. Each one serves a specific purpose, and some are required for core functionality.

### Sinihamba load

Sinihammas on peamine ühendusmeetod sinu telefoni ja Meshtastic raadio vahel:

- **Bluetoothi ​​skann** – avasta lähedalasuvad Meshtastic raadiod
- **Sinihamba ühendus** – loo ja halda seotud seadmete ühendusi

Grant both permissions when prompted. Ilma sinihambata peate kasutama USB- või TCP-ühendusi.

### Location Permission

> ⚠️ **Miks on sinihamba ​​jaoks vaja asukohateavet?** Android vajab lähedalasuvate sinihamba madala energia seadmete avastamiseks asukohale juurdepääsu luba. See on Androidi süsteeminõue, mitte Meshtastic põhine valik.

Meshtastic kasutab sinu asukohta ka järgmiseks:

- Showing your position on the mesh map
- Calculating distances to other nodes
- Sharing your GPS coordinates with other mesh members (if enabled)

Grant **"While using the app"** or **"Always"** depending on your preference:

- **Rakenduse kasutamise ajal** – asukohta uuendatakse kui rakendus on avatud
- **Alati** – lubab taustal asukoha värskendusi, et kärgvõrgu oleks alati sisse lülitatud

Kui keelatud, siis sinihamba otsing ei toimi ja sõlm ei edasta asukohta.

### Märguannete load

Märguanded teavitavad teid järgmisest:

- Sissetulevad sõnumid kanalitelt ja otsesõnumid
- New nodes joining the mesh
- Kaugsõlmel on aku tühjenenud

> 💡 **Vihje:** Teavituste eelistusi saab hiljem Androidi süsteemiseadetes täpsustada – rakendus loob iga kategooria kohta eraldi teavituskanali (lisaks mõned sisemised, näiteks taustateenus), nii et saad need eraldi lubada või vaigistada.

### Critical Alerts Permission

On supported devices, the app may request permission for critical alerts:

- Need on kõrge prioriteediga märguanded, mis võivad režiimist „Ära sega” läbi murda
- Useful for emergency mesh alerts or urgent messages
- Võid selle sammu **vahele jätta**, kui te kõrge prioriteediga märguandeid ei vaja
- Configure or revoke later in Android notification settings

## Peale seadistamist

Kui load on antud, läheb rakendus üle põhiliidesele. Esimene samm peaks olema ühenduse loomine Meshtastic raadioga – üksikasjalike juhiste saamiseks vaata [Ühendused] (connections).

> 💡 **Vihje:** Kui jätsid seadistamise ajal mõne õiguse andmata, saad selle hiljem anda jaotises **Androidi seaded → Rakendused → Meshtastic → Load**. The app will prompt you again if a missing permission blocks a feature you try to use.

## What's Next?

Kui raadioga on ühendus loodud, uuri:

- [Ühendused(connections) — seo oma esimene raadioseade
- [Sõnumid ja kanalid](messages-and-channels) — saada oma esimene sõnum
- [Seadmed](nodes) — vaata, kes on sinu võrgus
- [Map & Waypoints](map-and-waypoints) — view node positions
- [Settings](settings-radio-user) — configure your radio and user profile

Kas oled Meshtasticus algaja? Meshtastic.org lehel olev [alustusjuhend](https://meshtastic.org/docs/getting-started) käsitleb riistvara valimist, raadio esialgset seadistamist ja esimest võrgu seadistamist.

---
