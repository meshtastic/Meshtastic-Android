# Found a problem with our Android app?

The `google` build sends crash reports when analytics and crash reporting are allowed in the app's settings, but that covers only crashes and the errors the app logs, and the `fdroid` build sends none. For anything else, a log of the app while it does the wrong thing is the most useful thing to attach to a GitHub [issue](https://github.com/meshtastic/Meshtastic-Android/issues).

## From inside the app

Open **Settings → Advanced → Debug Panel**, reproduce the problem, then export the **App logs** tab with its download icon and attach the file to your issue. No PC or `adb` is needed. [Debug Logs](docs/en/user/debug-logs.md) covers the panel, and what an export contains, in full.

## With adb

When the app cannot reach the Debug Panel, for example because it crashes on launch, capture its logcat from a PC instead.

Set up your phone and PC for debugging:

* Install `adb` (Android Debug Bridge) from the official [SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) package.
* [Enable developer options](https://developer.android.com/studio/debug/dev-options) on your phone. The procedure differs slightly between phones; search for "enable developer mode" with your phone's name if needed.
* Connect your phone to a USB port on your PC. A dialog on the phone asks whether to allow USB debugging from this PC. Allow it, and tick the checkbox to always allow this PC.
* Run `adb devices` at your computer's shell prompt. Your phone should be listed.

Then capture the log:

* Long-press the Meshtastic app icon, open **App info**, and choose **Force stop**, so the log starts from scratch and is easier to read.
* Start the app again. The command that follows finds the app by its process, so it must be running first.
* On macOS or Linux, run:

```shell
adb shell 'logcat --pid=$(pidof -s com.geeksville.mesh)' | tee newlogfile.txt
```

* On Windows, run this in PowerShell:

```powershell
adb shell 'logcat --pid=$(pidof -s com.geeksville.mesh)' | Tee-Object newlogfile.txt
```

The package name above is for release builds. For a debug or snapshot build, use `com.geeksville.mesh.google.debug` or `com.geeksville.mesh.fdroid.debug` instead. For a problem that happens while the app starts, clear the log with `adb logcat -c`, run `adb logcat | tee newlogfile.txt` without the process filter, then start the app.

Go through the app to the part that misbehaves, then press Ctrl+C in the terminal to stop logging. Open a GitHub [issue](https://github.com/meshtastic/Meshtastic-Android/issues) describing the problem and attach `newlogfile.txt`. We'll reply with what we find, possibly with some extra questions.
