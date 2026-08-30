---
title: Перевод приложения
parent: Руководство пользователя
nav_order: 17
last_updated: 2026-08-29
description: Как приложение и его документация переводятся через Crowdin и рекомендации по внесению переводов.
aliases:
  - translate
  - crowdin
  - localization
---

# Перевод приложения

The app and its in-app docs are translated on Crowdin — this page shows how to contribute. Приложение использует [Crowdin](https://crowdin.com/) для управления переводами сообщества как для пользовательского интерфейса, так и для документации в приложении.

## Что переводится

| Ресурс                            | Исходное местоположение                                             | Заметки                                                                  |
| --------------------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| Строки интерфейса                 | `core/resources/src/commonMain/composeResources/values/strings.xml` | Кнопки, ярлыки, сообщения и весь видимый пользователю текст              |
| Страницы руководства пользователя | `docs/en/user/*.md`                                                 | Встроенная документация, отображаемая в разделе «Справка и документация» |
| Метаданные Fastlane               | `fastlane/metadata/android/en-US/`                                  | Название, описание и журналы изменений в App Store                       |

> ℹ️ **Note:** Developer Guide pages are English-only. Документация, ориентированная на код и для участников, не переводится.

## Как внести вклад

1. **Посетите проект Crowdin.** Откройте [проект Meshtastic Android на Crowdin](https://crowdin.com/project/meshtastic-android) и войдите в систему или создайте бесплатный аккаунт.
2. **Выберите свой язык.** Выберите существующий язык или запросите новый, открыв [GitHub issue](https://github.com/meshtastic/Meshtastic-Android/issues/new).
3. **Переводите строки.** Crowdin показывает английский исходный текст слева и ваш перевод справа. Переведите каждую строку и сохраните.
4. **Проверьте контекст.** Многие строки содержат скриншоты или комментарии о контексте — проверьте их, чтобы понять, где данный текст появляется в приложении. Approved translations are automatically merged into the next release.

> 💡 **Совет:** Сохраняйте переводы краткими. Строки интерфейса часто появляются на кнопках, фишках или узких колонках. Если перевод значительно длиннее английского оригинала, рассмотрите возможность сокращения, чтобы смысл оставался понятным.

## Добавление нового языка

Если твоего языка еще нет в списке на Crowdin:

1. Открой задачу на [GitHub](https://github.com/meshtastic/Meshtastic-Android/issues/new) для запроса нового языка.
2. Поддерживающий добавит язык в Crowdin и настроит `crowdin.yml`.
3. После добавления вы можете начать переводить сразу.

## Как организованы переводы

Приложение для Android использует **Compose Multiplatform resources** для всех строк, видимых пользователю:

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

Встроенная документация следует аналогичной схеме в папке `docs/`:

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

Папки локалей используют конвенцию ресурсов Android `{lang}-r{REGION}` (например, `fr-rFR`, `de-rDE`, `ja-rJP`), совпадая с директориями `values-*`, которые используются для строк в приложении.

The app automatically selects the correct locale based on your phone's **Language & Region** settings.

## Руководство по переводу

- **Не переводите** технические термины, такие как "LoRa", "MQTT", "BLE", "TAK", "SNR" или "RSSI" — они универсальны.
- **Сохраняйте заполнители без изменений.** Строки, такие как `%1$s` или `%d`, заполняются во время выполнения. Не удаляйте и не меняйте их порядок, если только грамматика вашего языка не требует этого.
- **Согласуйте тон.** Приложение использует дружелюбный, прямой стиль общения. Избегайте чрезмерно официального языка.
- **Test if possible.** Switch your phone's language and open the app to see how translations look in context.

## Вопросы?

Если у тебя есть вопросы о контексте конкретной строки или нужна помощь для начала работы, создайте обсуждение на странице [Meshtastic GitHub Discussions](https://github.com/orgs/meshtastic/discussions).

Thank you for helping expand the reach of Meshtastic.

## Связанные темы

- [Units & Locale](units-and-locale) — how the app picks number, date, and unit formats for your region
- [Help & Documentation](help-and-docs) — the in-app docs browser these pages are published to
- [Onboarding](onboarding) — where a new user first meets the translated strings
