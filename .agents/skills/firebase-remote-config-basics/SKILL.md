---
compatibility: This skill is best used with the Firebase CLI, but does not require it. Firebase CLI can be accessed through `npx -y firebase-tools@latest`.
description: Comprehensive guide for Firebase Remote Config, including template management and SDK usage. Use this skill when the user needs help setting up Remote Config, managing feature flags, or updating app behavior dynamically.
metadata:
    github-path: skills/firebase-remote-config-basics
    github-ref: refs/heads/main
    github-repo: https://github.com/firebase/agent-skills
    github-tree-sha: b02d177bdd624800cf75da75390248dcf9ab2c78
name: firebase-remote-config-basics
---
# Remote Config

This skill provides a complete guide for getting started with Remote Config on Android or iOS. Remote Config allows you to change the behavior and appearance of your app without publishing an app update by maintaining a cloud-based configuration template.

## Prerequisites

Provisioning Remote Config requires both a Firebase project and a Firebase app, either Android or iOS. To manage the Remote Config template and conditions via the command line, use the Firebase CLI. See the `firebase-basics` skill for references on project initialization.

## Troubleshooting Execution

### Handling npx 403 Forbidden Errors
If `npx -y firebase-tools@latest` fails due to registry permissions (403 error):
1. **Inform the user**: "I am unable to fetch the latest Firebase tools via npx due to a registry error."
2. **Fallback**: Attempt to use the local `firebase` command directly if the user confirms it is installed globally (`npm install -g firebase-tools`).

### Handling Project Context Issues
If a command fails because "no active project is selected":
1. **Check login**: Run `npx -y firebase-tools@latest login:list`.
2. **Prompt for ID**: If logged in but no project is active, ask the user: "Please provide your Firebase Project ID to proceed."
3. **Use Flag**: Append `--project <PROJECT_ID>` to every subsequent command.


## SDK Setup

To learn how to set up Remote Config in your application code, choose your platform:

*   **Android**: [android_setup.md](references/android_setup.md)
*   **iOS**: [ios_setup.md](references/ios_setup.md)

## Best Practices and Template Management

Follow these guidelines and use the associated CLI tools to ensure efficient and safe use of Remote Config.

### Fetching Strategies
To optimize app performance and user experience, follow these recommended patterns (see [Loading Strategies](https://firebase.google.com/docs/remote-config/loading)):
* **Load new values for next startup**: The most effective pattern is to activate previously fetched values immediately on startup and fetch new values in the background to be used next time. This minimizes user wait time.
* **Real-time Updates**: Use the SDK's real-time listener to update the app instantly without a refresh when server-side configuration changes.

### Template Management via CLI
Use the following commands to manage your Remote Config template and version history through the terminal:

### Template Management via CLI
Use the following commands to manage your Remote Config template and version history through the terminal:

* **Get current template**: Save the remote template to a local JSON file for auditing or modification.
  ```bash
  npx -y firebase-tools@latest remoteconfig:get -o remote_config.json
  ```
* **Autonomous Editing & Discovery** : Modify the local `remote_config.json` directly. Determine the correct signal (e.g., device.country or percent) and update the "conditions" array and "parameters" map accordingly.

* **MANDATORY: User Review and Verification** : STOP and ask the user to verify your changes before proceeding to deployment.
    * Action: Inform the user: "I have prepared the changes in remote_config.json. Please review the file for accuracy. Once you are satisfied, tell me to 'deploy' to make the changes live."
* **Deployment Orchestration** : To push changes, you must ensure the environment is configured for deployment.
   * Config Mapping: If a firebase.json file is missing, create one to map the local JSON to the Remote Config service:
    ```json
      { "remoteconfig": { "template": "remote_config.json" } }
    ```
  * Deploy: Execute the partial deployment command
    ```bash
    npx -y firebase-tools@latest deploy --only remoteconfig
    ```
* **Verification**: After deployment, verify the update by listing the version history.
    ```bash
    npx -y firebase-tools@latest remoteconfig:versions:list
    ```

The SDK provides a number of features to make your application dynamic and responsive to user segments.

* **Set In-App Defaults**: Define baseline values to ensure the app functions offline or before the first fetch.
* **Fetch and Activate**: Retrieve values from the Firebase backend and apply them to the local UI/Logic.
* **Template Management**: Use the Firebase CLI to version-control, get, and deploy your config JSON files.
