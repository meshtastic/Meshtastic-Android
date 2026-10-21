---
title: Перевод приложения
nav_order: 17
last_updated: 2026-10-21
description: Как приложение и его документация переводятся через Crowdin и рекомендации по внесению переводов.
aliases:
  - translate
  - crowdin
  - localization
---

# Перевод приложения

Приложение и его встроенные документы переведены на Crowdin — на этой странице показано, как внести свой вклад. Приложение использует [Crowdin](https://crowdin.com/) для управления переводами сообщества как для пользовательского интерфейса, так и для документации в приложении.

## Что переводится

| Ресурс                            | Исходное местоположение                                                    | Заметки                                                                                |
| --------------------------------- | -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| Строки интерфейса                 | `core/resources/src/commonMain/composeResources/values/strings.xml`        | Кнопки, ярлыки, сообщения и весь видимый пользователю текст                            |
| Setting names                     | `core/resources/src/commonMain/composeResources/values/schema_strings.xml` | Names and descriptions of the node's settings, generated from the Meshtastic protobufs |
| Страницы руководства пользователя | `docs/en/user/*.md`                                                        | Встроенная документация, отображаемая в разделе «Справка и документация»               |
| Метаданные Fastlane               | `fastlane/metadata/android/en-US/`                                         | Название, описание и журналы изменений в Google Play                                   |

> ℹ️ **Примечание:** Страницы Руководства разработчика только на английском. Документация, ориентированная на код и для участников, не переводится.

## Как внести вклад

1. **Посетите проект Crowdin.** Откройте [проект Meshtastic Android на Crowdin](https://crowdin.com/project/meshtastic-android) и войдите в систему или создайте бесплатный аккаунт.
2. **Выберите свой язык.** Выберите существующий язык или запросите новый, открыв [GitHub issue](https://github.com/meshtastic/Meshtastic-Android/issues/new).
3. **Переводите строки.** Crowdin показывает английский исходный текст слева и ваш перевод справа. Переведите каждую строку и сохраните.
4. **Проверьте контекст.** Многие строки содержат скриншоты или комментарии о контексте — проверьте их, чтобы понять, где данный текст появляется в приложении. A scheduled job pulls translations from Crowdin and opens a pull request; they ship once a maintainer merges it and a new build goes out.

> 💡 **Совет:** Сохраняйте переводы краткими. Строки интерфейса часто появляются на кнопках, фишках или узких колонках. Если перевод значительно длиннее английского оригинала, рассмотрите возможность сокращения, чтобы смысл оставался понятным.

## Добавление нового языка

If your language is not listed on Crowdin:

1. Открой задачу на [GitHub](https://github.com/meshtastic/Meshtastic-Android/issues/new) для запроса нового языка.
2. A maintainer adds the language to the Crowdin project.
3. Once added, you can begin translating. The app offers every language that has a strings folder, so the first sync that brings yours in adds it to the app.

## Как организованы переводы

Приложение для Android использует **Compose Multiplatform resources** для всех строк, видимых пользователю:

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

Встроенная документация следует аналогичной схеме в папке `docs/`:

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

Страница, пришедшая из Crowdin, в приложении под своим заголовком отмечена как **Переведено сообществом**. If a page has no Crowdin translation for your language, the Google-flavor Android build machine-translates the English source on the fly and labels it **Auto-translated** instead; F-Droid and desktop builds show the English page. Твой перевод в Crowdin заменяет машинный, как только он появляется.

## Руководство по переводу

- **Не переводите** технические термины, такие как "LoRa", "MQTT", "BLE", "TAK", "SNR" или "RSSI" — они универсальны.
- **Сохраняйте заполнители без изменений.** Строки, такие как `%1$s` или `%d`, заполняются во время выполнения. Не удаляйте и не меняйте их порядок, если только грамматика вашего языка не требует этого.
- **Согласуйте тон.** Приложение использует дружелюбный, прямой стиль общения. Избегайте чрезмерно официального языка.
- **Проверь, если возможно.** Поменяй язык на телефоне и открой приложение, чтобы увидеть, как выглядят переводы в контексте.

## Вопросы?

Если у тебя есть вопросы о контексте конкретной строки или нужна помощь для начала работы, создайте обсуждение на странице [Meshtastic GitHub Discussions](https://github.com/orgs/meshtastic/discussions).

Спасибо, что помогаешь расширять охват Meshtastic.

## Связанные темы

- [Единицы и локаль](units-and-locale) — как приложение выбирает форматы чисел, дат и единиц для твоего региона
- [Справка и документация](help-and-docs) — встроенный браузер документации, куда публикуются эти страницы
- [Onboarding](onboarding) — это момент, когда новый пользователь впервые сталкивается с переведёнными строками
