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

> ℹ️ **Mis on täna saadaval:** Google Play versioon pakub **ainult teavitused** autosõnumeid – sissetulevad sõnumid antakse teada peakomplektis ja saate vastata teavitusnuppude kaudu. Allpool kirjeldatud täielik vahekaartidega **Sõnumid / Sõlmed / Olek** kogemus on Android Car App Libraryl põhinev beetaversioon (Google'i mallipõhine auto kasutajaliides on praegu piiratud suletud/sisemise esituse radadega), seega kuvatakse see ainult versioonides, mis on kompileeritud `-PenableCarTemplates=true`-ga. The rest of this page documents that beta experience.

## Ülevaade

Kui telefon on ühendatud Android Auto peakomplektiga (või arenduses kasutatava töölaua peakomplekti emulaatoriga), esitleb beetaversioon Meshtasticut Android Car App Library abil loodud sõnumsiderakendusena, millel on vahekaartidega avakuva, mis on optimeeritud sõiduohutuks ja hõlpsasti kasutatavaks:

- **Messages** — recent conversations, with hands-free reading and replies.
- **Nodes** — the mesh node list, with a node-detail view.
- **Olek** — praegune ühendus ja võrgu olek.

The car app does not add a new connection of its own. See kasutab Meshtastici rakenduse olemasolevat ühendust, sõlme ja sõnumi olekut, seega kajastab see seda, millega telefon on juba ühendatud.

> ⚠️ **Märkus:** Autorakenduse reaalajas andmete kuvamiseks peab telefon olema ühendatud Meshtastic raadioga. Kui rakendus on lahti ühendatud, kajastab auto ekraan lahti ühendatud olekut.

## Sõnumid

The Messages tab lists your recent conversations. While driving, you can:

- **Have messages read aloud** so you don't need to look at the screen.
- **Reply by voice or text** using your head unit's reply control, dictating your response hands-free.

## Sõlmed

Vahekaart „Sõlmed” kuvab kärgvõrgu sõlmede loendi autosõbralikus paigutuses. Sõlme valimine avab sõlme detailvaate, kus on selle sõlme kohta põhiteave. Täieliku teabe tähenduse leiad jaotisest [Nodes](nodes).

## Olek

Vahekaart „Olek“ annab ülevaate sinu praegusest ühendusest ja võrguühenduse olekust – see on kasulik telefoni avamata kinnitamaks, et oled endiselt raadioga ühendatud.

## Seotud teemad

- [Sõnumid ja kanalid](user/messages-and-channels) - täielikud sõnumsidefunktsioonid sinu telefonis
- [Nodes](nodes) — detailed node list and node-detail information
- [Connections](connections) — how the app connects to your radio

---

