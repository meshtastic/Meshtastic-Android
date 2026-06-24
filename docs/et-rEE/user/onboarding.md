---
title: Getting Started
parent: User Guide
nav_order: 1
last_updated: 2026-05-13
description: First-launch setup — permissions, onboarding flow, and next steps after connecting your radio.
aliases:
  - first-launch
  - setup
  - intro
---

# Getting Started

Tere tulemast Meshtasticusse! See juhend juhendab teid Meshtastic Androidi rakenduse esmasel seadistamisel.

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

The app requests several permissions during setup. Each one serves a specific purpose, and some are required for core functionality.

### Bluetooth Permission

Sinihammas on peamine ühendusmeetod teie telefoni ja Meshtastic raadio vahel:

- **Bluetoothi ​​skann** – avasta lähedalasuvad Meshtastic raadiod
- **Sinihamba ühendus** – loo ja halda seotud seadmete ühendusi

Grant both permissions when prompted. Without Bluetooth, you'll need to use USB or TCP connections instead.

### Location Permission

> ⚠️ **Miks on sinihamba ​​jaoks vaja asukohateavet?** Android vajab lähedalasuvate sinihamba madala energia seadmete avastamiseks asukohale juurdepääsu luba. See on Androidi süsteeminõue, mitte Meshtastic põhine valik.

Meshtastic kasutab teie asukohta ka järgmiseks:

- Showing your position on the mesh map
- Calculating distances to other nodes
- Sharing your GPS coordinates with other mesh members (if enabled)

Grant **"While using the app"** or **"Always"** depending on your preference:

- **Rakenduse kasutamise ajal** – asukohta uuendatakse kui rakendus on avatud
- **Alati** – lubab taustal asukoha värskendusi, et kärgvõrgu oleks alati sisse lülitatud

If denied, Bluetooth scanning will not function and your node will not report a position.

### Notifications Permission

Notifications alert you to:

- Incoming messages from channels and direct messages
- Connection status changes (connected, disconnected, reconnecting)
- Püsivara värskenduse võimalikus

> 💡 **Vihje:** Märguannete eelistusi saad hiljem Androidi süsteemiseadetes täpsustada. The app creates separate notification channels for messages, connection events, and background service status.

### Critical Alerts Permission

On supported devices, the app may request permission for critical alerts:

- These are high-priority notifications that can break through Do Not Disturb mode
- Useful for emergency mesh alerts or urgent messages
- You can **skip** this step if you don't need breakthrough notifications
- Configure or revoke later in Android notification settings

## After Setup

Once permissions are granted, the app transitions to the main interface. Esimene samm peaks olema ühenduse loomine Meshtastic raadioga – üksikasjalike juhiste saamiseks vaata [Ühendused] (connections).

> 💡 **Vihje:** Kui jätsid seadistamise ajal mõne õiguse andmata, saad selle hiljem anda jaotises **Androidi seaded → Rakendused → Meshtastic → Load**. The app will prompt you again if a missing permission blocks a feature you try to use.

## What's Next?

Once connected to a radio, explore:

- [Ühendused(connections) — seo oma esimene raadioseade
- [Messages & Channels](messages-and-channels) — send your first message
- [Nodes](nodes) — see who's on your mesh
- [Map & Waypoints](map-and-waypoints) — view node positions
- [Settings](settings-radio-user) — configure your radio and user profile

Kas oled Meshtasticus algaja? Meshtastic.org lehel olev [alustusjuhend](https://meshtastic.org/docs/getting-started) käsitleb riistvara valimist, raadio esialgset seadistamist ja teie esimest võrgu seadistamist.

---
