# Meshtastic Release Process

This guide summarizes the steps for releasing new versions of Meshtastic Android and Desktop. The process is fully automated via GitHub Actions and Fastlane.

## Overview

The entire release process is managed by a single, manually-triggered GitHub Action: **`Create or Promote Release`**.

-   **Trigger:** To start a new release or promote an existing one, a developer manually runs the workflow from the GitHub Actions tab.
-   **Inputs:** The workflow requires the following inputs:
    1.  `version`: The base version number you are releasing (e.g., `2.4.0`).
    2.  `channel`: The release channel you are targeting (`internal`, `closed`, `open`, or `production`).
    3.  `build_desktop`: Whether to build and attach Desktop native installers (default: `false`).
-   **Automation:** The workflow handles everything automatically:
    -   **Syncs Assets:** Fetches the latest firmware/hardware lists, protobuf definitions, and translations (Crowdin).
    -   **Generates Changelog:** Creates a clean changelog from commits since the last production release and commits it to the repo.
    -   **Updates Config:** Automatically bumps the `VERSION_NAME_BASE` in `config.properties`.
    -   **Verifies & Tags:** Runs lint checks, builds the app, and *only* tags the release if successful.
    -   **Deploys Android:** Uploads the build to the correct Google Play track and attaches artifacts (`.aab`/`.apk`) to a GitHub Release.
    -   **Deploys Desktop** *(when enabled)*: Builds native installers (DMG, MSI, EXE, DEB, RPM, AppImage) on a matrix of runners and attaches them to the GitHub Release.
-   **Changelog:** Release notes are auto-generated from PR labels. Ensure PRs are labeled correctly to maintain an accurate changelog.

## Release Steps

### 1. Start an Internal Release

1.  Navigate to the **Actions** tab in the GitHub repository.
2.  Select the **`Create or Promote Release`** workflow.
3.  Click the **"Run workflow"** dropdown.
4.  Enter the base `version` (e.g., `2.4.0`).
5.  Select the `internal` channel.
6.  Check **`build_desktop`** if you want Desktop installers included in this release.
7.  Click **"Run workflow"**.

The workflow will:
1.  **Create a new commit** on the current branch containing updated assets, translations, and the new changelog.
2.  **Tag** that commit with an incremental internal tag (e.g., `v2.4.0-internal.1`).
3.  **Build & Deploy** the verified Android artifact to the Play Store Internal track.
4.  **Build Desktop** *(if enabled)* native installers on macOS, Windows, and Linux runners.
5.  Publish a **draft** pre-release on GitHub with all artifacts attached.

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

1.  **Verify Android:** Check the Google Play Console to ensure the build is available on the correct track.
2.  **Verify Desktop** *(if built)*: Download and smoke-test at least one installer (DMG, MSI, or AppImage) from the GitHub Release.
3.  **Merge:** Merge the release branch (if one was used for stabilization) back into `main`.

## Desktop Release Details

Desktop native installers are built as part of the main release pipeline when `build_desktop` is enabled. There is no separate promotion flow for Desktop — installers are built once during the `internal` release and attached to the GitHub Release alongside Android artifacts.

### Artifacts Produced

| Platform | Format | Runner |
|---|---|---|
| macOS | `.dmg` | `macos-latest` |
| Windows | `.msi`, `.exe` | `windows-latest` |
| Linux (x86_64) | `.deb`, `.rpm`, `.AppImage` | `ubuntu-24.04` |
| Linux (ARM64) | `.deb`, `.rpm`, `.AppImage` | `ubuntu-24.04-arm` |

### macOS Code Signing & Notarization

macOS builds are signed and notarized when the following CI secrets are configured:

| Secret | Source |
|---|---|
| `APPLE_SIGNING_IDENTITY` | Developer ID Application certificate (from Apple Developer account) |
| `APPLE_ID` | Apple ID email used for notarization |
| `APPLE_APP_SPECIFIC_PASSWORD` | App-specific password from [appleid.apple.com](https://appleid.apple.com) |
| `APPLE_TEAM_ID` | 10-character Apple Developer Team ID |

Without these secrets, macOS builds are produced unsigned. Unsigned DMGs will trigger Gatekeeper warnings on end-user machines.

### Version Alignment

Desktop uses the same version resolution chain as Android — both read `VERSION_CODE_OFFSET` and `VERSION_NAME_BASE` from `config.properties`, with CI passing the resolved values as environment variables. Version names are sanitized to strict `X.Y.Z` format for native installer compatibility.

### Flatpak

Flatpak packaging is maintained externally at [vidplace7/org.meshtastic.desktop](https://github.com/vidplace7/org.meshtastic.desktop). It builds `:desktop:packageUberJarForCurrentOS` (not the native distribution pipeline) and includes its own AppStream metainfo, `.desktop` entry, and JBR bundling.

## Build Attestations & Provenance

All release artifacts are accompanied by explicit GitHub build attestations (provenance). This provides cryptographic proof that the artifacts were built by our trusted GitHub Actions workflow, ensuring supply chain integrity.

-   You can view and verify provenance in the GitHub UI under each release asset.
-   For more details, see [GitHub's documentation on build provenance](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions#provenance-attestations).
