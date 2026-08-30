---
title: アプリを翻訳する
parent: User Guide
nav_order: 17
last_updated: 2026-08-29
description: アプリとそのドキュメントが Crowdin を通じてどう翻訳されるか、および翻訳に貢献するためのガイドラインを説明します。
aliases:
  - translate
  - crowdin
  - localization
---

# アプリを翻訳する

The app and its in-app docs are translated on Crowdin — this page shows how to contribute. アプリは、ユーザーインターフェースとアプリ内ドキュメントの両方のコミュニティ翻訳を管理するために [Crowdin](https://crowdin.com/) を使用しています。

## 翻訳される対象

| リソース           | ソースの場所                                                              | 備考                                  |
| -------------- | ------------------------------------------------------------------- | ----------------------------------- |
| UI 文字列         | `core/resources/src/commonMain/composeResources/values/strings.xml` | ボタン、ラベル、メッセージ、およびユーザーに表示されるすべてのテキスト |
| ユーザーガイドのページ    | `docs/en/user/*.md`                                                 | 「ヘルプとドキュメント」に表示されるアプリ内ドキュメント        |
| Fastlane メタデータ | `fastlane/metadata/android/en-US/`                                  | アプリストアの掲載タイトル、説明、変更履歴               |

> ℹ️ **Note:** Developer Guide pages are English-only. コントリビューター向けの、コード中心のドキュメントは翻訳されません。

## 貢献する方法

1. **Crowdin プロジェクトにアクセスします。** [Meshtastic Android の Crowdin プロジェクト](https://crowdin.com/project/meshtastic-android) を開き、サインインするか、無料アカウントを作成します。
2. **言語を選びます。** 既存の言語を選択するか、[GitHub の issue](https://github.com/meshtastic/Meshtastic-Android/issues/new) を作成して新しい言語をリクエストします。
3. **文字列を翻訳します。** Crowdin では、左側に英語の原文、右側に自分の翻訳が表示されます。 各文字列を翻訳して保存します。
4. **コンテキストを確認します。** 多くの文字列には、スクリーンショットやコンテキストのコメントが含まれています。これらを確認して、テキストがアプリのどこに表示されるかを把握してください。 Approved translations are automatically merged into the next release.

> 💡 **ヒント：** 翻訳は短くしてください。 UI 文字列は、ボタン、チップ、狭い列に表示されることがよくあります。 翻訳が英語の原文よりも大幅に長くなる場合は、意味が明確なままになる範囲で短縮することを検討してください。

## 新しい言語を追加する

自分の言語がまだ Crowdin に掲載されていない場合：

1. [GitHub](https://github.com/meshtastic/Meshtastic-Android/issues/new) で issue を作成し、新しいロケールをリクエストします。
2. メンテナーが Crowdin にその言語を追加し、`crowdin.yml` を設定します。
3. 追加されたら、すぐに翻訳を始められます。

## 翻訳の構成

Android アプリは、ユーザーに表示されるすべての文字列に **Compose Multiplatform リソース**を使用しています：

```
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

```
docs/
├── en/user/             ← 英語ソース（デフォルト）
│   ├── onboarding.md
│   └── ...
├── fr-rFR/user/         ← フランス語（フランス）
│   ├── onboarding.md
│   └── ...
├── de-rDE/user/         ← ドイツ語（ドイツ）
│   └── ...
└── ...
```

ロケールフォルダーは、Android のリソース規則 `{lang}-r{REGION}`（例：`fr-rFR`、`de-rDE`、`ja-rJP`）を使用し、アプリ文字列に使われる `values-*` ディレクトリと対応しています。

The app automatically selects the correct locale based on your phone's **Language & Region** settings.

## 翻訳ガイドライン

- 「LoRa」「MQTT」「BLE」「TAK」「SNR」「RSSI」などの技術用語は**翻訳しないでください**。これらは共通です。
- **プレースホルダーはそのまま保ちます。** `%1$s` や `%d` のような文字列は、実行時に値が埋め込まれます。 自分の言語の文法上必要な場合を除き、削除したり並び替えたりしないでください。
- **トーンを合わせます。** アプリは、親しみやすく率直な語り口を使っています。 過度に堅い言葉は避けてください。
- **Test if possible.** Switch your phone's language and open the app to see how translations look in context.

## 質問がありますか？

特定の文字列のコンテキストについて質問がある場合や、始め方について助けが必要な場合は、[Meshtastic GitHub Discussions](https://github.com/orgs/meshtastic/discussions) のページでディスカッションを作成してください。

Thank you for helping expand the reach of Meshtastic.

## 関連トピック

- [Units & Locale](units-and-locale) — how the app picks number, date, and unit formats for your region
- [Help & Documentation](help-and-docs) — the in-app docs browser these pages are published to
- [Onboarding](onboarding) — where a new user first meets the translated strings
