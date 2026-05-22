# Firebase Crashlytics iOS Setup Guide

Important references:

- Refer to the `firebase-basics` skills, particularly those for iOS setup, before proceeding.
- Refer to the `xcode-project-setup` skills.

## Project and App Setup

Use the `firebase-tools` CLI to set up the project if necessary.

1.  **Find Bundle ID:** Read the Xcode project to find the iOS bundle ID. Check the `PRODUCT_BUNDLE_IDENTIFIER` value in the `.pbxproj` file or the `Info.plist` file.
2.  **Create Firebase Project:** If no project exists, create one:
    `npx -y firebase-tools@latest projects:create <project-id> --display-name="My Awesome App"`
3.  **Create Firebase App:** Register the iOS app with the discovered bundle ID:
    `npx -y firebase-tools@latest apps:create IOS <bundle-id>`
4.  **Link the GoogleService-Info.plist file:** Use the script in the `xcode-project-setup` skill to obtain the config and link.

## Add Swift Package Dependencies

Install the Crashlytics SDK using the Swift package manager, or the script in the `xcode-project-setup` skill.

Install the `FirebaseCrashlytics` package from the `https://github.com/firebase/firebase-ios-sdk.git` repository.

## Initialize Firebase in App Code

Modify the application's entry point to initialize Firebase. Refer to the iOS setup reference in the `firebase-basics` skill.

## Add dSYM Upload Script

Add a Run Script phase to the main app target in Xcode. This step is required to upload dSYM files for crash symbolication. 

1.  **Debug Information Format**: The `Debug Information Format` in Build Settings must be set to `DWARF with dSYM File`.
2.  **Run Script Content**: A new "Run Script Phase" should be added to the target's "Build Phases" with the following content:
    ```bash
    ${BUILD_DIR%/Build/*}/SourcePackages/checkouts/firebase-ios-sdk/Crashlytics/run
    ```

When using the `xcode-project-setup` skills, the above two steps will be done as part of adding the `FirebaseCrashlytics` package. Once the skill has been invoked and succeeded, verify that the app's project.pbxproj file contains a Run Script Build phase where the shell script attribute value contains 'Crashlytics'. Specifically, there should be a `PBXShellScriptBuildPhase` section with the attribute `shellScript` that is set to a value that contains `Crashlytics/run` and an attribute `inputPaths` where one of the values contains `GoogleService-Info.plist`. If verification is not successful, present the above two options to be done manually.

## Follow up Steps

### Required: Force a Test Crash

1. Add code to trigger a crash a few seconds after app startup to verify Crashlytics setup.

**For SwiftUI Apps (in `AppDelegate.swift`):**

    *File: `AppDelegate.swift`*
    ```swift
    import FirebaseCore
    import Dispatch // For DispatchQueue

    // ...

    class AppDelegate: NSObject, UIApplicationDelegate {
      func application(_ application: UIApplication,
                       didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil) -> Bool {
        FirebaseApp.configure()
        // Force a crash after a delay to test Crashlytics
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
            fatalError("Test Crash")
        }
        return true
      }
    }
    ```

2.  Run your app on a device or simulator. If running in the iOS simulator, make sure that the Xcode debugger is disconnected, otherwise the crash will not make it to Crashlytics. The app should crash after a short delay.

3.  Restart the app. The Crashlytics SDK will send the crash report to Firebase on the next app launch.

4.  After a few minutes, the crash should be available in the Firebase console. Go to **DevOps & Engagement** > **Crashlytics** to view your dashboard and crash reports.
  -  If the Firebase MCP server is installed, use the `get_report` tool to check that a crash was received.
  -  As a fallback, visit the Crashlytics dashboard in the Firebase console to see the new crash report.

5. After verifying that Firebase has received the crash report - either using the `get_report` tool or manually viewing it in the Firebase console - remove the code from step 1 that triggers the crash.  This prevents the application from always crashing on start up after a delay.

### Optional: Add custom debugging information

Customize reports to help you better understand what's happening in your app and the circumstances around events reported to Crashlytics. See [Customize Crash Reports for Apple Platforms](https://firebase.google.com/docs/crashlytics/ios/customize-crash-reports.md).
