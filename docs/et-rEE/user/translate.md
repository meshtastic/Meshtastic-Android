---
title: Tõlgi rakendus
parent: User Guide
nav_order: 17
last_updated: 2026-06-25
description: Kuidas rakendust ja selle dokumentatsiooni Crowdini kaudu tõlgitakse ja tõlgete panustamise juhised.
aliases:
  - tõlgi
  - crowdin
  - localization
---

# Tõlgi rakendus

Tõlgete koostamisele kaasaaitamine aitab Meshtasticut laiemale publikule kättesaadavaks teha. Rakendus kasutab nii kasutajaliidese kui ka rakendusesisese dokumentatsiooni kogukonna tõlgete haldamiseks [Crowdinit](https://crowdin.com/).

---

## Mida tõlgitakse

| Resource          | Source Location                                                     | Sõnumid                                                                |
| ----------------- | ------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| UI strings        | `core/resources/src/commonMain/composeResources/values/strings.xml` | Buttons, labels, messages, and all user-visible text                   |
| User Guide pages  | `docs/en/user/*.md`                                                 | In-app documentation shown in Help & Documentation |
| Fastlane metadata | `fastlane/metadata/android/en-US/`                                  | App Store listing title, description, and changelogs                   |

> ⚠️ **Note:** Developer Guide pages are English-only. Kaastöölistele suunatud koodikeskset dokumentatsiooni ei tõlgita.

---

## How to Contribute

1. **Külasta Crowdini projekti.** Ava [Meshtastic Android Crowdini projekt](https://crowdin.com/project/meshtastic-android) ja logi sisse või loo tasuta konto.
2. **Vali keel.** Vali olemasolev keel või taotle uut, avades [GitHubi probleemi](https://github.com/meshtastic/Meshtastic-Android/issues/new).
3. **Tõlgi stringe.** Crowdin kuvab ingliskeelse allika vasakul ja sinu tõlke paremal. Tõlki iga string ja salvesta.
4. **Review context.** Many strings include screenshots or context comments — check these to understand where the text appears in the app.
5. **Submit.** Approved translations are automatically merged into the next release.

> 💡 **Tip:** Keep translations short. UI strings often appear in buttons, chips, or narrow columns. If a translation is significantly longer than the English original, consider abbreviating where the meaning stays clear.

---

## Lisa uus keel

Kui teie keelt Crowdinis veel pole:

1. Ava probleem [GitHubis](https://github.com/meshtastic/Meshtastic-Android/issues/new) ja taotle uut locale.
2. Hooldaja lisab keele Crowdinile ja seadistab faili `crowdin.yml`.
3. Pärast lisamist saad kohe tõlkima hakata.

---

## How Translations Are Organized

The Android app uses **Compose Multiplatform resources** for all user-visible strings:

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

In-app documentation follows a similar pattern under `docs/`:

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

Locale folders use the Android resource convention `{lang}-r{REGION}` (e.g. `fr-rFR`, `de-rDE`, `ja-rJP`), matching the `values-*` directories used for app strings.

The app automatically selects the correct locale based on your device's **Language & Region** settings.

---

## Translation Guidelines

- **Ära tõlgi** tehnilisi termineid nagu "LoRa", "MQTT", "BLE", "TAK", "SNR", or "RSSI" — need on universaalsed.
- **Keep placeholders intact.** Strings like `%1$s` or `%d` are filled in at runtime. Do not remove or reorder them unless the grammar of your language requires it.
- **Match tone.** The app uses a friendly, direct voice. Avoid overly formal language.
- **Testi võimalusel.** Vaheta seadme keelt ja ava rakendus, et näha, kuidas tõlked kontekstis välja näevad.

---

## Questions?

Kui on küsimusi konkreetse stringi konteksti kohta või vajad abi alustamisel, ava arutelu lehel [Meshtastic GitHub Discussions](https://github.com/meshtastic/Meshtastic-Android/discussions).

Tänan teid Meshtasticu haardeala laiendamise eest!
