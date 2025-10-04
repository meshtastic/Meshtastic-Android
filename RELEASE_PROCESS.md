# Meshtastic-Android Release Process

This guide summarizes the steps for releasing a new version of Meshtastic-Android. The process is fully automated via GitHub Actions and Fastlane.

## Overview

The entire release process is managed by a single, manually-triggered GitHub Action: **`Create or Promote Release`**.

-   **Trigger:** To start a new release or promote an existing one, a developer manually runs the workflow from the GitHub Actions tab.
-   **Inputs:** The workflow requires two inputs:
    1.  `version`: The base version number you are releasing (e.g., `2.4.0`).
    2.  `channel`: The release channel you are targeting (`internal`, `closed`, `open`, or `production`).
-   **Automation:** The workflow handles everything automatically:
    -   Calculates the correct Git tag based on the channel (e.g., `v2.4.0-internal.1` or `v2.4.0`).
    -   Pushes the new tag to the repository.
    -   Calls a reusable workflow that builds the app, deploys it to the correct Google Play track, and attaches the artifacts (`.aab`/`.apk`) to a GitHub Release.
-   **Changelog:** Release notes are auto-generated from PR labels. Ensure PRs are labeled correctly to maintain an accurate changelog.

## Release Steps

### 1. Start an Internal Release

1.  Navigate to the **Actions** tab in the GitHub repository.
2.  Select the **`Create or Promote Release`** workflow.
3.  Click the **"Run workflow"** dropdown.
4.  Enter the base `version` (e.g., `2.4.0`).
5.  Select the `internal` channel.
6.  Click **"Run workflow"**.

The workflow will create an incremental internal tag (e.g., `v2.4.0-internal.1`) and publish a **draft** pre-release on GitHub.

### 2. Promote to the Next Channel

Once an internal build has been verified, you can promote it to a wider audience.

1.  Run the **`Create or Promote Release`** workflow again with the same base `version`.
2.  Select the next channel in the sequence (e.g., `closed`, then `open`).
3.  The workflow will create a new incremental tag for that channel (e.g., `v2.4.0-closed.1`) and create a **published** pre-release on GitHub.

### 3. Promote to Production

After testing is complete on all pre-release channels, you can create the final public release.

1.  Run the **`Create or Promote Release`** workflow one last time.
2.  Use the same base `version`.
3.  Select the `production` channel.
4.  The workflow will create a clean version tag (e.g., `v2.4.0`) and create a **published, stable** (non-prerelease) release on GitHub.

### 4. Post-Release

1.  **Verify:** Check the Google Play Console to ensure the build is available on the correct track.
2.  **Merge:** Merge the release branch (if one was used for stabilization) back into `main`.

## Build Attestations & Provenance

All release artifacts are accompanied by explicit GitHub build attestations (provenance). This provides cryptographic proof that the artifacts were built by our trusted GitHub Actions workflow, ensuring supply chain integrity.

-   You can view and verify provenance in the GitHub UI under each release asset.
-   For more details, see [GitHub's documentation on build provenance](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions#provenance-attestations).
