---
title: Translate the App
parent: User Guide
nav_order: 17
last_updated: 2026-06-25
description: How the app and its documentation are translated via Crowdin, and guidelines for contributing translations.
aliases:
  - translate
  - crowdin
  - localization
---

# Translate the App

Contributing translations helps make Meshtastic accessible to a wider audience. The app uses [Crowdin](https://crowdin.com/) to manage community translations for both the user interface and in-app documentation.

---

## What Gets Translated

| Resource          | Source Location                                                     | Knoten                                                                 |
| ----------------- | ------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| UI strings        | `core/resources/src/commonMain/composeResources/values/strings.xml` | Buttons, labels, messages, and all user-visible text                   |
| User Guide pages  | `docs/en/user/*.md`                                                 | In-app documentation shown in Help & Documentation |
| Fastlane metadata | `fastlane/metadata/android/en-US/`                                  | App Store listing title, description, and changelogs                   |

> ⚠️ **Note:** Developer Guide pages are English-only. Code-focused documentation targeting contributors is not translated.

---

## Wie man beitragen kann

1. **Visit the Crowdin project.** Open the [Meshtastic Android Crowdin project](https://crowdin.com/project/meshtastic-android) and sign in or create a free account.
2. **Choose your language.** Select an existing language or request a new one by opening a [GitHub issue](https://github.com/meshtastic/Meshtastic-Android/issues/new).
3. **Translate strings.** Crowdin shows the English source on the left and your translation on the right. Translate each string and save.
4. **Review context.** Many strings include screenshots or context comments — check these to understand where the text appears in the app.
5. **Submit.** Approved translations are automatically merged into the next release.

> 💡 **Tip:** Keep translations short. UI strings often appear in buttons, chips, or narrow columns. If a translation is significantly longer than the English original, consider abbreviating where the meaning stays clear.

---

## Adding a New Language

If your language is not yet listed on Crowdin:

1. Open an issue on [GitHub](https://github.com/meshtastic/Meshtastic-Android/issues/new) requesting the new locale.
2. A maintainer will add the language to Crowdin and configure `crowdin.yml`.
3. Once added, you can begin translating immediately.

---

## How Translations Are Organized

The Android app uses **Compose Multiplatform resources** for all user-visible strings:

```
core/resources/src/commonMain/composeResources/
├── values/              ← Englisch (Standard)
│   └── strings.xml
├── values-de/           ← Deutsch
│   └── strings.xml
├── values-fr/           ← Französisch
│   └── strings.xml
└── ...
```

In-app documentation follows a similar pattern under `docs/`:

```
docs/
├── en/user/             ← Englisch  (Standard)
│   ├── onboarding.md
│   └── ...
├── fr-rFR/user/         ← Französisch (Frankreich)
│   ├── onboarding.md
│   └── ...
├── de-rDE/user/         ← Deustch (Deutschland)
│   └── ...
└── ...
```

Locale folders use the Android resource convention `{lang}-r{REGION}` (e.g. `fr-rFR`, `de-rDE`, `ja-rJP`), matching the `values-*` directories used for app strings.

The app automatically selects the correct locale based on your device's **Language & Region** settings.

---

## Translation Guidelines

- **Do not translate** technical terms like "LoRa", "MQTT", "BLE", "TAK", "SNR", or "RSSI" — these are universal.
- **Keep placeholders intact.** Strings like `%1$s` or `%d` are filled in at runtime. Do not remove or reorder them unless the grammar of your language requires it.
- **Match tone.** The app uses a friendly, direct voice. Avoid overly formal language.
- **Test if possible.** Switch your device language and open the app to see how translations look in context.

---

## Questions?

Wenn du Fragen zum Kontext einer bestimmten Zeichenkette hast oder Hilfe bei den ersten Schritten benötigst, starte eine Diskussion auf der Seite [Meshtastic GitHub Diskussion](https://github.com/meshtastic/Meshtastic-Android/discussions).

Vielen Dank, dass Sie dabei geholfen haben, die Reichweite von Meshtastic zu vergrößern!
