---
title: Sõnumid ja kanalid
parent: Kasutusjuhend
nav_order: 3
last_updated: 2026-08-27
description: Saada ja võta vastu sõnumeid, halda kanaleid, konfigureeri krüpteerimist ning kasuta kiirvestlust, reaktsioone ja sõnumitoiminguid.
aliases:
  - kanalid
  - otsesõnumid
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

| Ikoon | Turvatase                            | Kirjeldus                                                                                                                                 |
| ----- | ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- |
| 🔒    | PSK (256-bit AES) | Täielikult krüpteeritud eel-jagatud tugeva võtmega. Ainult sobiva võtmega sõlmed saavad sõnumeid lugeda.  |
| 🔐    | PSK (128-bit AES) | Krüpteeritud lühema võtmega. Secure for most uses but 256-bit is preferred for sensitive data.            |
| 🔓    | Vaikimisi / Avatud                   | Kasutab teada-tuntud vaikevõtit. **Iga Meshtastic seade** saab sama eelseadistusega neid sõnumeid lugeda. |
| ⚠️    | Insecure + Position                  | Ava kanal, mis levitab ka sinu GPS asukohta. Use with caution in public meshes.                           |

> 🔒 **Security:** Always configure a unique PSK for private communications. Vaikimisi kanal on tahtlikult avatud, et uued kasutajad saaksid kärgvõrku avastada – aga kõige tundliku jaoks peaksite looma eraldi krüptitud kanali.

### Lisa kanal

1. Mine **Sätted → Kanalid**.
2. Tap the **+** button to add a channel, or import one by scanning a channel QR code.
3. Konfigureeri kanali nime ja krüpteerimisvõtit.
4. Jaga kanali URL-i/QR-koodi teistega, kes seda vajavad.

Kanali puudutamine kuvab selle üksikasjad ja jagamisvalikud.

## Otsesõnumid

Otsesõnumid (DM-id) on punkt-punkti krüptitud suhtlus kahe konkreetse sõlme vahel.

### Sending a Direct Message

1. Ava vahekaart **Sõnumid**.
2. Vali kontaktide loendist sõlm või puuduta sõlme loendis.
3. Tippi oma sõnum ja puuduta nuppu **Saada**.

### Managing the Conversation List

The **Messages** tab lists your conversations. Each row carries what you need to triage it at a
glance, and the list itself is directly actionable:

- **Unsent drafts survive.** Type into a conversation and leave without sending, and the text is
  still there when you come back. The row shows it as `Draft: …` in place of the last message —
  an unsent draft is the thing the row is waiting on _you_ for.
- **Unread badge.** A count sits on the row until you open the conversation.
- **Swipe right to mute** (swipe again to unmute) and **swipe left to delete**. Deleting asks
  first; muting shows a snackbar with **Undo**.
- **Long-press to select** one or more conversations, then use the action bar to **Pin**,
  **Mark unread**, mute or delete them together. Pinned conversations carry a pin marker and rise
  to the top of **their own section**.
- **The list is split into Channels and Direct Messages**, each with a collapsible header and each
  sorted independently — so a pinned direct message rises within its own section, not above the
  Channels one.

### Conversation Bubbles

On Android 11 and later, a message notification can be opened as a floating **bubble** that
stays on top of whatever else you are doing. Tap the bubble icon on the notification to promote
a conversation; Android remembers the choice per conversation, and the system Bubbles settings
control whether they are offered at all.

### Sõnumi olek

Olekumärgis kuvatakse ainult **sinu enda** väljaminevate sõnumite all (teiste sissetulevate sõnumite puhul olekumärgist ei kuvata):

| Olek                              | Tähendus                                                                                                                                                                                                                                  |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Saadan…                           | Queued or already handed to the radio, not yet resolved either way. Both stages share this text, but the icon and colour change as it progresses — a yellow upload cloud while queued, a blue arrow once the radio has it |
| Saajale kätte toimetatud          | Kõige tugevam kinnitus otsesõnumile – vastus tuli                                                                                                                                                                                         |
| Kärgvõrku kohale jõudnud          | Kanali leviedastuse puhul jõuab sõnum kärgvõrku (leviedastustel puudub saajapõhine kinnitus)                                                                                                                           |
| Vahendatud, saaja pole kinnitanud | Otsesõnumi puhul kuvatakse hoiatusvärviga – sõnum edastati, kuid kinnitust pole veel tulnud                                                                                                                                               |
| Marsruutimine SF++ ahela kaudu…   | Being routed/buffered by the Store & Forward Plus Plus chain                                                                                                                                                          |
| Kinnitatud SF++ ahel              | Kinnitatud kohaletoimetamine SF++ keti kaudu                                                                                                                                                                                              |
| Tõrge                             | Kohaletoimetamine ebaõnnestus – puuduta konkreetse põhjuse olekut (vt allpool jaotist „Kohaletoimetamise vead”)                                                                                                        |

### Delivery Errors

Kui sõnumit ei õnnestu kohale toimetada, näitab veaindikaator, mis valesti läks:

| Tõrge                                    | Tähendab                                                                                                                                                                      | Mida teha                                                                                                                             |
| ---------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| No Route                                 | Sihtkoha sõlmeni pole teed olemas                                                                                                                                             | Saaja võib olla võrguühenduseta või võrguühenduse levialast väljas. Try later or move closer.         |
| No radio interface                       | No radio interface available to send                                                                                                                                          | Check that your radio is connected and available.                                                                     |
| Failed to deliver to mesh                | Retries exhausted. The same label covers three underlying causes — a relay refusing (NAK), a plain timeout, and running out of retransmits | Move closer, improve signal, or wait for conditions to improve. Tap the error for the specific cause. |
| Piiratud määr                            | The mesh is throttling you for sending too fast                                                                                                                               | Wait before sending again.                                                                                            |
| Pole autoriseeritud                      | The destination refused the request                                                                                                                                           | Check you have the right channel and keys for that node.                                                              |
| Saaja vajab sinu võtit                   | Direct-message encryption could not complete because the other node does not have your public key yet                                                                         | Exchange node info — the key travels with it. Common on a first DM to a new contact.                  |
| Saaja võti pole saadaval                 | You do not have the recipient's public key                                                                                                                                    | Wait for their node info to arrive, or ask them to broadcast it.                                                      |
| Krüptitud sõnumi saatmine ebaõnnestus    | Encryption failed for this direct message                                                                                                                                     | Verify both nodes have exchanged keys and are on compatible firmware.                                                 |
| Admin sessioon aegunud                   | A remote-admin session timed out                                                                                                                                              | Reopen the remote node's settings to start a new session.                                                             |
| Administraatori võti pole autoriseeritud | The target node does not accept your admin key                                                                                                                                | Veendu, et administraatori võti sobiks mõlemas sõlmele.                                                               |
| Channel/key mismatch                     | Destination channel/key does not match                                                                                                                                        | Verify both nodes share the same channel and PSK.                                                                     |
| Sõnum on saatmiseks liiga pikk           | Sõnum ületab maksimaalset sõnumi mahtu                                                                                                                                        | Lühenda sõnumit ja proovi uuesti.                                                                                     |
| No app response                          | App or plugin did not respond to the request                                                                                                                                  | Proovi uuesti või kontrolli sihtrakenduse või -mooduli olekut.                                                        |
| Töötsükli piirang                        | Regional airtime limit reached                                                                                                                                                | Oota, kuni töötsükli aken lähtestub.                                                                                  |
| Invalid request                          | Malformed or invalid request                                                                                                                                                  | Retry after updating or restarting the app if this persists.                                                          |

> 💡 **Vihje:** Enamik kohaletoimetamise vigu laheneb iseenesest. If a node is intermittently reachable, the mesh will retry. Püsivate „Marsruuti pole” vigade korral kontrolli, kas ruuteri vahesõlmed on võrgus.

## Sõnumi omadused

### Kiirvestlus

Eelsalvestatud sõnumid kiireks suhtluseks:

- Juurdepääs läbi sõnumi sisestamise alas oleva kiirvestluse nupu
- Valige sisseehitatud fraaside või kohandatud sõnumite hulgast
- Kohanda kiirvestluse sõnumeid menüüs **Seaded → Kiirvestlus**
- Kasulik, kui trükkimine on ebapraktiline (kindad, väike ekraan, kiireloomuline)

![Kiirvestluse võimalus](../../assets/screenshots/messages_quick_chat.png)

Igal kiirvestluse kirjel on lühike **Nimi** (nupu silt), **Sõnum**, mille see lisab, ja **Saada kohe** lüliti – kui see on lubatud, saadetakse nupu puudutamisel sõnum kohe, selle asemel et see sisestada sisestusväljale redigeerimiseks:

[Uus kiirvestluse dialoog nime, sõnumi ja kohese saatmise lülitiga](../../assets/screenshots/messages_edit_quick_chat.png)

Kanalite loendis kuvatakse iga kanal koos selle viimase sõnumi eelvaatega.

### Otsin sõnumeid

Saad otsida mis tahes vestluse kogu ajaloost otse vestlusekraanilt:

1. Ava vestlus (kanal või otsesõnum).
2. Puuduta ülemisel ribal **otsinguikooni**.
3. Tipi väljale **Otsi sõnumeid…**. Otsing toimub tippimise ajal kõigis selle vestluse salvestatud sõnumites.
4. Use the **N / M** result counter and the **previous / next arrows** to jump between matches, which are highlighted in the conversation.

![Sõnumite otsinguriba tulemuste loenduri ja eelmise/järgmise noolega](../../assets/screenshots/messages_search_bar.png)

> 💡 **Vihje:** Otsing toimub täisteksti põhjal ja jääb vestlusse, kust sa selle avasid – see ei otsi teistest kanalitest ega kontaktide hulgast. See võrdleb seadmesse juba salvestatud sõnumeid, seega töötab see täielikult võrguühenduseta.

### Sõnumimullid

Sõnumid kuvatakse vestlusmullidena – saadetud sõnumid paremal, vastuvõetud sõnumid vasakul. Iga mull näitab saatjat, ajatempli ja kohaletoimetamise olekut. Messages with replies include a quoted preview of the original message above the response.

### Teksti vormindamine

Messages support lightweight inline **Markdown**. Received messages render the styling with the syntax characters removed:

| Tüüp             | Syntax                        | Renders as           |
| ---------------- | ----------------------------- | -------------------- |
| Paks             | `**paks**`                    | **paks**             |
| Italic           | `*italic*`                    | _italic_             |
| Läbikriipsutatud | `~~läbikriips~~`              | ~~läbikriips~~       |
| Inline code      | `` `kood` ``                  | monospace `code`     |
| Ühendus          | `[silt](https://example.com)` | a tappable **label** |

When composing, focus the message field and type at least three characters to reveal a **formatting toolbar** below the input. Vali tekst ja puuduta stiili, et see murda (puuduta uuesti, et see eemaldada); kui valikut pole, lisab stiil tühja paari, kusjuures kursor on markerite vahel. Linginupp avab URL-i sisestamiseks dialoogiboksi. As you type, the draft styles live in the field while the underlying text keeps its Markdown characters.

> 💡 **Vihje:** Vormindus kantakse kärgvõrgu literaalmärkidena – samad baidid, mida iOS saadab. Kliendid, mis ei toeta Markdowni (vanemad rakendused, tavalised püsivara kliendid), kuvavad toored `**`/`~~` märgid. URL-id, e-posti aadressid ja telefoninumbrid lingitakse endiselt automaatselt olenemata sellest, kas kasutate Markdowni või mitte.

### Mainimine

Type `@` while composing to mention a node — a picker suggests matching contacts as you type. Saadud sõnumis kuvatakse mainimine esiletõstetud kiibina, mis näitab sõlme nime; puuduta seda, et hüpata otse selle sõlme üksikasjade lehele.

### Reaktsioonid

React to messages with emoji:

- **Double-tap** a message — or long-press it — to raise a quick reaction bar above the bubble
- Tap an emoji in the bar to send it; tap **more** to open the full picker, or anywhere outside
  the bar to dismiss it without sending
- Reaktsioonid kuvatakse sõnumimulli all
- Mitu kasutajat saavad samale sõnumile reageerida
- Reageeri oma või teiste sõnumitele

> ℹ️ **Note:** Opening the bar sends nothing. A reaction is a real mesh packet, so it only goes
> out when you pick an emoji.

![Emotikonide reaktsioonimärgid kuvatakse sõnumi all](../../assets/screenshots/messages_reaction.png)

> 💡 **Vihje:** Reaktsioonid on kerged – need kasutavad täistekstisõnumitega võrreldes minimaalselt võrgu ribalaiust.

### Replying

**Swipe a message to the right** to reply to it — the composer opens with that message quoted.
Swiping past the reply threshold arms the action; releasing before it springs back with nothing sent.
Reply is also in the actions menu, reached by long-pressing and then tapping **More**.

### Day Separators

Messages are grouped by day. The separator above the first message of each day reads **Today**
or **Yesterday** for the two most recent days, and the date itself for older ones.

### Jump to Latest

Scrolling back through a conversation raises a jump-to-latest control. When messages arrive
while you are scrolled up, it names the most recent sender and adds a count of the other unread
messages. That count is messages, not people — five unread from one person reads as their name
**+4**.

### Message Actions

Long-press or double-tap a message to open the quick reaction bar, then tap **More** (the
overflow icon on that bar) to reach:

- **Kopeeri** – kopeeri sõnumi tekst lõikelauale
- **Vasta** – tsiteeri oma vastuses sõnumit
- **React** — add an emoji reaction
- **Tõlgi** – tõlgi vastuvõetud sõnum oma seadme keelde ja vaheta algse ja tõlgitud teksti vahel (ainult Google Play versioon; kasutab seadmesisest tõlget)
- **Kustuta** — saadetud sõnumi eemaldamine (lokaalne kustutamine)

### Sõnumi prioriteet

The app sends every message you compose at the same, default priority — there is no
emergency or alert tier to choose, and nothing in the app raises a direct message above a
channel broadcast. Any prioritising between them happens in firmware, not here. (The app
does mark some of its own internal traffic, such as admin and traceroute packets, as
reliable or background, but that is not something you control from the message composer.)

### Message Limits

- **Maximum length:** 200 bytes (approximately 200 characters for ASCII text)
- The 200-byte cap applies to the in-app composer — the mesh payload limit itself is ~233 bytes, so messages from other senders (e.g., App Functions) may arrive slightly longer
- **Kiiruse piiramine:** Võrk tagab eetriaja õigluse; suurte sõnumite mahtu võidakse piirata
- **Kohaletoimetamine:** Kui kinnitust ei ole saabunud, proovitakse sõnumeid automaatselt uuesti saata

## Parimad tavad

- Kasuta kanaleid grupi koordineerimiseks
- Kasutage privaatseks inimestevaheliseks suhtluseks otsesõnumeid
- Hoidke sõnumid lühikesed – võrgu ribalaius on piiratud
- Configure encryption for sensitive communications

## Seotud teemad

- [Sõlmed] (nodes) — otsesõnumi alustamiseks puuduta sõlme
- [Seaded — Raadio ja kasutaja](settings-radio-user) — kanalite krüpteerimise ja eelseadete konfimine
- [MQTT](mqtt) — silda kanali sõnumid internetti
- [Kanali konf](https://meshtastic.org/docs/configuration/radio/channels) — üksikasjalikud kanali seaded leiate aadressilt meshtastic.org

---

