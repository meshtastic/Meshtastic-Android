---
title: Tõlgi rakendus
nav_order: 17
last_updated: 2026-10-21
description: Kuidas rakendust ja selle dokumentatsiooni Crowdini kaudu tõlgitakse ja tõlgete panustamise juhised.
aliases:
  - tõlgi
  - crowdin
  - localization
---

# Tõlgi rakendus

The app and its in-app docs are translated on Crowdin — this page shows how to contribute. Rakendus kasutab nii kasutajaliidese kui ka rakendusesisese dokumentatsiooni kogukonna tõlgete haldamiseks [Crowdinit](https://crowdin.com/).

## Mida tõlgitakse

| Resource             | Allika asukoht                                                             | Sõnumid                                                                                |
| -------------------- | -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| UI strings           | `core/resources/src/commonMain/composeResources/values/strings.xml`        | Nupud, sildid, sõnumid ja kogu kasutajale nähtav tekst                                 |
| Setting names        | `core/resources/src/commonMain/composeResources/values/schema_strings.xml` | Names and descriptions of the node's settings, generated from the Meshtastic protobufs |
| Kasutusjuhendi lehed | `docs/en/user/*.md`                                                        | Rakendusesisene dokumentatsioon kuvatakse jaotises Abi ja dokumentatsioon              |
| Fastlane metadata    | `fastlane/metadata/android/en-US/`                                         | Google Play listing title, description, and changelogs                                 |

> ℹ️ **Note:** Developer Guide pages are English-only. Kaastöölistele suunatud koodikeskset dokumentatsiooni ei tõlgita.

## How to Contribute

1. **Külasta Crowdini projekti.** Ava [Meshtastic Android Crowdini projekt](https://crowdin.com/project/meshtastic-android) ja logi sisse või loo tasuta konto.
2. **Vali keel.** Vali olemasolev keel või taotle uut, avades [GitHubi probleemi](https://github.com/meshtastic/Meshtastic-Android/issues/new).
3. **Tõlgi stringe.** Crowdin kuvab ingliskeelse allika vasakul ja sinu tõlke paremal. Tõlki iga string ja salvesta.
4. **Review context.** Many strings include screenshots or context comments — check these to understand where the text appears in the app. A scheduled job pulls translations from Crowdin and opens a pull request; they ship once a maintainer merges it and a new build goes out.

> 💡 \*\*Vihje." Hoia tõlked lühikesed. UI strings often appear in buttons, chips, or narrow columns. Kui tõlge on oluliselt pikem kui ingliskeelne originaal, kaaluge lühendamist kohtades, kus tähendus jääb selgeks.

## Lisa uus keel

If your language is not listed on Crowdin:

1. Ava probleem [GitHubis](https://github.com/meshtastic/Meshtastic-Android/issues/new) ja taotle uut lokaati.
2. A maintainer adds the language to the Crowdin project.
3. Once added, you can begin translating. The app offers every language that has a strings folder, so the first sync that brings yours in adds it to the app.

## How Translations Are Organized

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

Rakendusesisene dokumentatsioon järgib sarnast mustrit jaotises `docs/`:

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

## Translation Guidelines

- **Ära tõlgi** tehnilisi termineid nagu "LoRa", "MQTT", "BLE", "TAK", "SNR", or "RSSI" — need on universaalsed.
- **Jäta kohatäited puutumata.** Stringid nagu `%1$s` või `%d` täidetakse käitusajal. Do not remove or reorder them unless the grammar of your language requires it.
- **Match tone.** The app uses a friendly, direct voice. Avoid overly formal language.
- **Test if possible.** Switch your phone's language and open the app to see how translations look in context.

## Küsimusi?

Kui on küsimusi konkreetse stringi konteksti kohta või vajad abi alustamiseks, ava arutelu lehel [Meshtastic GitHub Discussions](https://github.com/orgs/meshtastic/discussions).

Thank you for helping expand the reach of Meshtastic.

## Seotud teemad

- [Units & Locale](units-and-locale) — how the app picks number, date, and unit formats for your region
- [Help & Documentation](help-and-docs) — the in-app docs browser these pages are published to
- [Onboarding](onboarding) — where a new user first meets the translated strings
