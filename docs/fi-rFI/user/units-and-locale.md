---
title: Yksiköt, mittaus ja kieli- ja alueasetukset
parent: Käyttöopas
nav_order: 16
last_updated: 2026-08-29
description: Miten sovellus muotoilee lämpötilan, etäisyyden, nopeuden ja muut mittayksiköt laitteesi alueasetusten perusteella.
aliases:
  - mittaaminen
  - yksiköt
  - paikallinen
  - metrinen
  - imperiaalinen
---

# Yksiköt, mittaus ja kieli- ja alueasetukset

Meshtastic-sovellus näyttää lämpötilat, etäisyydet, nopeudet ja ajat automaattisesti niissä yksiköissä, jotka laitteesi on määritetty käyttämään. Jos laitteesi asetukset eivät tarjoa haluamiasi yksiköitä, sovelluksen **Yksiköt**-asetus ohittaa ne.

## Kuinka se toimii

Meshtastic-radiot lähettävät tiedot aina **metrisissä yksiköissä** (metri, °C, m/s, hPa jne.). Kun sovellus vastaanottaa nämä tiedot, se muuntaa ja näyttää arvot laitteesi alueasetusten mukaisessa yksikköjärjestelmässä.

Androidissa mittausasetukset määräytyvät järjestelmän **Kieli ja alue** -asetusten mukaan. Työpöytäversiossa (JVM) sovellus käyttää JVM:n oletus-`Locale`-asetusta.

Yksiköt seuraavat laitteesi **aluetta**, ei näytön kieltä. Pelkkä kieli, kuten **English**, sovelluksen omasta Kieli-asetuksesta tai Androidin sovelluskohtaisesta kieliasetuksesta säilyttää laitteesi alueasetuksen. Kieli, jossa on oma alue, kuten **English (Canada)**, ohittaa sen ja ottaa käyttöön kyseisen alueen yksiköt. Android 16:ssa järjestelmän **Mittausjärjestelmä**-asetus ohittaa alueasetuksen kokonaan.

> 💡 **Vinkki:** Oletusarvoisesti mitään ei tarvitse määrittää – muuta järjestelmän mittausjärjestelmäasetusta, niin kaikki Meshtasticin näkymät päivittyvät automaattisesti. Jos laitteesi ei tarjoa toimivaa alue- tai mittausjärjestelmäasetusta (joissakin valmistajien Android-versioissa näin on), määritä se sovelluksessa kohdassa **Asetukset → Yksiköt**.

## Radion oma näyttö käyttää omia asetuksiaan

**Laite → Näyttö → Yksiköt** määrittää radion näytössä käytettävät yksiköt, ei sovelluksessa. Siksi **Käytä 12 tunnin kelloa** ja **Osoita aina pohjoiseen** vaikuttavat kaikki vain radion näyttöön. Kyseisen näytön lämpötilalla on oma asetuksensa, [**Telemetria → Näytä Fahrenheit-asteet**](https://meshtastic.org/docs/configuration/module/telemetry#display-fahrenheit).

Jos radioluettelossa näkyvät mailit, mutta radion näytössä kilometrit, syy on siinä, että ne määritetään eri paikoissa. Radion asetusten muuttaminen ei koskaan vaikuta siihen, mitä sovellus näyttää. Katso laitteen asetuksia koskevat ohjeet Meshtasticin [Näytön asetukset] (https://meshtastic.org/docs/configuration/radio/display) -oppaasta.

## Lämpötila

Lämpötila-arvot ympäristösensoreista lähetetään muodossa **°C** ja näytetään laitteen lämpötila-asetusten mukaisesti.

![Ympäristömittarit lämpötilan kanssa](../../assets/screenshots/nodes_environment_metrics.png)

| Asetuksesi | Näet  |
| ---------- | ----- |
| Celsius    | 22 °C |
| Fahrenheit | 72 °F |

Tämä vaikuttaa kaikkiin lämpötilanäyttöihin sovelluksessa: radion ympäristötelemetria, maaperän lämpötila, kastepiste ja telemetriakäyrien akselit.

Lämpötila noudattaa alueesi **lämpötila-asetusta** riippumatta etäisyysjärjestelmästä. Alueet, joissa käytetään sekä metri- että brittiläisiä yksiköitä, toimivat oikein – esimerkiksi Isossa-Britanniassa etäisyydet näytetään maileina, mutta lämpötila **°C**-asteina. Android 14:ssä **Lämpötila** -alueasetus (Asetukset → Järjestelmä → Kielet → Alueasetukset) ohittaa alueen oletusasetuksen.

## Etäisyys ja korkeus

Radioiden väliset etäisyydet ja GPS-korkeudet lähetetään **metreinä** ja muunnetaan sekä skaalataan automaattisesti.

![Etäisyystietonäkymä](../../assets/screenshots/nodes_distance_info.png)

| Asetuksesi                            | Pieni etäisyys | Suuri etäisyys         | Korkeus  |
| ------------------------------------- | -------------- | ---------------------- | -------- |
| Metrijärjestelmä                      | 350 m          | 2,5 km                 | 1 200 m  |
| Imperiaalinen (US) | 1,148 ft       | 1.6 mi | 3,937 ft |

Sovellus käyttää luonnollista skaalausta — lyhyet etäisyydet pysyvät metreissä tai jaloissa, kun taas pidemmät etäisyydet vaihtuvat automaattisesti kilometreihin tai maileihin.

### Missä nämä näkyvät

- **Radiolista** — etäisyys ja suunta jokaiseen radioon
- **Radion tiedot** — korkeus ja etäisyys sijainnistasi
- **Kartta** — reittipisteiden etäisyydet ja reitinselvityksen hyppy-etäisyydet
- **Kompassi** — etäisyys valittuun radioon

## Nopeus

GPS-maanopeus näytetään laitteesi kieli- ja alueasetusten mukaisessa nopeusyksikössä.

| Asetuksesi                            | Näet    |
| ------------------------------------- | ------- |
| Metrijärjestelmä                      | 12 km/h |
| Imperiaalinen (US) | 7 mph   |

## Tuuli

Anturi lähettää tuulen nopeuden, puuskat ja tyynten jaksojen nopeuden yksikössä **m/s**, ja ne muunnetaan näyttöä varten — sovellus näyttää alueellasi käytettävän sääyksikön, ei anturin raakaa yksikköä.

| Asetuksesi                            | Näet                     |
| ------------------------------------- | ------------------------ |
| Metrijärjestelmä                      | 18,0 km/h                |
| Imperiaalinen (US) | 11.2 mph |

Kaikki kolme näytetään samassa yksikössä kaikkialla, missä ne esiintyvät: Radion tiedot -näkymän ympäristöosiossa, ympäristötelemetrian lokissa ja kaavioissa.

## Paino

Yhdistetyn vaa'an mittaustulokset lähetetään yksikössä **kg** ja muunnetaan näyttöä varten.

| Asetuksesi                            | Näet                    |
| ------------------------------------- | ----------------------- |
| Metrijärjestelmä                      | 1,50 kg                 |
| Imperiaalinen (US) | 3.31 lb |

## Sademäärä

Sademittaukset (1 tunnin ja 24 tunnin yhteismäärät) lähetetään muodossa **mm** ja muunnetaan näyttöä varten.

| Asetuksesi                            | Näet                    |
| ------------------------------------- | ----------------------- |
| Metrijärjestelmä                      | 12,0 mm                 |
| Imperiaalinen (US) | 0.47 in |

## Yksiköt, jotka eivät muutu

Jotkin yksiköt ovat kansainvälisiä standardeja ja näkyvät samalla tavalla kieli- ja alueasetuksista riippumatta:

| Mittaus                           | Yksikkö                        | Miksi                                     |
| --------------------------------- | ------------------------------ | ----------------------------------------- |
| Ilmanpaine                        | hPa                            | Kansainvälinen meteorologinen standardi   |
| Suunta / suuntima                 | ° (astetta) | Universaali navigointikäytäntö            |
| Säteily                           | µR/h                           | Standardi dosimetria-yksikkö              |
| GPS-koordinaatit                  | desimaaliasteet                | Kansainvälinen maantieteellinen standardi |
| Kosteus, akku ja maaperän kosteus | %                              | Yleinen                                   |

## Päivämäärä ja aika

Kaikki aikaleimat koko sovelluksessa — viimeksi kuultu, viestien ajat, telemetrialokit, kaavioakselit — noudattavat laitteesi päivämäärä- ja aika-asetuksia.

| Asetus              | Mitä se ohjaa         | Esimerkki                                        |
| ------------------- | --------------------- | ------------------------------------------------ |
| **24 tunnin aika**  | Aikamuoto             | 14:30 vs 2:30 PM |
| **Päivämäärämuoto** | Päivämäärän järjestys | 09/05/2026 vs 05/09/2026                         |

Sovellus käyttää myös **suhteellista aikaa** silloin kun se on järkevää — esimerkiksi “5 min sitten” tai “2 tuntia sitten” radiolistassa — ja tämä mukautuu automaattisesti laitteesi kieleen.

## Mittausjärjestelmän muuttaminen

Oletusarvoisesti sovellus käyttää laitteesi asetuksia, ja mittausjärjestelmä (metrinen tai imperiaalinen) määräytyy alueasetuksen mukaan:

1. Avaa **Asetukset → Järjestelmä → Kieli ja alue**
2. Vaihda **alueasetusta**
3. Palaa Meshtasticiin — arvot päivittyvät välittömästi

Android 16:ssa järjestelmänlaajuinen **Mittayksikköjärjestelmä**-asetus ohittaa alueasetuksen kaikissa mittauksissa. Android 14:ssä lämpötilan voi ohittaa kohdassa **Alueasetukset → Lämpötila**.

Kaikki englanninkieliset alueet eivät käytä täysin metristä järjestelmää. **English (United Kingdom)** käyttää etäisyyksissä maileja ja korkeuksissa jalkoja, joten radioluettelossa näkyvät mailit ja korkeus jaloissa. Jos haluat käyttää metrijärjestelmää, valitse sovelluksen **Yksiköt**-asetukseksi **Metrinen** (katso [Yksiköiden ohittaminen sovelluksessa](#overriding-the-units-in-the-app)) tai valitse täysin metrijärjestelmää käyttävä alue, kuten English (Canada), English (Ireland) tai English (New Zealand).

Joissakin puhelimissa **Alueasetukset** -valikkoa ei ole lainkaan, vaan tarjolla on vain English (United States). Näillä laitteilla käytä sovelluksen **Yksiköt**-asetusta (katso [Yksiköiden ohittaminen sovelluksessa](#overriding-the-units-in-the-app)).

### Sovelluksen yksiköiden ohittaminen

Kaikki laitteet eivät tue kaikkia asetuksia – joissakin valmistajien Android-versioissa ei ole lainkaan alueasetuksia, joissakin on tarjolla vain yksi englanninkielinen alue, ja Ison-Britannian alueasetukset käyttävät etäisyyksissä brittiläisiä yksiköitä, vaikka haluaisit lukea korkeuden metreinä. Tällöin voit käyttää sovelluksen omaa asetusta:

1. Avaa **Meshtasticin asetukset → Yksiköt**
2. Valitse **Järjestelmän oletus**, **Metrinen** tai **Imperiaalinen**
3. Kaikki näkymät päivittyvät heti – uudelleenkäynnistystä ei tarvita

**Järjestelmän oletus** käyttää puhelimesi tai tietokoneesi alue- ja mittayksikköasetuksia. **Metrinen**- tai **Imperiallinen**-asetuksen pakottaminen koskee kaikkia yksiköitä, myös lämpötilaa (metrinen → °C, imperiaalinen → °F), vaikka järjestelmän omat alueasetukset määrittäisivät toisin. Asetus on käytettävissä sekä Android- että työpöytäversiossa.

## Aiheeseen liittyvät aiheet

- [Radion mittarit](node-metrics) — missä lämpötila-, etäisyys- ja anturiarvot näytetään
- [Telemetria ja anturit](telemetry-and-sensors) — anturit, jotka tuottavat nämä mittaukset
- [Mittaus ja muotoilu](../developer/measurement) — kehittäjien viite muotoiluapuohjelmista
- [Asetukset — Radio ja käyttäjä](settings-radio-user) — alueasetus, joka määrittää käytettävät mittayksiköt
- [Näytön asetukset](https://meshtastic.org/docs/configuration/radio/display) – radion oman näytön yksikkö-, kello- ja kompassiasetukset meshtastic.orgissa
