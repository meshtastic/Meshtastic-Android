# Firebase Remote Config iOS Setup Guide

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

Install the Remote Config and Analytics SDKs using the Swift package manager.

Install the `FirebaseRemoteConfig` and `FirebaseAnalytics` packages from the [https://github.com/firebase/firebase-ios-sdk.git](https://github.com/firebase/firebase-ios-sdk.git) repository.

## Initialize Firebase in App Code

Modify the application's entry point to initialize Firebase. Refer to the iOS setup reference in the firebase-basics skill.

## Follow up Steps

The following steps cover the essential patterns for using Remote Config effectively in your iOS app.

### Set In-App Defaults
Define default values so your app behaves as intended before it connects to the backend. Create a property list file (e.g., RemoteConfigDefaults.plist):

    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
    <plist version="1.0">
    <dict>
        <key>welcome_message</key>
        <string>Welcome to the app!</string>
        <key>is_feature_enabled</key>
        <false/>
    </dict>
    </plist>
    ```

Then, initialize the SDK and set the defaults:

    ```swift
    import FirebaseRemoteConfig

    let remoteConfig = RemoteConfig.remoteConfig()
    remoteConfig.setDefaults(fromPlist: "RemoteConfigDefaults")
    ```
### Fetch and Activate Values
To retrieve values from the cloud and apply them to your app:

    ```swift
    remoteConfig.fetchAndActivate { (status, error) in
        if status == .successFetchedFromRemote || status == .successUsingPreFetchedData {
            print("Config fetched and activated!")
        } else {
            print("Config not fetched")
        }
        
        // Access a value
        let message = remoteConfig.configValue(forKey: "welcome_message").stringValue
    }
    ```
