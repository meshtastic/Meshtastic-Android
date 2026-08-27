---
title: Yksiköt, mittaus ja kieli- ja alueasetukset
parent: Käyttöopas
nav_order: 16
last_updated: 2026-08-27
description: Miten sovellus muotoilee lämpötilan, etäisyyden, nopeuden ja muut mittayksiköt laitteesi alueasetusten perusteella.
---

# Yksiköt, mittaus ja kieli- ja alueasetukset

The Meshtastic app automatically displays temperatures, distances, speeds, and times in the units your device is configured to use. If your device's settings can't express the units you want, an in-app **Units** setting overrides them.

---

## Miten se toimii

Meshtastic-radiot lähettävät tiedot aina **metrisissä yksiköissä** (metri, °C, m/s, hPa jne.). Kun sovellus vastaanottaa nämä tiedot, se muuntaa ja näyttää arvot laitteesi alueasetusten mukaisessa yksikköjärjestelmässä.

Androidissa mittausasetukset määräytyvät järjestelmän **Kieli ja alue** -asetusten mukaan. Työpöytäversiossa (JVM) sovellus käyttää JVM:n oletus-`Locale`-asetusta.

Units follow your device's **region**, not the display language. Choosing a plain language — like **English** in the app's own Language setting or Android's per-app language — keeps the region your device is set to; only a choice that names a region of its own (like **English (Canada)**) brings that region's units with it. On Android 16+, the system-wide **Measurement system** preference overrides the region entirely.

> 💡 **Tip:** By default there is nothing to configure — change your system measurement preferences and every screen in Meshtastic updates automatically. If your device offers no working region or measurement setting (some manufacturer builds don't), set **Settings → Units** in the app instead.

---

## The Radio's Own Screen Is Separate

**Device → Display → Units** configures the screen on the radio, not the app. So do **Use 12-Hour Clock** and **Always Point North** — all three apply to the node's display only. Temperature on that screen has its own setting, [**Telemetry → Display Fahrenheit**](https://meshtastic.org/docs/configuration/module/telemetry#display-fahrenheit).

If your node list shows miles while the radio's screen shows kilometres, this is why: the two are set in different places. Changing the device setting will never alter what the app displays. See the [Display Config](https://meshtastic.org/docs/configuration/radio/display) guide on meshtastic.org for the device-side options.

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

## Changing Your Measurement System

By default the app follows your device, and your measurement system (metric vs imperial) is tied to your region setting:

1. Avaa **Asetukset → Järjestelmä → Kieli ja alue**
2. Change your **Region**
3. On Android 16+, **Measurement system** overrides the region for every measurement
4. Android 14:ssä lämpötila-asetus voidaan määrittää erikseen kohdassa **Alueasetukset → Lämpötila**
5. Palaa Meshtasticiin — arvot päivittyvät välittömästi

Not every English region is fully metric. **English (United Kingdom)** uses miles and feet for distance, so the node list shows miles and altitude in feet. For metric distances, set the app's **Units** setting to Metric (below), or choose a fully metric region such as English (Canada), English (Ireland), or English (New Zealand).

Some phones do not offer the **Regional preferences** menu at all and list only English (United States). On those devices, use the app's **Units** setting below.

### Overriding the units in the app

Not every device can express every preference — some manufacturer builds ship no regional preferences at all, some
offer only one English variant, and UK regions are imperial for distance even if you'd rather read altitude in
metres. For those cases the app has its own switch:

1. Open **Meshtastic Settings → Units**
2. Choose **System default**, **Metric**, or **Imperial**
3. Every screen updates immediately — no restart needed

**System default** follows your device as described above. Forcing **Metric** or **Imperial** applies to
everything, temperature included (metric → °C, imperial → °F), even where the device's own regional preferences say
otherwise. The setting exists on Android and Desktop alike.

> 💡 **Vinkki:** Kaikki mittausten muotoilut tehdään keskitetysti ja ne noudattavat käyttöympäristösi alueasetuksia, joten yksiköt pysyvät yhtenäisinä kaikkialla sovelluksessa.

## Aiheeseen liittyvät aiheet

- [Radion mittarit](node-metrics) — missä lämpötila-, etäisyys- ja anturiarvot näytetään
- [Telemetria ja anturit](telemetry-and-sensors) — anturit, jotka tuottavat nämä mittaukset
- [Mittaus ja muotoilu](../developer/measurement) — kehittäjien viite muotoiluapuohjelmista
- [Asetukset — Radio ja käyttäjä](settings-radio-user) — alueasetus, joka määrittää käytettävät mittayksiköt
- [Display Config](https://meshtastic.org/docs/configuration/radio/display) — units, clock, and compass settings for the radio's own screen, on meshtastic.org

---

