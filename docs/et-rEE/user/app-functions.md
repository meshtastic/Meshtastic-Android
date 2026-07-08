---
title: App Functions
parent: Kasutaja juhis
nav_order: 19
last_updated: 2026-06-11
description: Expose mesh capabilities to the Android system and on-device AI assistants (e.g. Gemini) so they can run mesh workflows without opening the app.
aliases:
  - app-functions
  - süsteemi-ti
  - gemini
  - assistant-functions
---

# App Functions

Rakendusfunktsioonid avaldavad Meshtasticu võimalused Androidi süsteemile ja seadmesisestele tehisintellekti assistentidele (näiteks Gemini) Androidi rakenduste funktsioonide API kaudu. Kui need on lubatud, saab assistent sinu eest kärgvõrgu töövooge avastada ja käivitada – näiteks sõnumi saata või kärgvõrgu olekut kontrollida – ilma, et peaksid rakendust avama.

> ⚠️ **Märkus:** Rakenduse funktsioonid on saadaval ainult **Google'i-tüüpi Androidi versioonides**.

> ⚠️ **Märkus:** See on eraldi rakenduse sisesest **Chirpy** assistendist. Rakenduse funktsioonid lasevad _süsteemi_ tehisintellekti assistendil kärgvõrgu kallal tegutseda; Chirpy on vestlusassistent Meshtasticu rakenduses endas.

## Enabling App Functions

Rakenduse funktsioone saab juhtida menüüst **Seaded → Süsteemi TI** (rakenduse sisemine ekraan on tähistatud kui „Süsteemi TI“). The screen has:

- **Pealüliti** sildiga **"Luba tehisintellektile juurdepääs"** alapealkirjaga _"Luba süsteemi tehisintellekti assistentidel (nt Gemini) kärgvõrgu funktsioone avastada ja kasutada"_. When off, no functions are exposed to the system.
- An **individual toggle for each function**, so you can expose only the capabilities you want.

The functions are grouped into a **Write** section (functions that change something or send data to your mesh) and a **Read** section (functions that only return information).

![App Functions screen with master and per-function toggles](../../assets/screenshots/app-functions_settings.png)

### Write Functions

| Function         | What it does                                                                                                |
| ---------------- | ----------------------------------------------------------------------------------------------------------- |
| **Send Message** | Saadab kontaktile (otsesõnum) või kanalile tekstisõnumi, kuni 237 baiti. |

### Read Functions

| Function                | What it returns                                             |
| ----------------------- | ----------------------------------------------------------- |
| **Get Mesh Status**     | Overall mesh status.                        |
| **Get Node List**       | The list of nodes on your mesh.             |
| **Saa kanali info**     | Teave kanalite kohta.                       |
| **Get Device Status**   | Status of your connected radio.             |
| **Get Node Details**    | Detailed information about a specific node. |
| **Get Recent Messages** | Recent messages from your conversations.    |
| **Get Unread Summary**  | A summary of unread messages.               |
| **Get Mesh Metrics**    | Telemetry and metrics from your mesh.       |

## Privacy

> 🔒 **Privaatsus:** Funktsioon **Saada sõnum** võimaldab assistendil sinu nimel sinu kärgvõrku sõnumeid saata. Only enable functions you trust the assistant to use. The read functions expose node, message, and metric data to the assistant — enable only what you're comfortable sharing. Igal funktsioonil on oma lüliti ja peamine lüliti lülitab need kõik korraga välja.

## Related Topics

- [Sõnumid ja kanalid](user/messages-and-channels)— sõnumite saatmine otse rakenduses
- [Nodes](nodes) — the node list the read functions draw from
- [Node Metrics](node-metrics) — the telemetry behind Get Mesh Metrics

---

