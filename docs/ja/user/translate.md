---
title: アプリを翻訳する
nav_order: 17
last_updated: 2026-10-21
description: アプリとそのドキュメントが Crowdin を通じてどう翻訳されるか、および翻訳に貢献するためのガイドラインを説明します。
aliases:
  - translate
  - crowdin
  - localization
---

# アプリを翻訳する

The app and its in-app docs are translated on Crowdin — this page shows how to contribute. アプリは、ユーザーインターフェースとアプリ内ドキュメントの両方のコミュニティ翻訳を管理するために [Crowdin](https://crowdin.com/) を使用しています。

## 翻訳される対象

| リソース           | ソースの場所                                                                     | 備考                                                                                     |
| -------------- | -------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| UI 文字列         | `core/resources/src/commonMain/composeResources/values/strings.xml`        | ボタン、ラベル、メッセージ、およびユーザーに表示されるすべてのテキスト                                                    |
| Setting names  | `core/resources/src/commonMain/composeResources/values/schema_strings.xml` | Names and descriptions of the node's settings, generated from the Meshtastic protobufs |
| ユーザーガイドのページ    | `docs/en/user/*.md`                                                        | 「ヘルプとドキュメント」に表示されるアプリ内ドキュメント                                                           |
| Fastlane メタデータ | `fastlane/metadata/android/en-US/`                                         | Google Play listing title, description, and changelogs                                 |

> ℹ️ **Note:** Developer Guide pages are English-only. コントリビューター向けの、コード中心のドキュメントは翻訳されません。

## 貢献する方法

1. **Crowdin プロジェクトにアクセスします。** [Meshtastic Android の Crowdin プロジェクト](https://crowdin.com/project/meshtastic-android) を開き、サインインするか、無料アカウントを作成します。
2. **言語を選びます。** 既存の言語を選択するか、[GitHub の issue](https://github.com/meshtastic/Meshtastic-Android/issues/new) を作成して新しい言語をリクエストします。
3. **文字列を翻訳します。** Crowdin では、左側に英語の原文、右側に自分の翻訳が表示されます。各文字列を翻訳して保存します。
4. **コンテキストを確認します。** 多くの文字列には、スクリーンショットやコンテキストのコメントが含まれています。これらを確認して、テキストがアプリのどこに表示されるかを把握してください。 A scheduled job pulls translations from Crowdin and opens a pull request; they ship once a maintainer merges it and a new build goes out.

> 💡 **ヒント：** 翻訳は短くしてください。 UI 文字列は、ボタン、チップ、狭い列に表示されることがよくあります。翻訳が英語の原文よりも大幅に長くなる場合は、意味が明確なままになる範囲で短縮することを検討してください。

## 新しい言語を追加する

If your language is not listed on Crowdin:

1. [GitHub](https://github.com/meshtastic/Meshtastic-Android/issues/new) で issue を作成し、新しいロケールをリクエストします。
2. A maintainer adds the language to the Crowdin project.
3. Once added, you can begin translating. The app offers every language that has a strings folder, so the first sync that brings yours in adds it to the app.

## 翻訳の構成

Android アプリは、ユーザーに表示されるすべての文字列に **Compose Multiplatform リソース**を使用しています：

```text
core/resources/src/commonMain/composeResources/
├── values/              ← 英語（デフォルト）
│   └── strings.xml
├── values-de/           ← ドイツ語
│   └── strings.xml
├── values-fr/           ← フランス語
│   └── strings.xml
└── ...
```

アプリ内ドキュメントも、`docs/` の下で同様のパターンに従います：

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

## 翻訳ガイドライン

- 「LoRa」「MQTT」「BLE」「TAK」「SNR」「RSSI」などの技術用語は**翻訳しないでください**。これらは共通です。
- **プレースホルダーはそのまま保ちます。** `%1$s` や `%d` のような文字列は、実行時に値が埋め込まれます。自分の言語の文法上必要な場合を除き、削除したり並び替えたりしないでください。
- **トーンを合わせます。** アプリは、親しみやすく率直な語り口を使っています。過度に堅い言葉は避けてください。
- **Test if possible.** Switch your phone's language and open the app to see how translations look in context.

## 質問がありますか？

特定の文字列のコンテキストについて質問がある場合や、始め方について助けが必要な場合は、[Meshtastic GitHub Discussions](https://github.com/orgs/meshtastic/discussions) のページでディスカッションを作成してください。

Thank you for helping expand the reach of Meshtastic.

## 関連トピック

- [Units & Locale](units-and-locale) — how the app picks number, date, and unit formats for your region
- [Help & Documentation](help-and-docs) — the in-app docs browser these pages are published to
- [Onboarding](onboarding) — where a new user first meets the translated strings
