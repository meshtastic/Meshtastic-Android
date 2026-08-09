---
title: Yksiköt, mittaus ja kieli- ja alueasetukset
parent: Käyttöopas
nav_order: 16
last_updated: 2026-07-08
description: Miten sovellus muotoilee lämpötilan, etäisyyden, nopeuden ja muut mittayksiköt laitteesi alueasetusten perusteella.
---

# Yksiköt, mittaus ja kieli- ja alueasetukset

Meshtastic-sovellus näyttää automaattisesti lämpötilat, etäisyydet, nopeudet ja ajat niissä yksiköissä, jotka laitteesi on asetettu käyttämään — asetuksia ei tarvitse muuttaa sovelluksessa.

---

## Miten se toimii

Meshtastic-radiot lähettävät tiedot aina **metrisissä yksiköissä** (metri, °C, m/s, hPa jne.). Kun sovellus vastaanottaa nämä tiedot, se muuntaa ja näyttää arvot laitteesi alueasetusten mukaisessa yksikköjärjestelmässä.

Androidissa mittausasetukset määräytyvät järjestelmän **Kieli ja alue** -asetusten mukaan. Työpöytäversiossa (JVM) sovellus käyttää JVM:n oletus-`Locale`-asetusta.

> 💡 **Vinkki:** Mittayksiköitä ei tarvitse koskaan vaihtaa sovelluksen sisältä. Muuta järjestelmäsi mittayksikköasetuksia, niin kaikki Meshtasticin näkymät päivittyvät automaattisesti — radion tiedot, telemetriakaaviot, sää, korkeus ja paljon muuta.

---

## Lämpötila

Lämpötila-arvot ympäristösensoreista lähetetään muodossa **°C** ja näytetään laitteen lämpötila-asetusten mukaisesti.

![Ympäristömittarit lämpötilan kanssa](../../assets/screenshots/nodes_environment_metrics.png)

| Asetuksesi | Näet  |
| ---------- | ----- |
| Celsius    | 22 °C |
| Fahrenheit | 72 °F |

Tämä vaikuttaa kaikkiin lämpötilanäyttöihin sovelluksessa: radion ympäristötelemetria, maaperän lämpötila, kastepiste ja telemetriakäyrien akselit.

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

Tuulen nopeus- ja puuskadata ympäristösensoreista lähetetään muodossa **m/s** ja muunnetaan näyttöä varten.

| Asetuksesi                            | Näet   |
| ------------------------------------- | ------ |
| Metrijärjestelmä                      | 5 m/s  |
| Imperiaalinen (US) | 11 mph |

Tuulimittaukset näkyvät **Radion tiedot** -näkymän ympäristöosiossa sekä **Ympäristötelemetria** -kaavioissa.

## Sademäärä

Sademittaukset (1 tunnin ja 24 tunnin yhteismäärät) lähetetään muodossa **mm** ja muunnetaan näyttöä varten.

| Asetuksesi                            | Näet                   |
| ------------------------------------- | ---------------------- |
| Metrijärjestelmä                      | 12 mm                  |
| Imperiaalinen (US) | 0.5 in |

## Yksiköt, jotka eivät muutu

Jotkin yksiköt ovat kansainvälisiä standardeja ja näkyvät samalla tavalla kieli- ja alueasetuksista riippumatta:

| Mittaus                           | Yksikkö                        | Miksi                                     |
| --------------------------------- | ------------------------------ | ----------------------------------------- |
| Ilmanpaine                        | hPa                            | Kansainvälinen meteorologinen standardi   |
| Suunta / suuntima                 | ° (astetta) | Universaali navigointikäytäntö            |
| Säteily                           | μR/hr                          | Standardi dosimetria-yksikkö              |
| GPS-koordinaatit                  | desimaaliasteet                | Kansainvälinen maantieteellinen standardi |
| Kosteus, akku ja maaperän kosteus | %                              | Yleinen                                   |

## Päivämäärä ja aika

Kaikki aikaleimat koko sovelluksessa — viimeksi kuultu, viestien ajat, telemetrialokit, kaavioakselit — noudattavat laitteesi päivämäärä- ja aika-asetuksia.

| Asetus              | Mitä se ohjaa         | Esimerkki                                        |
| ------------------- | --------------------- | ------------------------------------------------ |
| **24 tunnin aika**  | Aikamuoto             | 14:30 vs 2:30 PM |
| **Päivämäärämuoto** | Päivämäärän järjestys | 09/05/2026 vs 05/09/2026                         |

Sovellus käyttää myös **suhteellista aikaa** silloin kun se on järkevää — esimerkiksi “5 min sitten” tai “2 tuntia sitten” radiolistassa — ja tämä mukautuu automaattisesti laitteesi kieleen.

## Mittausjärjestelmän vaihtaminen (Android)

Androidissa mittausjärjestelmäsi (metrinen vs imperial) on sidottu alueasetuksiin:

1. Avaa **Asetukset → Järjestelmä → Kieli ja alue**
2. Vaihda **Alue**- tai **Mittausyksiköt**-asetusta
3. Palaa Meshtasticiin — arvot päivittyvät välittömästi

> 💡 **Vinkki:** Kaikki mittausten muotoilut tehdään keskitetysti ja ne noudattavat käyttöympäristösi alueasetuksia, joten yksiköt pysyvät yhtenäisinä kaikkialla sovelluksessa.

## Aiheeseen liittyvät aiheet

- [Radion mittarit](node-metrics) — missä lämpötila-, etäisyys- ja anturiarvot näytetään
- [Telemetria ja anturit](telemetry-and-sensors) — anturit, jotka tuottavat nämä mittaukset
- [Mittaus ja muotoilu](../developer/measurement) — kehittäjien viite muotoiluapuohjelmista
- [Asetukset — Radio ja käyttäjä](settings-radio-user) — alueasetus, joka määrittää käytettävät mittayksiköt

---

