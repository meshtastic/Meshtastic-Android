---
compatibility: This skill is best used with the Firebase CLI, but does not require it. Firebase CLI can be accessed through `npx -y firebase-tools@latest`.
description: Comprehensive guide for Firebase Crashlytics, including provisioning and SDK usage. Use this skill when the user needs help setting up Crashlytics, adding crash reporting, or using the Crashlytics SDK in their application.
metadata:
    github-path: skills/firebase-crashlytics
    github-ref: refs/heads/main
    github-repo: https://github.com/firebase/agent-skills
    github-tree-sha: 155d48db65cb4bc2e44f2cbdf9ec5c4adbf8ef9c
name: firebase-crashlytics
---
# Crashlytics

This skill provides a complete guide for getting started with Crashlytics on Android or iOS. Crash data collected from client applications can be read using the MCP server in the Firebase CLI.

## Prerequisites

Provisioning Crashlytics requires both a Firebase project and a Firebase app, either Android or iOS. To read the data collected by Crashlytics, install the MCP server in the Firebase CLI. See the `firebase-basics` skill for references.

## SDK Setup

To learn how to setup Crashlytics in your application code, choose your platform:

*   **Android**: [android_setup.md](references/android_setup.md)
*   **iOS**: [ios_setup.md](references/ios_setup.md)

## SDK Usage

The SDK provides a number of features to make crash reports more actionable.

* Add custom keys
* Add custom logs
* Set user identifiers
* Report non-fatal exceptions

To learn how to customize crash reports and add additional debugging data, consult the documentation for your platform.

*   **Android**: [Customize Crash Reports for Android](https://firebase.google.com/docs/crashlytics/android/customize-crash-reports.md)
*   **iOS**: [Customize Crash Reports for Apple Platforms](https://firebase.google.com/docs/crashlytics/ios/customize-crash-reports.md)
