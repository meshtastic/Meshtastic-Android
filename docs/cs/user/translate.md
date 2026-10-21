---
title: Překlad aplikace
nav_order: 17
last_updated: 2026-10-21
description: Jak se aplikace a její dokumentace překládají pomocí Crowdin a jak přispívat k překladům.
aliases:
  - translate
  - crowdin
  - localization
---

# Překlad aplikace

Aplikace a její dokumentace v aplikaci se překládají na Crowdin – tato stránka ukazuje, jak přispívat k překladům. Aplikace používá [Crowdin](https://crowdin.com/) ke správě komunitních překladů uživatelského rozhraní i dokumentace přímo v aplikaci.

## Co se překládá

| Zdroj                        | Umístění zdroje                                                            | Poznámka                                                                               |
| ---------------------------- | -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| UI řetězce                   | `core/resources/src/commonMain/composeResources/values/strings.xml`        | Tlačítka, popisky, zprávy a veškerý text zobrazený uživateli                           |
| Setting names                | `core/resources/src/commonMain/composeResources/values/schema_strings.xml` | Names and descriptions of the node's settings, generated from the Meshtastic protobufs |
| Stránky uživatelské příručky | `docs/en/user/*.md`                                                        | Dokumentace v aplikaci zobrazená v části Nápověda a dokumentace                        |
| Fastlane metadata            | `fastlane/metadata/android/en-US/`                                         | Google Play listing title, description, and changelogs                                 |

> ℹ️ **Poznámka:** Stránky Vývojářské příručky jsou pouze v angličtině. Dokumentace zaměřená na kód pro přispěvatele se nepřekládá.

## Jak přispět

1. **Navštivte projekt na Crowdinu.** Otevřete [Meshtastic Android Crowdin project](https://crowdin.com/project/meshtastic-android) a přihlaste se nebo si zdarma vytvořte účet.
2. **Vyberte svůj jazyk.** Vyberte některý z dostupných jazyků nebo požádejte o přidání nového jazyka založením [GitHub problému](https://github.com/meshtastic/Meshtastic-Android/issues/new).
3. **Přeložte řetězce.** Crowdin zobrazuje vlevo anglický zdrojový text a vpravo váš překlad. Přeložte každý řetězec a uložte ho.
4. **Zkontrolujte kontext.** Mnoho řetězců obsahuje snímky obrazovky nebo poznámky ke kontextu – podívejte se na ně, abyste zjistili, kde se text v aplikaci zobrazuje. A scheduled job pulls translations from Crowdin and opens a pull request; they ship once a maintainer merges it and a new build goes out.

> 💡 **Tip:** Překlady udržujte krátké. UI řetězce se často zobrazují na tlačítkách, štítcích nebo v úzkých sloupcích. Pokud je překlad výrazně delší než anglický originál, zvažte jeho zkrácení, pokud zůstane význam jasný.

## Přidání nového jazyka

If your language is not listed on Crowdin:

1. Otevřete problém na [GitHub](https://github.com/meshtastic/Meshtastic-Android/issues/new) s žádostí o přidání nového jazyka.
2. A maintainer adds the language to the Crowdin project.
3. Once added, you can begin translating. The app offers every language that has a strings folder, so the first sync that brings yours in adds it to the app.

## Jak jsou překlady uspořádány

The Android app uses **Compose Multiplatform resources** for all user-visible strings:

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

In-app documentation follows a similar pattern under `docs/`:

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

## Pokyny pro překlad

- Technické výrazy jako „LoRa“, „MQTT“, „BLE“, „TAK“, „SNR“ nebo „RSSI“ nepřekládejte – jsou univerzální.
- \*\*Zástupné symboly ponechte beze změny. \*\* Řetězce jako `%1$s` nebo `%d`se doplňují za běhu aplikace. Neodstraňujte je ani neměňte jejich pořadí, pokud to nevyžaduje gramatika vašeho jazyka.
- **Dodržujte tón komunikace.** Aplikace používá přátelský a přímý styl. Vyhněte se příliš formálnímu jazyku.
- **Pokud můžete, překlad otestujte.** Přepněte jazyk telefonu a otevřete aplikaci, abyste viděli, jak překlady vypadají v kontextu.

## Máte dotazy?

Pokud máte dotazy ke kontextu konkrétního řetězce nebo potřebujete pomoct se začátky, založte diskuzi na stránce [Meshtastic GitHub Discussions](https://github.com/orgs/meshtastic/discussions).

Děkujeme, že pomáháte rozšiřovat Meshtastic.

## Související témata

- [Units & Locale](units-and-locale) — how the app picks number, date, and unit formats for your region
- [Help & Documentation](help-and-docs) — the in-app docs browser these pages are published to
- [Onboarding](onboarding) — where a new user first meets the translated strings
