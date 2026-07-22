---
title: Viestit ja kanavat
parent: Käyttöopas
nav_order: 3
last_updated: 2026-07-11
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

> 🔒 **Tietoturvavinkki:** Käytä aina yksilöllistä PSK-avainta yksityiseen viestintään. Oletuskanava on tarkoituksella avoin, jotta uudet käyttäjät löytävät mesh-verkon — luo erillinen salattu kanava arkaluontoiselle viestinnälle.

### Kanavan lisääminen

1. Siirry kohtaan **Asetukset → Kanavat**.
2. Napauta **Lisää kanava** tai skannaa QR-koodi.
3. Määritä kanavan nimi ja salausavain.
4. Jaa kanavan URL tai QR-koodi muille, jotka tarvitsevat pääsyn.

Napauttamalla kanavaa näet sen tiedot ja jakovaihtoehdot.

## Yksityisviestit

Yksityisviestit (DM) ovat kahden radion välistä päästä päähän salattua viestintää.

### Yksityisviestin lähettäminen

1. Avaa **Viestit**-välilehti.
2. Valitse radion yhteystiedoista tai napauta radiota radiolistasta.
3. Kirjoita viesti ja napauta **Lähetä**.

### Viestin tilat

Tilateksti näkyy vain **omissa** lähtevissä viesteissäsi (muiden lähettämissä saapuvissa viesteissä ei näytetä tilaa):

| Tila                                                           | Merkitys                                                                                                                                                             |
| -------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Lähetetään…                                                    | Jonossa tai jo siirretty radiolle, mutta lopullista tilaa ei ole vielä vahvistettu (jonossa ja lähetetään näyttävät molemmat tämän saman tekstin) |
| Toimitettu vastaanottajalle                                    | Vahvin mahdollinen vahvistus suoralle viestille — vastaanottokuittaus on saatu                                                                                       |
| Toimitettu mesh-verkkoon                                       | Kanavalähetyksessä viesti on saavuttanut mesh-verkon (kanavalähetyksille ei lähetetä vastaanottajakohtaista kuittausta)                           |
| Välitetty, mutta vastaanottaja ei ole vahvistanut vastaanottoa | Suorassa viestissä tämä näytetään varoitusvärillä — viesti on välitetty eteenpäin, mutta vastaanottokuittausta ei ole vielä saatu                                    |
| Reititetään SF++ ketjun kautta…                                | Reititetty tai puskuroitu varastoi & välitä Plus Plus -ketjussa                                                                                  |
| Vahvistettu SF++-ketjussa                                      | Toimitus vahvistettu varastoi & välitä++ -ketjun kautta                                                                                          |
| Virhe                                                          | Toimitus epäonnistui — napauta tilaa nähdäksesi tarkemman syyn (katso alla kohta toimitusvirheet)                                                 |

### Toimitusvirheet

Kun viestin toimitus epäonnistuu, virheilmaisin näyttää, mikä meni pieleen:

| Virhe                                                  | Merkitys                                        | Toimenpiteet                                                                                                                                                                             |
| ------------------------------------------------------ | ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Ei reittiä                                             | Kohderadioon ei ole reittiä                     | Vastaanottaja voi olla offline-tilassa tai verkon kantaman ulkopuolella. Yritä myöhemmin tai siirry lähemmäksi.                                          |
| Hylkäyskuittaus vastaanotettu (NAK) | Seuraavan hypyn radio kieltäytyi välittämästä   | Välittäjä-radio voi olla ruuhkautunut. Odota ja yritä uudelleen.                                                                                         |
| Aikakatkaisu                                           | Ei kuittausta uudelleenyritysten aikana         | Vastaanottaja voi olla kantaman ulkopuolella. Yritä nostaa hyppyrajaa tai siirtyä parempaan paikkaan.                                                    |
| Ei Käyttöliittymää                                     | Lähetykseen ei ole käytettävissä radioliitäntää | Tarkista, että radio on yhdistetty ja kanava on määritetty.                                                                                                              |
| Kaikki uudelleenyritykset käytetty                     | Uudelleenyritysten enimmäismäärä saavutettu     | Mesh-reitti ei ole luotettava. Kokeile toista kanavaa tai odota olosuhteiden parantumista.                                                               |
| Ei Kanavaa                                             | Kohdekanavaa ei ole olemassa                    | Varmista, että molemmilla radioilla on sama kanavakonfiguraatio.                                                                                                         |
| Liian suuri                                            | Viesti ylittää sallitun enimmäiskoon            | Lyhennä viestisi (enintään noin 200 merkkiä).                                                                                                         |
| Ei vastausta                                           | Radio vastaanotti viestin mutta ei vastannut    | Vastaanottajan radio voi olla varattu tai virransäästötilassa.                                                                                                           |
| Käyttöasteen rajoitus                                  | Alueellinen lähetysajan raja saavutettu         | Radiosi on käyttänyt sallitun lähetysajan. Odota, että käyttöasteen rajoituksen ikkuna nollautuu (tyypillisesti 1 tunti EU-alueilla). |
| Virheellinen pyyntö                                    | Viallinen tai virheellinen viesti               | Tämä yleensä viittaa ohjelmistovirheeseen. Kokeile käynnistää sovellus uudelleen.                                                                        |

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

- **Pitkä painallus** viestissä avaa toimintovalikon
- Napauta **Lisää reaktio** valitaksesi emojin
- Reaktiot näkyvät viestin alapuolella
- Useampi käyttäjä voi reagoida samaan viestiin
- Voit reagoida omiin ja muiden viesteihin

![Emoji-reaktiot viestin alla](../../assets/screenshots/messages_reaction.png)

> 💡 Vinkki: Reaktiot kuluttavat vain vähän mesh-verkon kaistaa verrattuna täysiin tekstiviesteihin.

### Viestitoiminnot

Pitkä painallus missä tahansa viestissä avaa:

- **Kopioi** — kopioi viestin teksti leikepöydälle
- **Vastaa** — lainaa viesti vastaukseesi
- **Reagoi** — lisää emoji-reaktio
- **Käännä** — kääntää vastaanotetun viestin laitteesi kielelle ja mahdollistaa vaihtamisen alkuperäisen ja käännetyn tekstin välillä (vain Google Play -versiossa; käyttää laitteella toimivaa käännöstä)
- **Poista** — poista lähettämäsi viesti (paikallinen poisto)

### Viestien prioriteetti

Viestit jonotetaan ja lähetetään prioriteetin mukaan:

1. Hätä ja hälytysviestit (korkein)
2. Suoraviestit
3. Kanavaviestit (alin prioriteetti)

### Viestirajoitukset

- **Enimmäispituus:** 200 tavua (noin 200 merkkiä ASCII-tekstille)
- The 200-byte cap applies to the in-app composer — the mesh payload limit itself is ~233 bytes, so messages from other senders (e.g., App Functions or Android Auto) may arrive slightly longer
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

