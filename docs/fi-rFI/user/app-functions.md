---
title: Sovellustoiminnot
parent: Käyttöopas
nav_order: 19
last_updated: 2026-06-11
description: Tuo mesh-ominaisuudet Android-järjestelmälle ja laitteessa toimiville tekoälyavustajille (esim. Gemini), jotta ne voivat suorittaa mesh-toimintoja ilman sovelluksen avaamista.
aliases:
  - sovellustoiminnot
  - järjestelmä-ai
  - gemini
  - avustaja
---

# Sovellustoiminnot

Sovellustoiminnot tuovat Meshtastic-ominaisuudet Android-järjestelmälle ja laitteessa toimiville tekoälyavustajille (kuten Gemini) Android App Functions -rajapinnan kautta. Kun ne ovat käytössä, avustaja voi löytää ja käynnistää mesh-toimintoja puolestasi — esimerkiksi lähettää viestin tai tarkistaa mesh-tilan — ilman että avaat sovellusta.

> ⚠️ **Huom:** Sovellustoiminnot ovat saatavilla vain **Google-version Android-laitteissa**.

> ⚠️ **Huom:** Tämä on erillinen in-app **Chirpy** -avustajasta. Sovellustoiminnot mahdollistavat sen, että _järjestelmän_ tekoälyavustaja voi toimia mesh-verkon kautta; Chirpy on Meshtastic-sovelluksen sisäinen keskusteluavustaja.

## Sovellustoimintojen käyttöönotto

Sovellustoimintoja hallitaan kohdasta **Asetukset → Järjestelmän tekoäly** (sovelluksen näkymän nimi on "Järjestelmän tekoäly"). Näyttö sisältää:

- **Pääkytkin**, nimeltään **"Salli tekoälyn käyttö"**, ja alaotsikko _"Salli järjestelmän tekoälyavustajien (esim. Gemini) löytää ja käyttää mesh-toimintoja"_. Kun pois käytöstä, toimintoja ei jaeta järjestelmälle.
- Yksittäinen kytkin jokaiselle toiminnolle, jotta voit paljastaa vain haluamasi ominaisuudet.

Toiminnot on jaettu **Kirjoita**-osioon (toiminnot, jotka muuttavat jotakin tai lähettävät dataa mesh-verkkoon) ja **Lue**-osioon (toiminnot, jotka palauttavat vain tietoa).

![Sovellustoimintojen näkymä, jossa on pääkytkin ja toimintokohtaiset kytkimet](../../assets/screenshots/app-functions_settings.png)

### Kirjoitustoiminnot

| Toiminto          | Mitä se tekee                                                                                                          |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------- |
| **Lähetä viesti** | Lähettää tekstiviestin kontaktille (suora viesti) tai kanavaan, enintään 237 tavua. |

### Lukutoiminnot

| Toiminto                         | Mitä se palauttaa                                           |
| -------------------------------- | ----------------------------------------------------------- |
| **Hae mesh-verkko-tila**         | Koko mesh-verkon tila.                      |
| **Hae radiolista**               | Mesh-verkon radiolista.                     |
| **Hae kanavatiedot**             | Tietoa kanavistasi.                         |
| **Hae laitteen tila**            | Yhdistetyn radion tila.                     |
| **Hae radion tiedot**            | Yksityiskohtaiset tiedot tietystä radiosta. |
| **Hae viimeisimmät viestit**     | Viimeisimmät viestisi keskusteluista.       |
| **Hae lukemattomien yhteenveto** | Yhteenveto lukemattomista viesteistä.       |
| **Hae mesh-metriikat**           | Mesh-verkon telemetria ja metriikat.        |

## Yksityisyys

> 🔒 **Tietosuoja:** **Lähetä viesti** -toiminnon avulla avustaja voi lähettää viestejä mesh-verkkoosi puolestasi. Ota käyttöön vain ne toiminnot, joihin luotat avustajan saavan käyttää. Lukutoiminnot tuovat radion, viestien ja metriikoiden tiedot avustajan käyttöön — ota käyttöön vain se, mitä haluat jakaa. Jokaisella toiminnolla on oma kytkin, ja pääkytkin poistaa kaikki käytöstä kerralla.

## Aiheeseen liittyvät aiheet

- [Viestit ja kanavat](messages-and-channels) — viestien lähettäminen suoraan sovelluksessa
- [Radiot](nodes) — radiolista, josta lukutoiminnot hakevat tiedot
- [Radiometriikat](node-metrics) — telemetria, jonka pohjalta **Hae mesh-metriikat** muodostuu

---

