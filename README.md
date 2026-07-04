<p align="center">
  <img src=".github/meshtastic_logo.png" alt="Meshtastic Logo" width="200"/>
</p>
<h1 align="center">Meshtastic-Android</h1>

![GitHub all releases](https://img.shields.io/github/downloads/meshtastic/meshtastic-android/total)
[![Android CI](https://github.com/meshtastic/Meshtastic-Android/actions/workflows/pull-request.yml/badge.svg?branch=main)](https://github.com/meshtastic/Meshtastic-Android/actions/workflows/pull-request.yml)
[![codecov](https://codecov.io/gh/meshtastic/Meshtastic-Android/graph/badge.svg)](https://codecov.io/gh/meshtastic/Meshtastic-Android)
[![Crowdin](https://badges.crowdin.net/e/f440f1a5e094a5858dd86deb1adfe83d/localized.svg)](https://crowdin.meshtastic.org/android)
[![CLA assistant](https://cla-assistant.io/readme/badge/meshtastic/Meshtastic-Android)](https://cla-assistant.io/meshtastic/Meshtastic-Android)
[![Fiscal Contributors](https://opencollective.com/meshtastic/tiers/badge.svg?label=Fiscal%20Contributors&color=deeppink)](https://opencollective.com/meshtastic/)
[![Vercel](https://img.shields.io/static/v1?label=Powered%20by&message=Vercel&style=flat&logo=vercel&color=000000)](https://vercel.com?utm_source=meshtastic&utm_campaign=oss)

This is a tool for using Android (and Compose Desktop) with open-source mesh radios. For more information see our webpage: [meshtastic.org](https://www.meshtastic.org). If you are looking for the device side code, see [here](https://github.com/meshtastic/firmware).

If you have questions or feedback please [Join our discussion forum](https://github.com/orgs/meshtastic/discussions) or the [Discord Group](https://discord.gg/meshtastic). We would love to hear from you!



## Get Meshtastic

The easiest and fastest way to get the latest releases is to use our [GitHub releases](https://github.com/meshtastic/Meshtastic-Android/releases). It is recommended to use these with [Obtainium](https://github.com/ImranR98/Obtainium) to get the latest updates automatically.

Alternatively, these other providers are also available, but may be slower to update. 

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
alt="Get it on F-Droid"
width="24%">](https://f-droid.org/packages/com.geeksville.mesh/)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
alt="Get it on IzzyOnDroid"
width="24%">](https://apt.izzysoft.de/fdroid/index/apk/com.geeksville.mesh)
[<img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png"
alt="Get it on GitHub"
width="24%">](https://github.com/meshtastic/Meshtastic-Android/releases)
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
alt="Download at https://play.google.com/store/apps/details?id=com.geeksville.mesh]"
width="24%">](https://play.google.com/store/apps/details?id=com.geeksville.mesh&referrer=utm_source%3Dgithub-android-readme)

The play store is the last to update of these options, but if you want to join the Play Store testing program go to [this URL](https://play.google.com/apps/testing/com.geeksville.mesh) and opt-in to become a tester.
If you encounter any problems or have questions, [ask us on the discord](https://discord.gg/meshtastic), [create an issue](https://github.com/meshtastic/Meshtastic-Android/issues), or [post in the forum](https://github.com/orgs/meshtastic/discussions) and we'll help as we can.

### Desktop

**Meshtastic Desktop** installers (macOS DMG, Windows MSI/EXE, Linux DEB/RPM/AppImage) are available from [GitHub Releases](https://github.com/meshtastic/Meshtastic-Android/releases). A Flatpak package is maintained at [vidplace7/org.meshtastic.desktop](https://github.com/vidplace7/org.meshtastic.desktop).

## Documentation

Both sites are deployed to GitHub Pages automatically on every push to `main`.

| Site | URL | Contents |
|---|---|---|
| **User & Developer Docs** | [meshtastic.github.io/Meshtastic-Android](https://meshtastic.github.io/Meshtastic-Android/) | Jekyll site — user guide, developer guide, in-app doc content |
| **API Reference** | [meshtastic.github.io/Meshtastic-Android/api](https://meshtastic.github.io/Meshtastic-Android/api/) | Dokka-generated KDoc for all public APIs |

### Generating Locally

**User & Developer Docs (Jekyll):**
```bash
./gradlew generateDocsBundle publishDocsSite
BUNDLE_GEMFILE=docs/Gemfile bundle exec jekyll serve \
  --source build/_site --baseurl ""
```

**API Reference (Dokka):**
```bash
./gradlew dokkaGeneratePublicationHtml
# Output: build/dokka/html/index.html
```

## Architecture

### Modern Android Development (MAD)
The app follows modern Android development practices, built on top of a shared Kotlin Multiplatform (KMP) Core:
- **KMP Modules:** Business logic (`core:domain`), data sources (`core:data`, `core:database`, `core:datastore`), and communications (`core:network`, `core:ble`) are entirely platform-agnostic, targeting Android and Compose Desktop.
- **UI:** JetBrains Compose Multiplatform (Material 3) using Compose Multiplatform resources.
- **State Management:** Unidirectional Data Flow (UDF) with ViewModels, Coroutines, and Flow.
- **Dependency Injection:** Koin with Koin Annotations (K2 Compiler Plugin).
- **Navigation:** JetBrains Navigation 3 (Multiplatform routing with RESTful deep linking).
- **Data Layer:** Repository pattern with Room KMP (local DB), DataStore (prefs), and Protobuf (device comms).

### Bluetooth Low Energy (BLE)
The BLE stack uses a multiplatform interface-driven architecture. Platform-agnostic interfaces live in `commonMain`, utilizing the **Kable** multiplatform BLE library to handle device communication across all supported targets (Android, Desktop). This provides a robust, Coroutine-based architecture for reliable device communication while remaining fully KMP compatible. See [core/ble/README.md](core/ble/README.md) for details.

### Module Documentation

Each module has its own README with details on its responsibilities, API surface, and internal design.

| Module | Description |
|---|---|
| [core/api](core/api/README.md) | AIDL service API for third-party integrations |
| [core/domain](core/domain/README.md) | Business-logic use cases (radio config, sessions, exports) |
| [core/repository](core/repository/README.md) | Data & infrastructure contracts (RadioTransport, NodeRepository, ServiceRepository) |
| [core/takserver](core/takserver/README.md) | Meshtastic ↔ TAK (ATAK/iTAK) bridge — CoT server & conversion |
| [core/ble](core/ble/README.md) | Multiplatform BLE transport (Kable) |
| [core/network](core/network/README.md) | Internet comms: firmware metadata, map tiles, radio transports |
| [core/data](core/data/README.md) | Repository layer — orchestrates DB, network, and service data |
| [core/database](core/database/README.md) | Room KMP local persistence |
| [core/datastore](core/datastore/README.md) | DataStore preferences |
| [core/service](core/service/README.md) | Meshtastic Android service abstractions |
| [core/navigation](core/navigation/README.md) | Type-safe Navigation 3 route model |
| [core/resources](core/resources/README.md) | Centralised CMP string & drawable resources |
| [core/model](core/model/README.md) | Shared domain models |
| [core/ui](core/ui/README.md) | Shared UI components |
| [core/common](core/common/README.md) | Common utilities |
| [core/di](core/di/README.md) | Koin DI modules |
| [core/testing](core/testing/README.md) | Shared test fakes & utilities |
| [core/nfc](core/nfc/README.md) | NFC support |
| [core/prefs](core/prefs/README.md) | Legacy preference helpers |
| [core/barcode](core/barcode/README.md) | Barcode / QR scanning |
| [core/proto](core/proto/README.md) | Protobuf submodule wrapper |
| [feature/messaging](feature/messaging/README.md) | Messaging UI feature |
| [feature/map](feature/map/README.md) | Map UI feature |
| [feature/node](feature/node/README.md) | Node detail UI feature |
| [feature/settings](feature/settings/README.md) | Settings UI feature |
| [feature/firmware](feature/firmware/README.md) | Firmware update UI feature |
| [feature/intro](feature/intro/README.md) | Onboarding / intro UI feature |
| [feature/wifi-provision](feature/wifi-provision/README.md) | Wi-Fi provisioning UI feature |
| [feature/connections](feature/connections/README.md) | Device discovery & connection management (BLE / USB / TCP) |
| [feature/docs](feature/docs/README.md) | In-app documentation browser with Chirpy AI assistant |
| [feature/widget](feature/widget/README.md) | Android home-screen Glance widget (live mesh stats) |

## Translations

You can help translate the app into your native language using [Crowdin](https://crowdin.meshtastic.org/android).

## API & Integration

Developers can integrate with the Meshtastic Android app using our published API library via **JitPack**. This allows third-party applications (like the ATAK plugin) to communicate with the mesh service via AIDL.

For detailed integration instructions, see [core/api/README.md](core/api/README.md).

Additionally, the app includes a built-in **Local TAK Server** feature that can be enabled in settings. This runs a local TCP server on port 8089 to allow ATAK clients to connect directly and route their traffic over the mesh.

## Building the Android App
> [!WARNING]
> Debug and release builds can be installed concurrently. This is solely to enable smoother development, and you should avoid running both apps simultaneously. To ensure proper function, force quit the app not in use.

https://meshtastic.org/docs/development/android/

Note: when building the `google` flavor locally you will need to supply your own [Google Maps Android SDK api key](https://developers.google.com/maps/documentation/android-sdk/get-api-key) `MAPS_API_KEY` in `local.properties` in order to use Google Maps.
e.g.
```properties
MAPS_API_KEY=your_google_maps_api_key_here
```

## Contributing guidelines

For detailed instructions on how to contribute, please see our [CONTRIBUTING.md](CONTRIBUTING.md) file.
For details on our release process, see the [RELEASE_PROCESS.md](RELEASE_PROCESS.md) file.

## Repository Statistics

![Alt](https://repobeats.axiom.co/api/embed/1d75239069a6d671fe0b8f80b2e1bf590a98f0eb.svg "Repobeats analytics image")

Copyright 2025, Meshtastic LLC. GPL-3.0 license
