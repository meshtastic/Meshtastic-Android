---
title: ヘルプとアプリ内ドキュメント
parent: User Guide
nav_order: 21
last_updated: 2026-08-29
description: このドキュメントをアプリ内で閲覧・検索し、オンデバイスの AI アシスタント Chirpy に Meshtastic について質問できます。
aliases:
  - help
  - docs-browser
  - chirpy
  - assistant
---

# ヘルプとアプリ内ドキュメント

このユーザードキュメントは**アプリ内**にも同梱されており、Meshtastic を離れることなくオフラインで読めます。 「**設定 → ヘルプとドキュメント**」から開きます。

## 閲覧する

ドキュメントブラウザーには、ユーザーガイドのすべてのページが一覧表示されます。 ページをタップすると読めます。画像や相互リンクも、ここと同じように機能します。

![アプリ内ドキュメントブラウザーの目次](../../assets/screenshots/docs-browser_toc.png)

### 検索

検索アイコンをタップして入力すると、タイトルとキーワードでページを絞り込めます。結果は入力に応じて更新されます。

![アプリ内ドキュメントの検索](../../assets/screenshots/docs-browser_search.png)

ブラウザーで開いたページ：

![アプリ内で表示されたドキュメントページ](../../assets/screenshots/docs-browser_page.png)

## Chirpy：AI アシスタント

**Chirpy** は、この同梱ドキュメントを情報源として、Meshtastic に関する平易な質問に答えます。 ドキュメントブラウザーで Chirpy ボタンをタップして質問を入力すると、回答と、関連ページへのリンクが返ってきます。

![ページリンク付きで質問に答える Chirpy AI アシスタント](../../assets/screenshots/docs-browser_chirpy.png)

Chirpy is Google-flavor Android only. On F-Droid, desktop and iOS builds the assistant button does not appear at all — the same is true on a phone whose hardware cannot run the on-device model. Browsing and the docs browser's own search work normally on every platform.

> 🔒 **Privacy:** On supported phones running the Google-flavor build, Chirpy runs **on-device** using Gemini Nano — your questions never leave your phone. 初回使用時に、小さなモデルがダウンロードされます。

## 関連トピック

- [アプリを翻訳する](translate)：これらのページが他の言語にどう翻訳されるか
- [アプリ機能](app-functions)：Chirpy とは別の、システム AI 連携
