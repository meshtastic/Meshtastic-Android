---
title: Sõnumid ja kanalid
parent: User Guide
nav_order: 3
last_updated: 2026-07-11
description: Saada ja võta vastu sõnumeid, halda kanaleid, konfigureeri krüpteerimist ning kasuta kiirvestlust, reaktsioone ja sõnumitoiminguid.
aliases:
  - kanalid
  - direct-messages
  - messaging
  - conversations
---

# Sõnumid ja kanalid

Meshtastic toetab kahte suhtlusrežiimi: **kanalite levitamine** ja **otsesõnumid**.

## Kanal

Kanalid on jagatud suhtlusgrupid. Kõik sama kanalivõtmega seadistatud sõlmed saavad sellel kanalil sõnumeid lugeda ja saata.

### Vaikekanal

Igal Meshtastic seadmel on vaikimisi **PikkKauge** kanal. See on krüpteerimata kanal, mida kasutatakse üldiseks võrgusuhtluseks.

### Kanali turvalisus

Kanalid toetavad mitut krüpteerimistaset:

| Ikoon | Security Level                       | Kirjeldus                                                                                                                                 |
| ----- | ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
| 🔒    | PSK (256-bit AES) | Fully encrypted with a strong pre-shared key. Only nodes with the matching key can read messages.         |
| 🔐    | PSK (128-bit AES) | Encrypted with a shorter key. Secure for most uses but 256-bit is preferred for sensitive data.           |
| 🔓    | Default / Open                       | Uses the well-known default key. **Iga Meshtastic seade** saab sama eelseadistusega neid sõnumeid lugeda. |
| ⚠️    | Insecure + Position                  | Ava kanal, mis levitab ka sinu GPS asukohta. Use with caution in public meshes.                           |

> 🔒 **Turvanõuanne:** Privaatse suhtluse jaoks konfi alati unikaalne PSK. Vaikimisi kanal on tahtlikult avatud, et uued kasutajad saaksid kärgvõrku avastada – aga kõige tundliku jaoks peaksite looma eraldi krüptitud kanali.

### Lisa kanal

1. Mine **Sätted → Kanalid**.
2. Puuduta **Lisa kanal** või skanni QR-koodi.
3. Konfigureeri kanali nime ja krüpteerimisvõtit.
4. Jaga kanali URL-i/QR-koodi teistega, kes seda vajavad.

Kanali puudutamine kuvab selle üksikasjad ja jagamisvalikud.

## Direct Messages

Direct messages (DMs) are point-to-point encrypted communications between two specific nodes.

### Sending a Direct Message

1. Open the **Messages** tab.
2. Vali kontaktide loendist sõlm või puuduta sõlme loendis.
3. Tippi oma sõnum ja puuduta nuppu **Saada**.

### Sõnumi olek

A status label appears under **your own** outgoing messages only (incoming messages from others show no status label):

| Olek                                | Tähendus                                                                                                                             |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| Sending…                            | Queued or already handed to the radio, not yet resolved either way (queued and en-route both show this same text) |
| Delivered to recipient              | The strongest confirmation for a direct message — an acknowledgment came back                                                        |
| Kärgvõrku kohale jõudnud            | Kanali leviedastuse puhul jõuab sõnum kärgvõrku (leviedastustel puudub saajapõhine kinnitus)                      |
| Relayed, not confirmed by recipient | For a direct message, shown in a warning color — the message was relayed but no acknowledgment has come back yet                     |
| Marsruutimine SF++ ahela kaudu…     | Being routed/buffered by the Store & Forward Plus Plus chain                                                     |
| Kinnitatud SF++ ahel                | Confirmed delivered via the SF++ chain                                                                                               |
| Tõrge                               | Delivery failed — tap the status for the specific reason (see Delivery Errors below)                              |

### Delivery Errors

When a message fails to deliver, the error indicator shows what went wrong:

| Tõrge            | Meaning                                  | What to Do                                                                                                                                                                  |
| ---------------- | ---------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| No Route         | No path exists to the destination node   | The recipient may be offline or out of mesh range. Try later or move closer.                                                                |
| Got NAK          | Järgmise-hüppe sõlm keeldus edastamast   | The relay node may be congested. Wait and retry.                                                                                            |
| Aegunud          | No acknowledgment within retry window    | The recipient may be just out of range. Proovi hüppe limiiti suurendada või paremasse asukohta minna.                                       |
| Liidest pole     | No radio interface available to send     | Kontrolli, kas raadio on ühendatud ja kanal on seadistatud.                                                                                                 |
| Max Retransmit   | All retry attempts exhausted             | The mesh path is unreliable. Proovi teist kanalit või oota, kuni olukord paraneb.                                                           |
| Kanalit pole     | Sihtkanalit pole olemas                  | Veendu, et mõlemal sõlmel oleks sama kanali seadistus.                                                                                                      |
| Too Large        | Sõnum ületab maksimaalset sõnumi mahtu   | Shorten your message (max ~200 characters).                                                                              |
| Vastust pole     | Node received message but didn't respond | The recipient's radio may be busy or in low-power sleep mode.                                                                                               |
| Duty Cycle Limit | Regional airtime limit reached           | Your radio has used its allowed transmit time. Wait for the duty cycle window to reset (typically 1 hour in EU regions). |
| Vigane päring    | Malformed or invalid message             | This usually indicates a software bug. Try restarting the app.                                                                              |

> 💡 **Vihje:** Enamik kohaletoimetamise vigu laheneb iseenesest. If a node is intermittently reachable, the mesh will retry. For persistent "No Route" errors, check that intermediate Router nodes are online.

## Message Features

### Quick Chat

Eelsalvestatud sõnumid kiireks suhtluseks:

- Juurdepääs läbi sõnumi sisestamise alas oleva kiirvestluse nupu
- Choose from built-in phrases or custom messages
- Customize quick chat messages in **Settings → Quick Chat**
- Kasulik, kui trükkimine on ebapraktiline (kindad, väike ekraan, kiireloomuline)

![Quick chat option](../../assets/screenshots/messages_quick_chat.png)

Igal kiirvestluse kirjel on lühike **Nimi** (nupu silt), **Sõnum**, mille see lisab, ja **Saada kohe** lüliti – kui see on lubatud, saadetakse nupu puudutamisel sõnum kohe, selle asemel et see sisestada sisestusväljale redigeerimiseks:

![New quick chat dialog with name, message, and instantly-send toggle](../../assets/screenshots/messages_edit_quick_chat.png)

Kanalite loendis kuvatakse iga kanal koos selle viimase sõnumi eelvaatega.

### Searching Messages

You can search the full history of any conversation directly from the chat screen:

1. Ava vestlus (kanal või otsesõnum).
2. Puuduta ülemisel ribal **otsinguikooni**.
3. Type into the **Search messages…** field. The search runs as you type, across all stored messages in that conversation.
4. Use the **N / M** result counter and the **previous / next arrows** to jump between matches, which are highlighted in the conversation.

![Message search bar with result counter and previous/next arrows](../../assets/screenshots/messages_search_bar.png)

> 💡 **Vihje:** Otsing toimub täisteksti põhjal ja jääb vestlusse, kust sa selle avasid – see ei otsi teistest kanalitest ega kontaktide hulgast. It matches against the messages already stored on your device, so it works fully offline.

### Message Bubbles

Messages appear as chat bubbles — sent messages on the right, received messages on the left. Each bubble shows the sender, timestamp, and delivery status. Messages with replies include a quoted preview of the original message above the response.

### Teksti vormindamine

Messages support lightweight inline **Markdown**. Received messages render the styling with the syntax characters removed:

| Tüüp          | Syntax                         | Renders as           |
| ------------- | ------------------------------ | -------------------- |
| Bold          | `**bold**`                     | **bold**             |
| Italic        | `*italic*`                     | _italic_             |
| Strikethrough | `~~strike~~`                   | ~~strike~~           |
| Inline code   | `` `code` ``                   | monospace `code`     |
| Link          | `[label](https://example.com)` | a tappable **label** |

When composing, focus the message field and type at least three characters to reveal a **formatting toolbar** below the input. Select text and tap a style to wrap it (tap again to remove it); with no selection, a style inserts an empty pair with the cursor between the markers. Linginupp avab URL-i sisestamiseks dialoogiboksi. As you type, the draft styles live in the field while the underlying text keeps its Markdown characters.

> 💡 **Tip:** Formatting is carried as literal characters on the mesh — the same bytes iOS sends. Clients that don't support Markdown (older apps, plain firmware clients) will show the raw `**`/`~~` characters. URL-id, e-posti aadressid ja telefoninumbrid lingitakse endiselt automaatselt olenemata sellest, kas kasutate Markdowni või mitte.

### Mentions

Type `@` while composing to mention a node — a picker suggests matching contacts as you type. In a received message, a mention appears as a highlighted chip showing the node's name; tap it to jump straight to that node's detail page.

### Reactions

React to messages with emoji:

- **Long-press** a message to open the actions menu
- Emotikoni valimiseks puuduta **Lisa reaktsioon**
- Reactions appear below the message bubble
- Multiple users can react to the same message
- React to your own messages or others' messages

![Emoji reaction badges displayed beneath a message](../../assets/screenshots/messages_reaction.png)

> 💡 **Vihje:** Reaktsioonid on kerged – need kasutavad täistekstisõnumitega võrreldes minimaalselt võrgu ribalaiust.

### Message Actions

Juurdepääsuks vajuta pikalt mis tahes sõnumit:

- **Copy** — copy message text to clipboard
- **Reply** — quote the message in your response
- **React** — add an emoji reaction
- **Tõlgi** – tõlgi vastuvõetud sõnum oma seadme keelde ja vaheta algse ja tõlgitud teksti vahel (ainult Google Play versioon; kasutab seadmesisest tõlget)
- **Delete** — remove a message you sent (local deletion)

### Message Priority

Messages are queued and transmitted based on priority:

1. Emergency/alert messages (highest)
2. Direct messages
3. Kanalite levitamine (madalaim)

### Message Limits

- **Maximum length:** 200 bytes (approximately 200 characters for ASCII text)
- **Rate limiting:** The mesh enforces airtime fairness; heavy message volume may be throttled
- **Delivery:** Messages are retried automatically if no acknowledgment is received

## Best Practices

- Kasuta kanaleid grupi koordineerimiseks
- Use direct messages for private person-to-person communication
- Keep messages short — mesh bandwidth is limited
- Configure encryption for sensitive communications

## Related Topics

- [Sõlmed] (nodes) — otsesõnumi alustamiseks puuduta sõlme
- [Seaded — Raadio ja kasutaja](settings-radio-user) — kanalite krüpteerimise ja eelseadete konfimine
- [MQTT](mqtt) — silda kanali sõnumid internetti
- [Kanali konf](https://meshtastic.org/docs/configuration/radio/channels) — üksikasjalikud kanali seaded leiate aadressilt meshtastic.org

---

