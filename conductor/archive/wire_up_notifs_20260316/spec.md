# Specification: Wire Up Notifications

## Goal
To implement a unified, cross-platform notification system that abstracts platform-specific implementations (Android local push, Desktop TrayState) into a common API for the Kotlin Multiplatform (KMP) core. This will enable consistent notification dispatching for key mesh events.

## Requirements
1. **Abstraction Layer:** Create a shared `NotificationManager` interface in `commonMain` to handle notification dispatching across all targets.
2. **Platform Implementations:**
   - **Android:** Implement native local notifications following the existing Android app behavior and Material Design guidance.
   - **Desktop:** Implement system notifications using the `TrayState` API.
3. **Trigger Events:** Replicate the existing Android notification triggers (e.g., new messages, connections) and adapt them to use the new shared abstraction.
4. **User Preferences:** Provide a unified UI for users to opt in or out of specific notification categories, respecting their choices globally.
5. **Foreground Handling & Behavior:** Defer to platform-specific UX guidelines and the established Android implementation for aspects like sound, vibration, and in-app display (e.g., suppressing system notifications if the conversation is active).

## Out of Scope
- Changes to the underlying networking or Bluetooth layers.
- Remote Push Notifications (FCM/APNs) – this is strictly for local, mesh-driven events.