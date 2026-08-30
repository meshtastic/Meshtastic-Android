---
title: Rakenduse funktsioonid
parent: Kasutaja juhis
nav_order: 19
last_updated: 2026-08-29
description: Ava kärgvõrgu funktsioonid Androidi süsteemile ja seadme tehisintellektil põhinevatele assistentidele (nt Gemini), et nad saaksid kärgvõrgu töövooge käivitada rakendust avamata.
aliases:
  - rakenduse funktsioonid
  - süsteemi-ti
  - gemini
  - assistent
---

# Rakenduse funktsioonid

Rakendusfunktsioonid avaldavad Meshtasticu võimalused Androidi süsteemile ja seadmesisestele TI assistentidele (näiteks Gemini) Androidi rakenduste funktsioonide API kaudu. Kui need on lubatud, saab assistent sinu eest kärgvõrgu töövooge avastada ja käivitada – näiteks sõnumi saata või kärgvõrgu olekut kontrollida – ilma, et peaksid rakendust avama. App Functions are available on **Google-flavor Android builds only**.

> ℹ️ **Note:** This is separate from the in-app **Chirpy** assistant. Rakenduse funktsioonid lasevad _süsteemi_ TI assistendil kärgvõrgu kallal tegutseda; Chirpy on vestlusassistent Meshtasticu rakenduses endas.

## Luba rakenduse funktsioonid

Control App Functions from **Settings → System AI**. The screen has:

- **Pealüliti** sildiga **"Luba tehisintellektile juurdepääs"** alapealkirjaga _"Luba süsteemi TI assistentidel (nt Gemini) kärgvõrgu funktsioone avastada ja kasutada"_. Väljalülitatud olekus pole süsteemile ühtegi funktsiooni avatud.
- **Iga funktsiooni jaoks on eraldi lüliti**, nii et saad kuvada ainult soovitud võimalusi.

Funktsioonid on rühmitatud kirjutamis- (Write) sektsiooni (funktsioonid, mis midagi muudavad või andmeid kärgvõrgule saadavad) ja lugemis- (Read) sektsiooni (funktsioonid, mis ainult teavet tagastavad).

![Rakenduse funktsioonide ekraan põhi- ja funktsioonipõhiste lülititega](../../assets/screenshots/app-functions_settings.png)

### Kirjutamis funktsioonid

| Funktsioon       | What it does                                                                                                |
| ---------------- | ----------------------------------------------------------------------------------------------------------- |
| **Send Message** | Saadab kontaktile (otsesõnum) või kanalile tekstisõnumi, kuni 237 baiti. |

### Lugemis funktsioonid

| Funktsioon              | What it returns                                                                     |
| ----------------------- | ----------------------------------------------------------------------------------- |
| **Küsi kärgvõrgu olek** | Whether you're connected to a radio, and how many nodes are online. |
| **Get Node List**       | Teie kärgvõrgu sõlmede loend.                                       |
| **Saa kanali info**     | Teave kanalite kohta.                                               |
| **Küsi kärgvõrgu olek** | Ühendatud raadio olek.                                              |
| **Get Node Details**    | Detailed information about a specific node.                         |
| **Get Recent Messages** | Recent messages from your conversations.                            |
| **Get Unread Summary**  | A summary of unread messages.                                       |
| **Get Mesh Metrics**    | Telemetry and metrics from your mesh.                               |

## Privacy

> 🔒 **Privaatsus:** Funktsioon **Saada sõnum** võimaldab assistendil sinu nimel sinu kärgvõrku sõnumeid saata. Luba ainult need funktsioonid, mille kasutamist sa assistendile usaldad. Lugemisfunktsioonid avaldavad abilisele sõlme-, sõnumi- ja mõõdikuandmeid – lubage jagada ainult seda, mida soovite. Igal funktsioonil on oma lüliti ja peamine lüliti lülitab need kõik korraga välja.

## Seotud teemad

- [Sõnumid ja kanalid](user/messages-and-channels)— sõnumite saatmine otse rakenduses
- [Sõlmed](nodes) — sõlmede loend, millest lugemisfunktsioonid ammutavad
- [Node Metrics](node-metrics) — the telemetry behind Get Mesh Metrics
