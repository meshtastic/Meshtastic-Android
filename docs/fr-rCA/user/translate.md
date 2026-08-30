---
title: Translate the App
parent: User Guide
nav_order: 17
last_updated: 2026-08-29
description: How the app and its documentation are translated via Crowdin, and guidelines for contributing translations.
aliases:
  - translate
  - crowdin
  - localization
---

# Translate the App

The app and its in-app docs are translated on Crowdin — this page shows how to contribute. The app uses [Crowdin](https://crowdin.com/) to manage community translations for both the user interface and in-app documentation.

## Ce qui est traduit

| Ressource                       | Emplacement de la source                                            | Notes                                                                         |
| ------------------------------- | ------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| Chaînes UI                      | `core/resources/src/commonMain/composeResources/values/strings.xml` | Boutons, étiquettes, messages et tout texte visible par l'utilisateur         |
| Pages du Guide de l'utilisateur | `docs/en/user/*.md`                                                 | Documentation dans l'application affichée dans Aide et Documentation          |
| Métadonnées Fastlane            | `fastlane/metadata/android/fr-FR/`                                  | Titre de la liste de l'App Store, description et historique des modifications |

> ℹ️ **Note:** Developer Guide pages are English-only. Code-focused documentation targeting contributors is not translated.

## Comment contribuer

1. **Visitez le projet Crowdin.** Ouvrez le [projet Meshtastic Android Crowdin](https://crowdin.com/project/meshtastic-android) et connectez-vous ou créez un compte gratuit.
2. **Choisissez votre langue.** Sélectionnez une langue existante ou demandez-en une nouvelle en ouvrant un [problème GitHub](https://github.com/meshtastic/Meshtastic-Android/issues/new).
3. **Traductions** Crowdin montre la source anglaise à gauche et votre traduction à droite. Traduisez chaque portion et sauvegardez.
4. **Vérifier le contexte.** De nombreuses portions incluent des captures d'écran ou des commentaires de contexte — cochez celles-ci pour comprendre où le texte apparaît dans l'application. Approved translations are automatically merged into the next release.

> 💡 **Tip:** Keep translations short. UI strings often appear in buttons, chips, or narrow columns. Si une traduction est considérablement plus longue que l'original anglais, pensez à abréger en conservant la signification claire.

## Ajouter une nouvelle langue

Si votre langue n'est pas encore listée sur Crowdin :

1. Ouvrir un ticket sur [GitHub](https://github.com/meshtastic/Meshtastic-Android/issues/new) demandant la nouvelle langue.
2. Un responsable ajoutera la langue à Crowdin et configurera `crowdin.yml`.
3. Une fois ajouté, vous pouvez commencer à traduire immédiatement.

## Comment sont organisées les traductions

L'application Android utilise des **Ressources Multiplateforme** pour tous les textes visibles par l'utilisateur:

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

La documentation dans l'application suit un modèle similaire dans `docs/`:

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

The app automatically selects the correct locale based on your phone's **Language & Region** settings.

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
