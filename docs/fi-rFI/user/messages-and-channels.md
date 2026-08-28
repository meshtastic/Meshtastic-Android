---
title: Viestit ja kanavat
parent: Käyttöopas
nav_order: 3
last_updated: 2026-08-27
description: Lähetä ja vastaanota viestejä, hallitse kanavia, määritä salaus, hae keskusteluja sekä käytä pikachatia, reaktioita ja viestitoimintoja.
aliases:
  - kanavat
  - yksityisviestit
  - viestit
  - keskustelut
---

# Viestit ja kanavat

Meshtastic tukee kahta viestintätilaa: **kanavaviestit** ja **suoraviestit**.

## Kanavat

Kanavat ovat jaettuja viestintäryhmiä. Kaikki radiot, jotka on määritetty samalla kanava-avaimella, voivat lukea ja lähettää viestejä kyseisellä kanavalla.

### Oletuskanava

Jokaisessa Meshtastic-laitteessa on oletuksena **LongFast**-kanava. Tämä on salaamaton kanava yleiseen mesh-viestintään.

### Kanavan turvallisuus

Kanavat tukevat useita salaustasoja:

| Ikoni | Suojaustaso                               | Kuvaus                                                                                                                                                            |
| ----- | ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 🔒    | PSK (256-bittinen AES) | Täysin salattu vahvalla esijaetulla avaimella. Vain samaa avainta käyttävät radiot voivat lukea viestejä.                         |
| 🔐    | PSK (128-bittinen AES) | Salattu lyhyemmällä avaimella. Useimmille käyttötapauksille turvallinen, mutta 256-bittinen on suositeltu arkaluontoiseen dataan. |
| 🔓    | Oletus / avoin                            | Käyttää tunnettua oletusavainta. **Kaikki Meshtastic-radiot** samalla esiasetuksella voivat lukea nämä viestit.                   |
| ⚠️    | Turvaton + sijainti                       | Avoin kanava, joka lähettää myös GPS-sijaintisi. Käytä varoen julkisissa verkoissa.                                               |

> 🔒 **Security:** Always configure a unique PSK for private communications. Oletuskanava on tarkoituksella avoin, jotta uudet käyttäjät löytävät mesh-verkon — luo erillinen salattu kanava arkaluontoiselle viestinnälle.

### Kanavan lisääminen

1. Siirry kohtaan **Asetukset → Kanavat**.
2. Tap the **+** button to add a channel, or import one by scanning a channel QR code.
3. Määritä kanavan nimi ja salausavain.
4. Jaa kanavan URL tai QR-koodi muille, jotka tarvitsevat pääsyn.

Napauttamalla kanavaa näet sen tiedot ja jakovaihtoehdot.

## Yksityisviestit

Yksityisviestit (DM) ovat kahden radion välistä päästä päähän salattua viestintää.

### Yksityisviestin lähettäminen

1. Avaa **Viestit**-välilehti.
2. Valitse radion yhteystiedoista tai napauta radiota radiolistasta.
3. Kirjoita viesti ja napauta **Lähetä**.

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

### Viestin tilat

Tilateksti näkyy vain **omissa** lähtevissä viesteissäsi (muiden lähettämissä saapuvissa viesteissä ei näytetä tilaa):

| Tila                                                           | Merkitys                                                                                                                                                                                                                                  |
| -------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Lähetetään…                                                    | Queued or already handed to the radio, not yet resolved either way. Both stages share this text, but the icon and colour change as it progresses — a yellow upload cloud while queued, a blue arrow once the radio has it |
| Toimitettu vastaanottajalle                                    | Vahvin mahdollinen vahvistus suoralle viestille — vastaanottokuittaus on saatu                                                                                                                                                            |
| Toimitettu mesh-verkkoon                                       | Kanavalähetyksessä viesti on saavuttanut mesh-verkon (kanavalähetyksille ei lähetetä vastaanottajakohtaista kuittausta)                                                                                                |
| Välitetty, mutta vastaanottaja ei ole vahvistanut vastaanottoa | Suorassa viestissä tämä näytetään varoitusvärillä — viesti on välitetty eteenpäin, mutta vastaanottokuittausta ei ole vielä saatu                                                                                                         |
| Reititetään SF++ ketjun kautta…                                | Reititetty tai puskuroitu varastoi & välitä Plus Plus -ketjussa                                                                                                                                                       |
| Vahvistettu SF++-ketjussa                                      | Toimitus vahvistettu varastoi & välitä++ -ketjun kautta                                                                                                                                                               |
| Virhe                                                          | Toimitus epäonnistui — napauta tilaa nähdäksesi tarkemman syyn (katso alla kohta toimitusvirheet)                                                                                                                      |

### Toimitusvirheet

Kun viestin toimitus epäonnistuu, virheilmaisin näyttää, mikä meni pieleen:

| Virhe                                     | Merkitys                                                                                                                                                                      | Toimenpiteet                                                                                                                                    |
| ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Ei reittiä                                | Kohderadioon ei ole reittiä                                                                                                                                                   | Vastaanottaja voi olla offline-tilassa tai verkon kantaman ulkopuolella. Yritä myöhemmin tai siirry lähemmäksi. |
| Ei radioliitäntää                         | Lähetykseen ei ole käytettävissä radioliitäntää                                                                                                                               | Varmista, että radiosi on yhdistetty ja käytettävissä.                                                                          |
| Toimitus mesh-verkkoon epäonnistui        | Retries exhausted. The same label covers three underlying causes — a relay refusing (NAK), a plain timeout, and running out of retransmits | Move closer, improve signal, or wait for conditions to improve. Tap the error for the specific cause.           |
| Lähetysrajoitus saavutettu                | The mesh is throttling you for sending too fast                                                                                                                               | Wait before sending again.                                                                                                      |
| Ei valtuuksia                             | The destination refused the request                                                                                                                                           | Check you have the right channel and keys for that node.                                                                        |
| Vastaanottaja tarvitsee avaimesi          | Direct-message encryption could not complete because the other node does not have your public key yet                                                                         | Exchange node info — the key travels with it. Common on a first DM to a new contact.                            |
| Vastaanottajan avain ei ole käytettävissä | You do not have the recipient's public key                                                                                                                                    | Wait for their node info to arrive, or ask them to broadcast it.                                                                |
| Salattua viestiä ei voitu lähettää        | Encryption failed for this direct message                                                                                                                                     | Verify both nodes have exchanged keys and are on compatible firmware.                                                           |
| Ylläpitoistunto on vanhentunut            | A remote-admin session timed out                                                                                                                                              | Reopen the remote node's settings to start a new session.                                                                       |
| Ylläpitoavainta ei ole valtuutettu        | The target node does not accept your admin key                                                                                                                                | Varmista, että ylläpitoavain on sama molemmissa radioissa.                                                                      |
| Kanavan tai avaimen ristiriita            | Kohteen kanava tai avain ei täsmää                                                                                                                                            | Varmista, että molemmat radiot käyttävät samaa kanavaa ja PSK:ta.                                               |
| Viesti on liian suuri lähetettäväksi      | Viesti ylittää sallitun enimmäiskoon                                                                                                                                          | Lyhennä viestiä ja yritä uudelleen.                                                                                             |
| Ei sovellusvastausta                      | Sovellus tai liitännäinen ei vastannut pyyntöön                                                                                                                               | Yritä uudelleen tai tarkista kohdesovelluksen tai moduulin tila.                                                                |
| Käyttöasteen rajoitus                     | Alueellinen lähetysajan raja saavutettu                                                                                                                                       | Odota, että käyttöasteikkuna nollautuu.                                                                                         |
| Virheellinen pyyntö                       | Virheellinen tai puutteellinen pyyntö                                                                                                                                         | Jos ongelma jatkuu, päivitä tai käynnistä sovellus uudelleen ja yritä sitten uudelleen.                                         |

> 💡 Vinkki: Useimmat toimitusvirheet korjaantuvat itsestään. Jos radio on ajoittain tavoitettavissa, mesh yrittää uudelleen. Jos “Ei reittiä” -virhe toistuu, tarkista että välissä olevat reitittävät radiot ovat verkossa.

## Viestiominaisuudet

### Pikachatti

Valmiiksi määritetyt viestit nopeaan viestintään:

- Käytettävissä viestikentän Pikachatti-painikkeen kautta
- Valitse valmiista sisäänrakennetuista viesteistä tai omista viesteistä
- Muokkaa pikachatti-viestejä kohdassa **Asetukset → Pikachatti**
- Hyödyllinen, kun kirjoittaminen on hankalaa (hanskat, pieni näyttö, kiire)

![Pikachatti-vaihtoehto](../../assets/screenshots/messages_quick_chat.png)

Jokaisella pikaviestillä on lyhyt **Nimi** (painikkeen teksti), lisättävä **Viesti** sekä **Lähetä heti** -kytkin. Kun se on käytössä, painikkeen napauttaminen lähettää viestin välittömästi sen sijaan, että se lisättäisiin muokattavaksi syöttökenttään:

![Uusi pikakeskusteluviestin valintaikkuna, jossa näkyvät nimi, viesti ja Lähetä heti -kytkin](../../assets/screenshots/messages_edit_quick_chat.png)

Kanavalista näyttää jokaisen kanavan ja sen viimeisimmän viestin esikatselun.

### Viestien haku

Voit hakea koko keskusteluhistorian suoraan chat-näkymästä:

1. Avaa keskustelu (kanava tai suoraviesti).
2. Napauta **hakukuvaketta** yläpalkissa.
3. Kirjoita **Etsi viestejä** -kenttään. Haku toimii kirjoittaessa ja käy läpi kaikki tallennetut viestit kyseisessä keskustelussa.
4. Käytä **N / M** -laskuria ja edellinen/seuraava -nuolia siirtyäksesi osumien välillä, jotka on korostettu keskustelussa.

![Viestihaku-palkki tuloslaskurilla ja nuolilla](../../assets/screenshots/messages_search_bar.png)

> 💡 Vinkki: Haku on täystekstihaku ja toimii vain siinä keskustelussa, josta avasit sen — se ei hae muista kanavista tai kontakteista. Se hakee osumat laitteellesi jo tallennetuista viesteistä, joten se toimii täysin offline-tilassa.

### Viestikuplat

Viestit näkyvät chat-kuplina — lähetetyt viestit oikealla, vastaanotetut vasemmalla. Jokainen kupla näyttää lähettäjän, aikaleiman ja toimitustilan. Vastaukselliset viestit sisältävät alkuperäisen viestin esikatselun vastauksen yläpuolella.

### Tekstin muotoilu

Viestit tukevat kevyttä rivinsisäistä **Markdown**-muotoilua. Vastaanotetut viestit näyttävät muotoilun ilman Markdown-syntaksimerkkejä:

| Kirjoita            | Syntaksi                      | Näkyy muodossa        |
| ------------------- | ----------------------------- | --------------------- |
| Lihavoitu           | **lihavoitu**                 | **lihavoitu**         |
| Kursivoitu          | `*kursivoitu*`                | _kursivoitu_          |
| Yliviivattu         | `~~yliviivattu~~`             | ~~yliviivattu~~       |
| Rivinsisäinen koodi | `` `koodi` ``                 | tasalevyinen `koodi`  |
| Linkki              | `[nimi](https://example.com)` | napautettava **nimi** |

Kun kirjoitat viestiä, napauta viestikenttää ja kirjoita vähintään kolme merkkiä, niin kentän alle avautuu **muotoilutyökalurivi**. Valitse teksti ja napauta muotoilua lisätäksesi sen ympärille merkinnät (napauta uudelleen poistaaksesi ne). Jos tekstiä ei ole valittuna, muotoilu lisää tyhjän merkkiparin ja sijoittaa kohdistimen niiden väliin. Linkkipainike avaa valintaikkunan URL-osoitteen syöttämistä varten. Kirjoittaessasi luonnoksen muotoilu näkyy kentässä, vaikka taustalla oleva teksti säilyttää Markdown-merkit.

> 💡 **Vinkki:** Muotoilu välitetään mesh-verkossa kirjaimellisina merkkeinä – samoina tavuina, jotka iOS lähettää. Sovellukset, jotka eivät tue Markdownia (vanhemmat sovellukset ja pelkkää laiteohjelmistoa käyttävät laitteet), näyttävät alkuperäiset `**`- ja `~~`-merkit. URL-osoitteet, sähköpostiosoitteet ja puhelinnumerot muutetaan edelleen automaattisesti linkeiksi riippumatta siitä, käytätkö Markdownia.

### Maininnat

Kirjoita viestiä laatiessasi `@` mainitaksesi radion — valitsin ehdottaa kirjoittaessasi vastaavia yhteystietoja. Vastaanotetussa viestissä maininta näkyy korostettuna tunnisteena, jossa näkyy radion nimi. Napauta sitä siirtyäksesi suoraan kyseisen radion tietosivulle.

### Reaktiot

Reagoi viesteihin emojeilla:

- **Double-tap** a message — or long-press it — to raise a quick reaction bar above the bubble
- Tap an emoji in the bar to send it; tap **more** to open the full picker, or anywhere outside
  the bar to dismiss it without sending
- Reaktiot näkyvät viestin alapuolella
- Useampi käyttäjä voi reagoida samaan viestiin
- Voit reagoida omiin ja muiden viesteihin

> ℹ️ **Note:** Opening the bar sends nothing. A reaction is a real mesh packet, so it only goes
> out when you pick an emoji.

![Emoji-reaktiot viestin alla](../../assets/screenshots/messages_reaction.png)

> 💡 Vinkki: Reaktiot kuluttavat vain vähän mesh-verkon kaistaa verrattuna täysiin tekstiviesteihin.

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

### Viestitoiminnot

Long-press or double-tap a message to open the quick reaction bar, then tap **More** (the
overflow icon on that bar) to reach:

- **Kopioi** — kopioi viestin teksti leikepöydälle
- **Vastaa** — lainaa viesti vastaukseesi
- **Reagoi** — lisää emoji-reaktio
- **Käännä** — kääntää vastaanotetun viestin laitteesi kielelle ja mahdollistaa vaihtamisen alkuperäisen ja käännetyn tekstin välillä (vain Google Play -versiossa; käyttää laitteella toimivaa käännöstä)
- **Poista** — poista lähettämäsi viesti (paikallinen poisto)

### Viestien prioriteetti

The app sends every message you compose at the same, default priority — there is no
emergency or alert tier to choose, and nothing in the app raises a direct message above a
channel broadcast. Any prioritising between them happens in firmware, not here. (The app
does mark some of its own internal traffic, such as admin and traceroute packets, as
reliable or background, but that is not something you control from the message composer.)

### Viestirajoitukset

- **Enimmäispituus:** 200 tavua (noin 200 merkkiä ASCII-tekstille)
- The 200-byte cap applies to the in-app composer — the mesh payload limit itself is ~233 bytes, so messages from other senders (e.g., App Functions) may arrive slightly longer
- **Rajoitusnopeus:** mesh-verkko tasaa lähetysajan oikeudenmukaisesti; suuri viestimäärä voi joutua rajoitetuksi
- **Toimitus:** viestit yritetään lähettää uudelleen automaattisesti, jos kuittausta ei saada

## Parhaat käytännöt

- Käytä kanavia ryhmäviestintään
- Käytä suoraviestejä kahden käyttäjän väliseen yksityiseen viestintään
- Pidä viestit lyhyinä — mesh-verkon kaistanleveys on rajallinen
- Määritä salaus arkaluontoiselle viestinnälle

## Aiheeseen liittyvät aiheet

- [Radiot](nodes) — napauta radiota aloittaaksesi suoraviestin
- [Asetukset — Radio ja käyttäjä](settings-radio-user) — määritä kanavan salaus ja esiasetukset
- [MQTT](mqtt) — välittää kanavaviestit internetiin
- [Kanavien määritys](https://meshtastic.org/docs/configuration/radio/channels) — tarkemmat kanava-asetukset meshtastic.org-sivustolla

---

