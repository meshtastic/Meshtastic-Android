---
title: Android auto
parent: Kasutaja juhis
nav_order: 18
last_updated: 2026-07-07
description: Kasuta Meshtasticut käed-vabad režiimis Android Auto peakomplektis – loe sõnumeid valjusti ette, vasta häälega ning kontrolli sõlmede ja võrgu olekut sõidu ajal.
aliases:
  - android auto
  - auto
  - head-unit
  - auto
---

# Android auto

Meshtastic integreerub Android Autoga, nii et saad sõidu ajal oma kärgvõrguga ühenduses püsida ilma käsi roolilt või pilku teelt tõstmata.

> ⚠️ **Märkus:** Android Auto tugi on saadaval ainult **Google'i-tüüpi Androidi versioonides**. It is not included in the F-Droid build, and it is not available on Desktop or iOS.

> ℹ️ **Mis on täna saadaval:** Google Play versioon pakub **ainult teavitused** autosõnumeid – sissetulevad sõnumid antakse teada peakomplektis ja saate vastata teavitusnuppude kaudu. The full tabbed **Messages / Nodes / Status** experience described below is a beta built on the Android Car App Library (Google's templated car UI is currently restricted to Closed/Internal Play tracks), so it appears only in builds compiled with `-PenableCarTemplates=true`. The rest of this page documents that beta experience.

## Overview

When your phone is connected to an Android Auto head unit (or the Desktop Head Unit emulator used for development), the beta build presents Meshtastic as a messaging app built with the Android Car App Library, with a tabbed Home screen optimized for driving-safe, glanceable use:

- **Messages** — recent conversations, with hands-free reading and replies.
- **Nodes** — the mesh node list, with a node-detail view.
- **Status** — current connection and mesh status.

The car app does not add a new connection of its own. See kasutab Meshtastici rakenduse olemasolevat ühendust, sõlme ja sõnumi olekut, seega kajastab see seda, millega telefon on juba ühendatud.

> ⚠️ **Märkus:** Autorakenduse reaalajas andmete kuvamiseks peab telefon olema ühendatud Meshtastic raadioga. Kui rakendus on lahti ühendatud, kajastab auto ekraan lahti ühendatud olekut.

## Sõnumid

The Messages tab lists your recent conversations. While driving, you can:

- **Have messages read aloud** so you don't need to look at the screen.
- **Reply by voice or text** using your head unit's reply control, dictating your response hands-free.

## Sõlmed

The Nodes tab shows your mesh node list in a car-friendly layout. Selecting a node opens a node-detail view with key information about that node. See [Nodes](nodes) for the full meaning of the information shown.

## Status

The Status tab summarizes your current connection and mesh status at a glance — useful for confirming you're still connected to your radio without opening your phone.

## Related Topics

- [Sõnumid ja kanalid](user/messages-and-channels) - täielikud sõnumsidefunktsioonid sinu telefonis
- [Nodes](nodes) — detailed node list and node-detail information
- [Connections](connections) — how the app connects to your radio

---

