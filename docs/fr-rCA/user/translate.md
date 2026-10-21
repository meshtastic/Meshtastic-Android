---
title: Translate the App
nav_order: 17
last_updated: 2026-10-21
description: How the app and its documentation are translated via Crowdin, and guidelines for contributing translations.
aliases:
  - translate
  - crowdin
  - localization
---

# Translate the App

The app and its in-app docs are translated on Crowdin — this page shows how to contribute. The app uses [Crowdin](https://crowdin.com/) to manage community translations for both the user interface and in-app documentation.

## Ce qui est traduit

| Ressource                       | Emplacement de la source                                                   | Notes                                                                                  |
| ------------------------------- | -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| Chaînes UI                      | `core/resources/src/commonMain/composeResources/values/strings.xml`        | Boutons, étiquettes, messages et tout texte visible par l'utilisateur                  |
| Setting names                   | `core/resources/src/commonMain/composeResources/values/schema_strings.xml` | Names and descriptions of the node's settings, generated from the Meshtastic protobufs |
| Pages du Guide de l'utilisateur | `docs/en/user/*.md`                                                        | Documentation dans l'application affichée dans Aide et Documentation                   |
| Métadonnées Fastlane            | `fastlane/metadata/android/fr-FR/`                                         | Google Play listing title, description, and changelogs                                 |

> ℹ️ **Note:** Developer Guide pages are English-only. Code-focused documentation targeting contributors is not translated.

## Comment contribuer

1. **Visitez le projet Crowdin.** Ouvrez le [projet Meshtastic Android Crowdin](https://crowdin.com/project/meshtastic-android) et connectez-vous ou créez un compte gratuit.
2. **Choisissez votre langue.** Sélectionnez une langue existante ou demandez-en une nouvelle en ouvrant un [problème GitHub](https://github.com/meshtastic/Meshtastic-Android/issues/new).
3. **Traductions** Crowdin montre la source anglaise à gauche et votre traduction à droite. Traduisez chaque portion et sauvegardez.
4. **Vérifier le contexte.** De nombreuses portions incluent des captures d'écran ou des commentaires de contexte — cochez celles-ci pour comprendre où le texte apparaît dans l'application. A scheduled job pulls translations from Crowdin and opens a pull request; they ship once a maintainer merges it and a new build goes out.

> 💡 **Tip:** Keep translations short. UI strings often appear in buttons, chips, or narrow columns. Si une traduction est considérablement plus longue que l'original anglais, pensez à abréger en conservant la signification claire.

## Ajouter une nouvelle langue

If your language is not listed on Crowdin:

1. Ouvrir un ticket sur [GitHub](https://github.com/meshtastic/Meshtastic-Android/issues/new) demandant la nouvelle langue.
2. A maintainer adds the language to the Crowdin project.
3. Once added, you can begin translating. The app offers every language that has a strings folder, so the first sync that brings yours in adds it to the app.

## Comment sont organisées les traductions

L'application Android utilise des **Ressources Multiplateforme** pour tous les textes visibles par l'utilisateur:

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

La documentation dans l'application suit un modèle similaire dans `docs/`:

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

## Directives de Traduction

- **Ne traduisez pas** des termes techniques tels que "LoRa", "MQTT", "BLE", "TAK", "SNR", ou "RSSI" — ce sont des termes universels.
- **Garder les espaces réservés intact.** Les chaînes comme `%1$s` ou `%d` sont remplies au moment de l'exécution. Ne les supprimez ni ne les réordonnez pas à moins que la grammaire de votre langue ne l'exige.
- **Respectez le ton** L'application utilise un ton amical Évitez des formulations trop formelles
- **Test if possible.** Switch your phone's language and open the app to see how translations look in context.

## Des questions ?

If you have questions about a specific string's context or need help getting started, open a discussion on the [Meshtastic GitHub Discussions](https://github.com/orgs/meshtastic/discussions) page.

Thank you for helping expand the reach of Meshtastic.

## Related Topics

- [Units & Locale](units-and-locale) — how the app picks number, date, and unit formats for your region
- [Help & Documentation](help-and-docs) — the in-app docs browser these pages are published to
- [Onboarding](onboarding) — where a new user first meets the translated strings
