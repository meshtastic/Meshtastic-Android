---
title: Преведете приложението
parent: Ръководство за потребителя
nav_order: 17
last_updated: 2026-09-11
description: Как се превеждат приложението и документацията му чрез Crowdin, както и насоки за принос към преводите.
aliases:
  - translate
  - crowdin
  - localization
---

# Преведете приложението

Приложението и придружаващата го документация се превеждат в Crowdin – на тази страница е обяснено как да се включите. Приложението използва [Crowdin](https://crowdin.com/) за управление на преводите, извършвани от общността, както за потребителския интерфейс, така и за документацията в самото приложение.

## What Gets Translated

| Resource          | Source Location                                                     | Бележки                                                                |
| ----------------- | ------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| UI strings        | `core/resources/src/commonMain/composeResources/values/strings.xml` | Buttons, labels, messages, and all user-visible text                   |
| User Guide pages  | `docs/en/user/*.md`                                                 | In-app documentation shown in Help & Documentation |
| Fastlane metadata | `fastlane/metadata/android/en-US/`                                  | Google Play listing title, description, and changelogs                 |

> ℹ️ **Note:** Developer Guide pages are English-only. Code-focused documentation targeting contributors is not translated.

## Как да допринесете

1. **Visit the Crowdin project.** Open the [Meshtastic Android Crowdin project](https://crowdin.com/project/meshtastic-android) and sign in or create a free account.
2. **Choose your language.** Select an existing language or request a new one by opening a [GitHub issue](https://github.com/meshtastic/Meshtastic-Android/issues/new).
3. **Translate strings.** Crowdin shows the English source on the left and your translation on the right. Translate each string and save.
4. **Review context.** Many strings include screenshots or context comments — check these to understand where the text appears in the app. A scheduled job pulls approved translations from Crowdin and opens a pull request; they ship once a maintainer merges it and a new build goes out.

> 💡 **Tip:** Keep translations short. UI strings often appear in buttons, chips, or narrow columns. If a translation is significantly longer than the English original, consider abbreviating where the meaning stays clear.

## Добавяне на нов език

Ако езикът ви все още не е включен в списъка в Crowdin:

1. Отворете проблем в [GitHub](https://github.com/meshtastic/Meshtastic-Android/issues/new), като поискате новия език.
2. Поддържащият ще добави езика в Crowdin и ще конфигурира `crowdin.yml`.
3. След като бъде добавен, можете да започнете да превеждате веднага.

## Как са организирани преводите

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
├── fr-rFR/user/         ← French (France)
│   ├── onboarding.md
│   └── ...
├── de-rDE/user/         ← German (Germany)
│   └── ...
└── ...
```

Doc locale folders use Android locale qualifiers, either `{lang}` or `{lang}-r{REGION}` (for example `fr`, `fr-rFR`, `de-rDE`, `ja-rJP`). The `values-*` folders for app strings use bare language codes instead (`values-fr`, `values-de`, `values-ja`), because `crowdin.yml` writes strings with `%two_letters_code%` and doc pages with `%android_code%`. The two sets do not line up one-to-one.

The app automatically selects the correct locale based on your phone's **Language & Region** settings.

A page that came from Crowdin is labeled **Community translated** under its title in the app. If a page has no Crowdin translation for your language yet, the Google-flavor Android build machine-translates the English source on the fly and labels it **Auto-translated** instead; F-Droid and desktop builds show the English page. Your Crowdin translation replaces the machine one as soon as it lands.

## Translation Guidelines

- **Do not translate** technical terms like "LoRa", "MQTT", "BLE", "TAK", "SNR", or "RSSI" — these are universal.
- **Keep placeholders intact.** Strings like `%1$s` or `%d` are filled in at runtime. Do not remove or reorder them unless the grammar of your language requires it.
- **Match tone.** The app uses a friendly, direct voice. Avoid overly formal language.
- **Test if possible.** Switch your phone's language and open the app to see how translations look in context.

## Въпроси?

If you have questions about a specific string's context or need help getting started, open a discussion on the [Meshtastic GitHub Discussions](https://github.com/orgs/meshtastic/discussions) page.

Thank you for helping expand the reach of Meshtastic.

## Свързани теми

- [Units & Locale](units-and-locale) — how the app picks number, date, and unit formats for your region
- [Help & Documentation](help-and-docs) — the in-app docs browser these pages are published to
- [Onboarding](onboarding) — where a new user first meets the translated strings
