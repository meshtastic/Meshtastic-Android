---
title: Rakenduse funktsioonid
parent: Kasutaja juhis
nav_order: 19
last_updated: 2026-08-28
description: Ava kärgvõrgu funktsioonid Androidi süsteemile ja seadme tehisintellektil põhinevatele assistentidele (nt Gemini), et nad saaksid kärgvõrgu töövooge käivitada rakendust avamata.
aliases:
  - rakenduse funktsioonid
  - süsteemi-ti
  - gemini
  - assistent
---

# Rakenduse funktsioonid

Rakendusfunktsioonid avaldavad Meshtasticu võimalused Androidi süsteemile ja seadmesisestele TI assistentidele (näiteks Gemini) Androidi rakenduste funktsioonide API kaudu. Kui need on lubatud, saab assistent sinu eest kärgvõrgu töövooge avastada ja käivitada – näiteks sõnumi saata või kärgvõrgu olekut kontrollida – ilma, et peaksid rakendust avama.

> ℹ️ **Note:** App Functions are available on **Google-flavor Android builds only**.
>
> This is separate from the in-app **Chirpy** assistant. Rakenduse funktsioonid lasevad _süsteemi_ TI assistendil kärgvõrgu kallal tegutseda; Chirpy on vestlusassistent Meshtasticu rakenduses endas.

## Luba rakenduse funktsioonid

Rakenduse funktsioone saab juhtida menüüst **Seaded → Süsteemi TI** (rakenduse sisemine ekraan on tähistatud kui „Süsteemi TI“). The screen has:

- **Pealüliti** sildiga **"Luba tehisintellektile juurdepääs"** alapealkirjaga _"Luba süsteemi TI assistentidel (nt Gemini) kärgvõrgu funktsioone avastada ja kasutada"_. Väljalülitatud olekus pole süsteemile ühtegi funktsiooni avatud.
- **Iga funktsiooni jaoks on eraldi lüliti**, nii et saad kuvada ainult soovitud võimalusi.

Funktsioonid on rühmitatud kirjutamis- (Write) sektsiooni (funktsioonid, mis midagi muudavad või andmeid kärgvõrgule saadavad) ja lugemis- (Read) sektsiooni (funktsioonid, mis ainult teavet tagastavad).

![Rakenduse funktsioonide ekraan põhi- ja funktsioonipõhiste lülititega](../../assets/screenshots/app-functions_settings.png)

### Kirjutamis funktsioonid

| Funktsioon       | What it does                                                                                                |
| ---------------- | ----------------------------------------------------------------------------------------------------------- |
| **Send Message** | Saadab kontaktile (otsesõnum) või kanalile tekstisõnumi, kuni 237 baiti. |

### Lugemis funktsioonid

| Funktsioon              | What it returns                                             |
| ----------------------- | ----------------------------------------------------------- |
| **Küsi kärgvõrgu olek** | Kärgvõrgu üldine seisund.                   |
| **Get Node List**       | Teie kärgvõrgu sõlmede loend.               |
| **Saa kanali info**     | Teave kanalite kohta.                       |
| **Get Device Status**   | Status of your connected radio.             |
| **Get Node Details**    | Detailed information about a specific node. |
| **Get Recent Messages** | Recent messages from your conversations.    |
| **Get Unread Summary**  | A summary of unread messages.               |
| **Get Mesh Metrics**    | Telemetry and metrics from your mesh.       |

## Privacy

> 🔒 **Privacy:** The **Send Message** function lets an assistant send messages to your mesh on your behalf. Only enable functions you trust the assistant to use. The read functions expose node, message, and metric data to the assistant — enable only what you're comfortable sharing. Each function has its own toggle, and the master toggle turns all of them off at once.

## Related Topics

- [Messages & Channels](messages-and-channels) — sending messages directly in the app
- [Nodes](nodes) — the node list the read functions draw from
- [Node Metrics](node-metrics) — the telemetry behind Get Mesh Metrics

---

