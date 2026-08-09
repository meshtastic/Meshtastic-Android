---
title: Käännä sovellus
parent: Käyttöopas
nav_order: 17
last_updated: 2026-06-25
description: Miten sovellus ja sen dokumentaatio käännetään Crowdinin avulla sekä ohjeet käännöksiin osallistumiseen.
aliases:
  - käännä
  - crowdin
  - lokalisointi
---

# Käännä sovellus

Käännöksiin osallistuminen auttaa tekemään Meshtasticista saavutettavamman laajemmalle yleisölle. Sovellus käyttää [Crowdinia](https://crowdin.com/) yhteisökäännösten hallintaan sekä käyttöliittymän sovelluksen sisäisten dokumentaatioiden hallintaan.

---

## Mitä käännetään

| Resurssi            | Lähteen sijainti                                                    | Viestit                                                             |
| ------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------- |
| UI-tekstit          | `core/resources/src/commonMain/composeResources/values/strings.xml` | Painikkeet, otsikot, viestit ja kaikki käyttäjälle näkyvä teksti    |
| Käyttöoppaan sivut  | `docs/en/user/*.md`                                                 | Sovelluksen sisäinen dokumentaatio Ohjeet ja dokumentaatio -osiossa |
| Fastlane-metatiedot | fastlane/metadata/android/en-US/                                    | Sovelluskaupan listauksen otsikko, kuvaus ja muutoslokit            |

> ⚠️ **Huomautus:** Kehittäjän oppaan sivut ovat saatavilla vain englanniksi. Koodiin keskittyvää dokumentaatiota, joka on suunnattu kehitystyöhön osallistuville, ei käännetä.

---

## Kuinka voi osallistua

1. Avaa Meshtastic Android Crowdin -projekti (https://crowdin.com/project/meshtastic-android) ja kirjaudu sisään tai luo ilmainen tili.
2. **Valitse kielesi.** Valitse olemassa oleva kieli tai pyydä uutta avaamalla [GitHub-issue](https://github.com/meshtastic/Meshtastic-Android/issues/new).
3. **Käännä merkkijonot.** Crowdin näyttää englanninkielisen lähteen vasemmalla ja käännöksesi oikealla. Käännä jokainen merkkijono ja tallenna.
4. **Tarkista konteksti.** Monet merkkijonot sisältävät kuvakaappauksia tai kontekstikommentteja — tarkista nämä ymmärtääksesi, missä teksti näkyy sovelluksessa.
5. **Lähetä.** Hyväksytyt käännökset yhdistetään automaattisesti seuraavaan julkaisuun.

> 💡 **Vinkki:** Pidä käännökset lyhyinä. Käyttöliittymän tekstit näkyvät usein painikkeissa, tunnisteissa tai kapeissa sarakkeissa. Jos käännös on huomattavasti pidempi kuin englanninkielinen alkuperäinen, harkitse tiivistämistä niin, että merkitys säilyy selkeänä.

---

## Uuden kielen lisääminen

Jos kieltäsi ei ole vielä listattu Crowdinissa:

1. Avaa GitHub-issue (https://github.com/meshtastic/Meshtastic-Android/issues/new) ja pyydä uutta kielialuetta.
2. Ylläpitäjä lisää kielen Crowdin-projektiin ja määrittää `crowdin.yml`-asetukset.
3. Kun kieli on lisätty, voit aloittaa kääntämisen heti.

---

## Kuinka käännökset on järjestetty

Android-sovellus käyttää **Compose Multiplatform -resursseja** kaikille käyttäjälle näkyville teksteille:

```
core/resources/src/commonMain/composeResources/
├── values/              ← English (default)
│   └── strings.xml
├── values-de/           ← German
│   └── strings.xml
├── values-fr/           ← French
│   └── strings.xml
└── ...
```

Sovelluksen sisäinen dokumentaatio noudattaa samaa rakennetta `docs/` -hakemistossa:

```
docs/
├── en/user/             ← English source (default)
│   ├── onboarding.md
│   └── ...
├── fr-rFR/user/         ← French (France)
│   ├── onboarding.md
│   └── ...
├── de-rDE/user/         ← German (Germany)
│   └── ...
└── ...
```

Aluekansiot käyttävät Androidin resurssikäytäntöä `{kieli}-r{ALUE}` (esim. `fr-rFR`, `de-rDE`, `ja-rJP`), joka vastaa sovelluksen merkkijonoissa käytettyjä `values-*`-hakemistoja.

Sovellus valitsee automaattisesti oikean kielialueen laitteen **Kieli & alue** -asetusten perusteella.

---

## Käännösohjeet

- **Älä käännä** teknisiä termejä kuten "LoRa", "MQTT", "BLE", "TAK", "SNR" tai "RSSI" — nämä ovat yleisiä.
- **Pidä paikkamerkit ennallaan.** Merkkijonot kuten `%1$s` tai `%d` täytetään ajon aikana. Älä poista tai muuta niiden järjestystä, ellei kielesi kielioppi sitä vaadi.
- **Säilytä sävy.** Sovellus käyttää ystävällistä ja suoraa tyyliä. Vältä liian muodollista kieltä.
- **Testaa jos mahdollista.** Vaihda laitteesi kieli ja avaa sovellus nähdäksesi käännökset kontekstissa.

---

## Kysyttävää?

Jos sinulla on kysyttävää tietyn merkkijonon asiayhteydestä tai tarvitset apua alkuun pääsemisessä, avaa keskustelu [Meshtastic GitHub Discussions](https://github.com/orgs/meshtastic/discussions) -sivulla.

Kiitos, että autat laajentamaan Meshtasticin tavoittavuutta!
