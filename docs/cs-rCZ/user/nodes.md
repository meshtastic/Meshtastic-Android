---
title: Uzly
parent: Uživatelská příručka
nav_order: 4
last_updated: 2026-09-19
description: Procházet, filtrovat a třídit uzly sítě – zobrazit podrobnosti, kvalitu signálu, role a rychlé akce.
aliases:
  - node-list
  - mesh-nodes
  - peers
  - hop-histogram
---

# Uzly

The Nodes screen lists every node visible on your mesh.

## Node list

The node list shows every node your node has heard, including:

- **Node name** — user-configured long name
- **Short name** — 4-character identifier
- **Signal quality** — SNR, RSSI, and a quality word, shown only for nodes your node heard directly. In the Complete layout a node reached through a relay shows its hop count here instead; a node heard only over MQTT shows neither
- **Last heard** — time since last communication
- **Distance** — estimated distance (if positions are shared)
- **Battery** — remote node battery level (if telemetry is enabled)

### Choosing what the list shows

The list has two densities, set at **Settings → Node Layout**. **Complete** shows every field a node has reported and hides the ones it hasn't. **Compact** fits more nodes on screen and lets you pick the fields yourself — **Power**, **Last Heard Time**, **Relative Last Heard Time**, **Distance and Bearing**, **Hops Away**, **Signal (Direct Only)**, **Channel**, and **Device & Role**. The **Environment Metrics** toggle applies to both densities. A preview above the toggles shows the effect before you leave the screen.

### Node Status indicators

| Indikátor                                             | Význam                                                                              |
| ----------------------------------------------------- | ----------------------------------------------------------------------------------- |
| Zelený - čas posledního příjmu                        | Uzel byl slyšet během posledních 2 hodin                                            |
| Běžný - čas posledního příjmu                         | Uzel nebyl slyšet déle než 2 hodiny                                                 |
| Orange last-heard time with a crossed-out signal icon | Not heard since your node's LoRa settings changed, so it can't be reached from here |
| Struck-through name                                   | Node you have ignored                                                               |
| ⭐ Oblíbený                                            | Uzel jste označili jako oblíbený.                                   |

There is no separate "away" tier, but the orange unreachable state takes precedence over the
green one: a node can be online and still be unreachable on your current settings. Nodes known
only over MQTT are never shown as unreachable.

### Role uzlu

Uzlům lze nastavit různé role, které ovlivňují jejich chování v mesh síti:

| Role              | Popis                                                                                                                                                         |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Client            | Standardní uzel pro koncové uživatele                                                                                                                         |
| Client Base       | Provoz z oblíbených uzlů považuje za prioritu Router Late; veškerý ostatní provoz za Client                                                                   |
| Client Mute       | Přijímá, ale nepřeposílá                                                                                                                                      |
| Client Hidden     | Stejně jako Client Mute, navíc skryje uzel ze seznamu uzlů                                                                                                    |
| Router            | Upřednostňuje přeposílání zpráv a zůstává vzhůru, aby je mohl přeposílat                                                                                      |
| Router Late       | Infrastrukturní uzel, který jednou přepošle paket, ale až po všech ostatních režimech (poskytuje doplňkové pokrytí)                        |
| ~~Router Client~~ | ⚠️ **Zastaralé** (odstraněno ve firmwaru 2.3.15) – již nelze vybrat; použijte místo něj Router nebo Client |
| ~~Repeater~~      | ⚠️ **Zastaralé** (odstraněno ve firmwaru 2.7.11) – již nelze vybrat; použijte místo něj Router             |
| Tracker           | Optimalizováno pro pravidelné odesílání polohy                                                                                                                |
| Senzor            | Optimalizováno pro hlášení telemetrie                                                                                                                         |
| TAK               | Spolupracuje se systémy TAK (odesílá/přijímá CoT)                                                                                          |
| TAK Tracker       | Pouze odesílání polohy TAK                                                                                                                                    |
| Lost and Found    | Sends its position to the default channel as a text message at regular intervals, to help recover a lost node                                                 |

### Choosing a role

Většina uživatelů by měla ponechat výchozí roli **Client**. Zvažte jinou roli, pokud:

- \*\*Router \*\*— Máte uzel na pevném, vyvýšeném místě se spolehlivým napájením (střecha, vrchol kopce). Routery zůstávají nepřetržitě aktivní, aby přeposílaly zprávy ostatních, a jsou klíčové pro rozšiřování pokrytí mesh sítě. Don't use Router on battery-powered handheld nodes.
- **Router Late** — Infrastrukturní uzel, který vždy jednou přepošle paket, ale až poté, co dostaly prostor všechny ostatní směrovací režimy. Poskytuje doplňkové pokrytí místním skupinám uzlů, aniž by konkuroval hlavním routerům.
- **Client Base** — Zachází s provozem z/na vaše oblíbené uzly s prioritou Router Late (zajišťuje těmto zprávám dodatečné přeposílání), zatímco vše ostatní zpracovává jako běžný Client.
- **Client Mute** — Chcete přijímat provoz mesh sítě, ale nechcete se podílet na jeho přeposílání. Useful for monitoring-only nodes or to reduce congestion in dense areas.
- **Tracker** — An unattended node whose sole purpose is broadcasting its GPS position (e.g., a vehicle, pet, or asset). Sleeps between broadcasts to conserve battery.
- **Sensor** — An unattended node reporting environmental telemetry (temperature, humidity, air quality). Podobná spotřeba energie jako Tracker.
- **TAK / TAK Tracker** – Potřebujete pouze pro spolupráci se systémy ATAK/WinTAK. Podrobnosti viz [TAK integrace](tak).

> 💡 **Tip:** Mesh síť funguje nejlépe, když je většina uzlů v roli **Client** nebo **Router**. Příliš mnoho uzlů Client Mute snižuje odolnost mesh sítě; příliš mnoho routerů v hustě osídlené oblasti může způsobit zahlcení. Dobrým orientačním pravidlem je jeden Router na 5–10 uzlů Client ve vaší oblasti.

### Security indicators

Each node carries one security icon beside its name in the node list. Tap it to read what it means, and choose **Show All Meanings** in that dialog for the full legend. The detail screen shows the same state in words, as a **Security** row that opens the same dialog.

| Icon                                                      | Meaning                                                                                                                                                                                                                                                                                     | Shown for                                                                                         |
| --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| Person with a shield check (green)     | **Verified contact** — you verified this node's key in person by exchanging contact QR codes, so its identity is confirmed. The strongest trust the list shows. Your own connected node carries it too                                                      | Any firmware version                                                                              |
| Nodes icon with a shield check (green) | **Signed node** — this node signs its broadcasts with its identity key, so its identity is consistent over time, but you haven't verified it in person. On firmware 2.8 the icon appears from the version alone, before any signed broadcast has been heard | Firmware 2.8 or newer, and any node whose signed broadcast your node has verified |
| 🔒 Closed lock                                            | A public key is on file and matches, so direct messages to this node are encrypted                                                                                                                                                                                                          | Firmware before 2.8, or no reported version                                       |
| 🔓 Open lock                                              | No public key has been received for this node, so it can't be direct messaged — use **Request User Info** on the node detail page to ask for one                                                                                                                                            | Firmware before 2.8, or no reported version                                       |
| ⚠️ Mismatch                                               | **Public key mismatch** — a different key arrived for this node after one was stored. Investigate before trusting                                                                                                                                                           | Any firmware version                                                                              |

Every node on firmware 2.8 or newer signs its broadcasts, so on that firmware the signed state is the baseline and the locks aren't shown.

Direct messages always use public-key encryption, so your node needs the other node's public key before it can send one. It refuses the send rather than falling back to channel encryption. Keys arrive inside node info, which is why an open lock usually clears itself once that node is heard from properly.

A mismatch never replaces the key you already hold. The app keeps the first key it recorded and refuses the new one, as the firmware does, so a stray or hostile node info can't silently break encrypted messaging to a contact. To clear a mismatch, first confirm through another trusted channel that the key change was intentional — a factory reset causes one. Then touch & hold the node, choose **Remove**, and let the two nodes exchange keys again the next time yours hears it.

## Rychlé akce

From the node list, you can:

- **Tap** a node to view its detail page
- **Touch & hold** for quick actions:
  - Mark/remove favorite
  - Mute/unmute notifications
  - Send a direct message
  - Trace route
  - Ignore/unignore
  - Odstranit

Touch & hold **your own node** instead and you get one action, **Update status**, which opens the
User settings screen with the cursor already in the Status Message field. It only appears while the
node is connected and running firmware 2.8 or newer — see
[Settings — Radio & User](settings-radio-user.md) for the field itself.

## Sharing a contact

On a node's detail screen, tap **Share Contact** to produce a link and a QR code for that node. From the same dialog, **Share link** opens the Android share sheet (on desktop it copies the link instead), **Write to NFC tag** saves it to a writable NFC tag, and **Copy** puts it on the clipboard. While that dialog is open and in front of you, the phone also offers the same link to any NFC reader, so someone can take the contact by tapping their phone against yours with no tag involved.

Sharing your own contact this way marks it as verified in person, so whoever imports it sees the **Verified contact** icon rather than the signed one. Relaying someone else's contact passes on only what your app had already recorded about them.

To add someone else's contact, use the import button on the node list and choose **Scan Shared Contact QR Code**, **Scan Shared Contact NFC**, or **Input Shared Contact URL**. The app asks you to confirm with **Import Shared Contact?**, and warns you when the contact is one you already have.

## Filtering & sorting

### Text search

Type in the search field to filter nodes by name or short name. The filter updates in real time as you type.

### Filter toggles

| Filtr                       | Popis                                                                                                                                                                                                                                                                                                                        |
| --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Hide offline nodes**      | Show only nodes heard within the last 2 hours                                                                                                                                                                                                                                                                                |
| **Only show direct nodes**  | Show only nodes your node heard directly, with no relay in between                                                                                                                                                                                                                                                           |
| **Include unknown**         | Show nodes that haven't sent user info yet. **On by default**, so a node heard before its info arrives stays visible; these carry a badge marking them incomplete, and can't be direct messaged until their user info brings a public key                                                                    |
| **Exclude infrastructure**  | Hide infrastructure-role nodes (Router, Router Late, Client Base, and legacy Repeater nodes) and any node that can't be messaged, whatever its role                                                                                                                                                       |
| **Exclude MQTT**            | Hide nodes heard only via MQTT internet bridge                                                                                                                                                                                                                                                                               |
| **Hide unheard nodes**      | Hide nodes your own node hasn't heard since its LoRa settings changed. Off by default, and it does nothing on firmware that doesn't report whether a node was heard on the current settings                                                                                                                  |
| **Signed only**             | Show only nodes whose signed broadcasts your node has actually heard and verified. Stricter than the icon: a node on 2.8 shows as signed by its firmware version before any signed broadcast arrives, and a contact verified in person on older firmware isn't signed at all |
| **Encrypted only**          | Show nodes with a matching public key on file, the key an encrypted direct message needs. A node with a key mismatch is excluded                                                                                                                                                                             |
| **Only show ignored Nodes** | Replace the list with the nodes you have ignored. Every other node is hidden while this is on, and a banner appears at the top of the list to take you back                                                                                                                                                  |

### Nodes not heard on your current Settings

When your node's LoRa settings change — a different preset, region or frequency slot — nodes heard
under the old settings are still in the list but can no longer be reached. The app marks them with
an orange last-heard time and a crossed-out signal icon, and shows a banner at the top of the list:
**N nodes not heard on your current LoRa settings**.

The banner offers two actions:

- **Keep** dismisses the banner and leaves every node in place.
- **Remove** deletes those nodes from the list. Your favorites and your own node are never removed.

**Remove** is a bulk delete and there is no undo, so use **Keep** if you expect to switch back to
the old settings. Removed nodes reappear if your node hears them again.

The **Hide unheard nodes** filter does the same hiding without deleting anything.

### Sort options

| Sort                                          | Popis                                                              |
| --------------------------------------------- | ------------------------------------------------------------------ |
| **Last heard**                                | Most recently heard nodes first                                    |
| **A-Z**                                       | Sorted by node long name                                           |
| **Distance**                                  | Nearest nodes first (requires position sharing) |
| **Hops away**                                 | Fewest relay hops first                                            |
| **Channel**                                   | Grouped by channel index                                           |
| **via MQTT**                                  | Grouped by MQTT vs. node-heard                     |
| **via Favorite** (default) | Favorited nodes first, then the rest                               |

## Nodes per hop

Tap the hop-histogram icon in the node list's app bar to open a bar chart of how many nodes sit at each hop distance (0 = direct, 1 = one relay away, and so on). Filter the chart to a **last heard** window — **All**, **1 Hour**, **8 Hours** or **24H** — to see how the mesh looks right now versus over a longer period. It's a quick way to gauge how busy and spread out your local mesh is.

## Node detail

Tapping a node opens the detail view with comprehensive information. See [Node Metrics](node-metrics) for full details on metrics and telemetry.

Signal quality is rated against your modem preset. The same SNR can be good on a long-range preset and poor on a faster one. Traceroute and neighbor-info SNR colors use that preset too. RSSI text has its own strength colors; it affects the quality rating only when a noise-floor reading is also available.

The Details card carries the node's short name, role, IDs, last heard time, hops away, uptime, and its SNR and RSSI:

![Node detail section](../../assets/screenshots/nodes_detail_section.png)

Inline status indicators show key metrics at a glance:

| Indicator        | Screenshot                                                    |
| ---------------- | ------------------------------------------------------------- |
| Signal quality   | ![Signal](../../assets/screenshots/nodes_signal_info.png)     |
| Battery level    | ![Battery](../../assets/screenshots/nodes_battery_info.png)   |
| Hop count        | ![Hops](../../assets/screenshots/nodes_hops_info.png)         |
| Naposledy slyšen | ![Last heard](../../assets/screenshots/nodes_last_heard.png)  |
| Vzdálenost       | ![Distance](../../assets/screenshots/nodes_distance_info.png) |

### Hardware support status

When a node's hardware is recognized, the detail view names the device and marks its support status, taken from the Meshtastic device registry rather than the app:

| Mark                                 | Význam                                                                                                                                          |
| ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Rosette (green)   | **Supported** — hardware the Meshtastic project actively supports                                                                               |
| Wrench (sky blue) | **Independent maker hardware** — built and tested by an independent maker, shown as its own rung whether or not the project has promoted it yet |
| Unverified (red)  | **Supported by Meshtastic Community** — hardware the project does not actively support, including legacy boards                                 |

The label always accompanies the mark, so the status is never conveyed by colour alone.

### Device links ("I want one")

When a node's hardware is recognized, the detail view shows a collapsible **"I want one"** section linking to places to buy or learn more about that device: the vendor's product page, product variants, and regional marketplace listings (such as AliExpress, Amazon, and supported retailers), filtered to your country. Each link opens through the `msh.to` redirect service. Devices with no matching links don't show the section.

A full, browsable directory of every link is also available at **Settings → Device Links**. The item is hidden while you have Settings open for a remote node.

Some of these are affiliate links. Both places say so above the links: product links may be affiliate links, and purchases may earn Meshtastic a commission.

## When no nodes appear

The list stays empty until your node hears another node.

- **No device connected** — the app isn't connected to a node. See [Connections](connections).
- **Searching for nodes** — the node is connected and listening, but nothing has arrived yet. Check that its region and modem preset match the mesh around you, and leave **Include unknown** on so a node that hasn't yet sent its name still appears. See [Settings — Radio & User](settings-radio-user).
- A node you expect is missing — check the filter toggles. **Only show direct nodes**, **Exclude MQTT**, and **Exclude infrastructure** each hide a whole category of node.

## Související témata

- [Node Metrics](node-metrics) — detailed telemetry dashboards for each node
- [Messages & Channels](messages-and-channels) — send a direct message to a node
- [Map & Waypoints](map-and-waypoints) — view node positions geographically
- [Local Mesh Discovery](discovery) — traceroute and neighbor info for topology exploration
- [Signal Meter](signal-meter) — understand what the signal bars mean
