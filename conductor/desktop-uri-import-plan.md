# Desktop URI Import Plan

## Objective
Wire up `SharedContact` and `ChannelSet` import logic for the Desktop target. This enables the Desktop app to process deep links or URIs passed on startup via arguments or intercepted by the OS using `java.awt.Desktop`'s `OpenURIHandler`. 

## Key Files & Context
- `desktop/src/main/kotlin/org/meshtastic/desktop/Main.kt`: Desktop app entry point. Must be updated to parse command line arguments and handle OS-level URI opening events.
- `desktop/src/main/kotlin/org/meshtastic/desktop/ui/DesktopMainScreen.kt`: The main UI composition. Must be updated to inject the shared `UIViewModel` and render the `SharedContactDialog` / `ScannedQrCodeDialog` when `requestChannelSet` or `sharedContactRequested` are present.
- `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/viewmodel/UIViewModel.kt`: Already handles URI dispatch and holds the requests, so no changes are needed here.

## Implementation Steps

1. **Update `DesktopMainScreen.kt`**
    - Import `org.meshtastic.core.ui.viewmodel.UIViewModel`, `org.koin.compose.viewmodel.koinViewModel`, `org.meshtastic.core.ui.share.SharedContactDialog`, `org.meshtastic.core.ui.qr.ScannedQrCodeDialog`, and `org.meshtastic.core.model.ConnectionState`.
    - Inject `UIViewModel` directly into `DesktopMainScreen` via `val uiViewModel = koinViewModel<UIViewModel>()`.
    - Add observations for `uiViewModel.sharedContactRequested` and `uiViewModel.requestChannelSet`.
    - Just like in Android's `MainScreen`, conditionally render `SharedContactDialog` and `ScannedQrCodeDialog` if `connectionState == ConnectionState.Connected` and either state contains a valid request.
    - Wire `onDismiss` closures to `uiViewModel.clearSharedContactRequested()` and `uiViewModel.clearRequestChannelUrl()`.

2. **Update `Main.kt` (Desktop)**
    - Alter `fun main()` to `fun main(args: Array<String>)`.
    - Resolve `UIViewModel` after `koinApp` initialization: `val uiViewModel = koinApp.koin.get<UIViewModel>()`.
    - Process the initial `args` and invoke `uiViewModel.handleScannedUri` using `MeshtasticUri` for any arguments that look like valid Meshtastic URIs (starting with `http` or `meshtastic://`).
    - Attempt to attach a `java.awt.desktop.OpenURIHandler` if `java.awt.Desktop.Action.APP_OPEN_URI` is supported. When triggered, process the incoming `event.uri` string using the same `handleScannedUri` logic.

## Verification & Testing
1. Compile the desktop target with `./gradlew desktop:run --args="meshtastic://meshtastic/v/contact..."`.
2. Connect to a device via Desktop Connections or wait for connection.
3. Validate that the corresponding Shared Contact or Channel Set dialog renders on screen.
4. Verify that dismissing the dialogs properly clears the state in the view model.
5. (Optional, macOS) If testing via packaged DMG, verify that opening a `.webloc` or invoking `open meshtastic://...` triggers the `APP_OPEN_URI` handler and routes through the UI.