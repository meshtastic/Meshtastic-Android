---
title: Käännä sovellus
nav_order: 17
last_updated: 2026-10-21
description: Miten sovellus ja sen dokumentaatio käännetään Crowdinin avulla sekä ohjeet käännöksiin osallistumiseen.
aliases:
  - käännä
  - crowdin
  - lokalisointi
---

# Käännä sovellus

Sovellus ja sen sisäinen dokumentaatio käännetään Crowdinissa – tällä sivulla kerrotaan, miten voit osallistua. Sovellus käyttää [Crowdinia](https://crowdin.com/) yhteisökäännösten hallintaan sekä käyttöliittymän sovelluksen sisäisten dokumentaatioiden hallintaan.

## Mitä käännetään

| Resurssi            | Lähteen sijainti                                                           | Viestit                                                                                |
| ------------------- | -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| UI-tekstit          | `core/resources/src/commonMain/composeResources/values/strings.xml`        | Painikkeet, otsikot, viestit ja kaikki käyttäjälle näkyvä teksti                       |
| Setting names       | `core/resources/src/commonMain/composeResources/values/schema_strings.xml` | Names and descriptions of the node's settings, generated from the Meshtastic protobufs |
| Käyttöoppaan sivut  | `docs/en/user/*.md`                                                        | Sovelluksen sisäinen dokumentaatio Ohjeet ja dokumentaatio -osiossa                    |
| Fastlane-metatiedot | fastlane/metadata/android/en-US/                                           | Google Play listing title, description, and changelogs                                 |

> ℹ️ **Huomautus:** Developer Guide -sivut ovat saatavilla vain englanniksi. Koodiin keskittyvää dokumentaatiota, joka on suunnattu kehitystyöhön osallistuville, ei käännetä.

## Kuinka voi osallistua

1. Avaa Meshtastic Android Crowdin -projekti (https://crowdin.com/project/meshtastic-android) ja kirjaudu sisään tai luo ilmainen tili.
2. **Valitse kielesi.** Valitse olemassa oleva kieli tai pyydä uutta avaamalla [GitHub-issue](https://github.com/meshtastic/Meshtastic-Android/issues/new).
3. **Käännä merkkijonot.** Crowdin näyttää englanninkielisen lähteen vasemmalla ja käännöksesi oikealla. Käännä jokainen merkkijono ja tallenna.
4. **Tarkista konteksti.** Monet merkkijonot sisältävät kuvakaappauksia tai kontekstikommentteja — tarkista nämä ymmärtääksesi, missä teksti näkyy sovelluksessa. A scheduled job pulls translations from Crowdin and opens a pull request; they ship once a maintainer merges it and a new build goes out.

> 💡 **Vinkki:** Pidä käännökset lyhyinä. Käyttöliittymän tekstit näkyvät usein painikkeissa, tunnisteissa tai kapeissa sarakkeissa. Jos käännös on huomattavasti pidempi kuin englanninkielinen alkuperäinen, harkitse tiivistämistä niin, että merkitys säilyy selkeänä.

## Uuden kielen lisääminen

If your language is not listed on Crowdin:

1. Avaa GitHub-issue (https://github.com/meshtastic/Meshtastic-Android/issues/new) ja pyydä uutta kielialuetta.
2. A maintainer adds the language to the Crowdin project.
3. Once added, you can begin translating. The app offers every language that has a strings folder, so the first sync that brings yours in adds it to the app.

## Kuinka käännökset on järjestetty

Android-sovellus käyttää **Compose Multiplatform -resursseja** kaikille käyttäjälle näkyville teksteille:

```text
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

```text
docs/
├── en/user/             ← English source (default)
│   ├── onboarding.md
│   └── ...
├── fr/user/             ← French
│   ├── onboarding.md
│   └── ...
├── de/user/             ← German
│   └── ...
└── ...
```

App strings and doc pages use the same locale names, so `values-de` and `docs/de` hold the same language. Most are a bare language code, which covers every region of that language. A few name a region or script where the language needs it: `pt-rBR` (Portuguese, Brazil), `fr-rCA` (French, Canada), `zh-rCN` and `zh-rTW` (Chinese), and `b+sr+Latn` for Serbian in Latin script, where plain `sr` is Cyrillic. Norwegian Bokmål is `nb`.

The app follows your phone's language unless you pick one under **Language** in the app's settings.

A page that came from Crowdin is labeled **Community translated** under its title in the app. If a page has no Crowdin translation for your language, the Google-flavor Android build machine-translates the English source on the fly and labels it **Auto-translated** instead; F-Droid and desktop builds show the English page. Your Crowdin translation replaces the machine one as soon as it lands.

## Käännösohjeet

- **Älä käännä** teknisiä termejä kuten "LoRa", "MQTT", "BLE", "TAK", "SNR" tai "RSSI" — nämä ovat yleisiä.
- **Pidä paikkamerkit ennallaan.** Merkkijonot kuten `%1$s` tai `%d` täytetään ajon aikana. Älä poista tai muuta niiden järjestystä, ellei kielesi kielioppi sitä vaadi.
- **Säilytä sävy.** Sovellus käyttää ystävällistä ja suoraa tyyliä. Vältä liian muodollista kieltä.
- **Testaa mahdollisuuksien mukaan.** Vaihda puhelimesi kieli ja avaa sovellus nähdäksesi, miltä käännökset näyttävät käytännössä.

## Kysyttävää?

Jos sinulla on kysyttävää tietyn merkkijonon asiayhteydestä tai tarvitset apua alkuun pääsemisessä, avaa keskustelu [Meshtastic GitHub Discussions](https://github.com/orgs/meshtastic/discussions) -sivulla.

Kiitos, että autat laajentamaan Meshtasticin tavoittavuutta.

## Aiheeseen liittyvät aiheet

- [Yksiköt ja alue](units-and-locale) – miten sovellus valitsee alueellesi sopivat lukujen, päivämäärien ja yksiköiden muodot
- [Ohje ja dokumentaatio](help-and-docs) – sovelluksen sisäinen dokumentaatio, johon nämä sivut julkaistaan
- [Käyttöönotto](onboarding) – missä uusi käyttäjä kohtaa ensimmäisenä käännetyt tekstit
