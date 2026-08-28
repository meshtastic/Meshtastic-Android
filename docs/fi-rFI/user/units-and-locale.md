---
title: Yksiköt, mittaus ja kieli- ja alueasetukset
parent: Käyttöopas
nav_order: 16
last_updated: 2026-08-27
description: Miten sovellus muotoilee lämpötilan, etäisyyden, nopeuden ja muut mittayksiköt laitteesi alueasetusten perusteella.
aliases:
  - measurement
  - units
  - locale
  - metric
  - imperial
---

# Yksiköt, mittaus ja kieli- ja alueasetukset

Meshtastic-sovellus näyttää lämpötilat, etäisyydet, nopeudet ja ajat automaattisesti niissä yksiköissä, jotka laitteesi on määritetty käyttämään. Jos laitteesi asetukset eivät tarjoa haluamiasi yksiköitä, sovelluksen **Yksiköt**-asetus ohittaa ne.

---

## Miten se toimii

Meshtastic-radiot lähettävät tiedot aina **metrisissä yksiköissä** (metri, °C, m/s, hPa jne.). Kun sovellus vastaanottaa nämä tiedot, se muuntaa ja näyttää arvot laitteesi alueasetusten mukaisessa yksikköjärjestelmässä.

Androidissa mittausasetukset määräytyvät järjestelmän **Kieli ja alue** -asetusten mukaan. Työpöytäversiossa (JVM) sovellus käyttää JVM:n oletus-`Locale`-asetusta.

Yksiköt seuraavat laitteesi **aluetta**, ei näytön kieltä. Pelkän kielen valitseminen – esimerkiksi **English** sovelluksen omasta kieliasetuksesta tai Androidin sovelluskohtaisesta kielestä – säilyttää laitteen alueasetuksen. Vasta aluekohtaisen vaihtoehdon, kuten **English (Canada)**, valitseminen tuo mukanaan kyseisen alueen yksiköt. Android 16:ssa järjestelmän **Mittausjärjestelmä**-asetus ohittaa alueasetuksen kokonaan.

> 💡 **Vinkki:** Oletusarvoisesti mitään ei tarvitse määrittää – muuta järjestelmän mittausjärjestelmäasetusta, niin kaikki Meshtasticin näkymät päivittyvät automaattisesti. Jos laitteesi ei tarjoa toimivaa alue- tai mittausjärjestelmäasetusta (joissakin valmistajien Android-versioissa näin on), määritä se sovelluksessa kohdassa **Asetukset → Yksiköt**.

---

## Radion oma näyttö käyttää omia asetuksiaan

**Laite → Näyttö → Yksiköt** määrittää radion näytössä käytettävät yksiköt, ei sovelluksessa. Myös **Käytä 12 tunnin kelloa** ja **Osoita aina pohjoiseen** vaikuttavat vain radion näyttöön. Kyseisen näytön lämpötilalla on oma asetuksensa, [**Telemetria → Näytä Fahrenheit-asteet**](https://meshtastic.org/docs/configuration/module/telemetry#display-fahrenheit).

Jos radioluettelossa näkyvät mailit, mutta radion näytössä kilometrit, syy on siinä, että ne määritetään eri paikoissa. Laitteen asetusten muuttaminen ei koskaan vaikuta sovelluksen näyttämiin tietoihin. Katso laitteen asetuksia koskevat ohjeet Meshtasticin [Näytön asetukset] (https://meshtastic.org/docs/configuration/radio/display) -oppaasta.

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

Wind speed, gust and lull are transmitted by the sensor as **m/s** and converted for display — the app shows the unit weather forecasts use in your region, not the raw sensor unit.

| Asetuksesi                            | Näet                      |
| ------------------------------------- | ------------------------- |
| Metrijärjestelmä                      | 18.0 km/h |
| Imperiaalinen (US) | 11.2 mph  |

All three read in the same unit wherever they appear: the Node Detail environment section, the Environment Telemetry log, and the charts.

## Weight

Readings from a connected scale are transmitted in **kg** and converted for display.

| Asetuksesi                            | Näet                    |
| ------------------------------------- | ----------------------- |
| Metrijärjestelmä                      | 1.50 kg |
| Imperiaalinen (US) | 3.31 lb |

## Sademäärä

Sademittaukset (1 tunnin ja 24 tunnin yhteismäärät) lähetetään muodossa **mm** ja muunnetaan näyttöä varten.

| Your Setting                     | You See                 |
| -------------------------------- | ----------------------- |
| Metric                           | 12.0 mm |
| Imperial (US) | 0.47 in |

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
3. Android 16:ssa **Mittausjärjestelmä** ohittaa alueasetuksen kaikissa mittauksissa
4. Android 14:ssä lämpötila-asetus voidaan määrittää erikseen kohdassa **Alueasetukset → Lämpötila**
5. Palaa Meshtasticiin — arvot päivittyvät välittömästi

Kaikki englanninkieliset alueet eivät käytä täysin metristä järjestelmää. **English (United Kingdom)** käyttää etäisyyksissä maileja ja korkeuksissa jalkoja, joten radioluettelossa näkyvät mailit ja korkeus jaloissa. Jos haluat käyttää metrisiä etäisyyksiä, valitse sovelluksen **Yksiköt** -asetukseksi _Metrinen_ (alla) tai valitse täysin metrinen alue, kuten English (Canada), English (Ireland) tai English (New Zealand).

Joissakin puhelimissa **Alueasetukset** -valikkoa ei ole lainkaan, vaan tarjolla on vain English (United States). Käytä tällöin alla olevaa sovelluksen **Yksiköt** -asetusta.

### Sovelluksen yksiköiden ohittaminen

Kaikki laitteet eivät tue kaikkia asetuksia – joissakin valmistajien Android-versioissa ei ole lainkaan alueasetuksia, joissakin on tarjolla vain yksi englanninkielinen alue, ja Ison-Britannian alueasetukset käyttävät etäisyyksissä brittiläisiä yksiköitä, vaikka haluaisit lukea korkeuden metreinä. Tällöin voit käyttää sovelluksen omaa asetusta:

1. Avaa **Meshtasticin asetukset → Yksiköt**
2. Valitse **Järjestelmän oletus**, **Metrinen** tai **Imperiaalinen**
3. Kaikki näkymät päivittyvät heti – uudelleenkäynnistystä ei tarvita

**Järjestelmän oletus** käyttää laitteesi asetuksia edellä kuvatulla tavalla. Pakottamalla asetukseksi **Metrinen** tai **Imperiaalinen** kaikki mittaukset käyttävät kyseistä järjestelmää, myös lämpötila (metrinen → °C, imperiallinen → °F), vaikka laitteen omat alueasetukset määrittäisivät toisin. Asetus on käytettävissä sekä Android- että työpöytäversiossa.

> 💡 **Vinkki:** Kaikki mittausten muotoilut tehdään keskitetysti ja ne noudattavat käyttöympäristösi alueasetuksia, joten yksiköt pysyvät yhtenäisinä kaikkialla sovelluksessa.

## Aiheeseen liittyvät aiheet

- [Radion mittarit](node-metrics) — missä lämpötila-, etäisyys- ja anturiarvot näytetään
- [Telemetria ja anturit](telemetry-and-sensors) — anturit, jotka tuottavat nämä mittaukset
- [Mittaus ja muotoilu](../developer/measurement) — kehittäjien viite muotoiluapuohjelmista
- [Asetukset — Radio ja käyttäjä](settings-radio-user) — alueasetus, joka määrittää käytettävät mittayksiköt
- [Näytön asetukset](https://meshtastic.org/docs/configuration/radio/display) – radion oman näytön yksikkö-, kello- ja kompassiasetukset meshtastic.orgissa

---

