# Meshtastic-Android Release Process

This document outlines the steps for releasing a new version of the Meshtastic-Android application. Adhering to this process ensures consistency and helps manage the release lifecycle, leveraging automation via the `release.yml` GitHub Action.

**Note on Automation:** The `release.yml` GitHub Action is primarily triggered by **pushing a Git tag** matching the pattern `v*` (e.g., `v1.2.3`, `v1.2.3-open.1`). It can also be manually triggered via `workflow_dispatch` from the GitHub Actions UI.

The workflow automatically:
*   Determines version information from the tag.
*   Builds F-Droid (APK) and Google (AAB, APK) artifacts.
*   Generates a changelog.
*   Creates a **draft GitHub Release** and attaches the artifacts.
*   Attests build provenance for the artifacts.
*   Deploys to the Google Play Console using a smart **"promote-or-upload"** strategy based on the Git tag:

    *   **Internal Release (`vX.X.X-internal.Y`):**
        *   Always **uploads** the AAB to the `internal` track. The release is automatically finalized and rolled out to internal testers.

    *   **Promotions (`-closed`, `-open`, production):**
        *   The workflow first attempts to **promote** an existing build from the previous track. The promotion path is: `internal` -> `NewAlpha` (closed) -> `beta` (open) -> `production`.
        *   **Fallback Safety Net:** If a promotion fails (e.g., the corresponding build doesn't exist on the source track), the workflow **will not fail**. Instead, it automatically falls back to **uploading** the newly built AAB directly to the target track as a **draft**.

This fallback mechanism makes the process resilient but adds a crucial manual checkpoint: **any release created via fallback upload will be a draft and requires you to manually review and roll it out in the Google Play Console.**

Finalizing and publishing the GitHub Release and the Google Play Store submission remain **manual steps**.

## Prerequisites

Before initiating the release process, ensure the following are completed:

1.  **Main Branch Stability:** The `main` branch (or your chosen release branch) must be stable, with all features and bug fixes intended for the release merged and thoroughly tested.
2.  **Automated Testing:** All automated tests must be passing.
3.  **Versioning and Tagging Strategy:**
    *   Tags **must** start with `v` and generally follow Semantic Versioning (e.g., `vX.X.X`).
    *   Use the correct suffixes for the desired release phase:
        *   **Internal/QA:** `vX.X.X-internal.Y`
        *   **Closed Alpha:** `vX.X.X-closed.Y`
        *   **Open Alpha/Beta:** `vX.X.X-open.Y`
        *   **Production:** `vX.X.X` (no suffix)
    *   **Recommendation:** Before tagging, update `VERSION_NAME_BASE` in `buildSrc/src/main/kotlin/Configs.kt` to match the `X.X.X` part of your tag. This ensures consistency for local development builds.

## Core Release Workflow: Triggering via Tag Push

The recommended release process follows the promotion chain.

1.  **Start with an Internal Release:** Create and push an `-internal` tag first.
    ```bash
    # This build will be uploaded and rolled out on the 'internal' track
    git tag v1.2.3-internal.1
    git push origin v1.2.3-internal.1
    ```
2.  **Promote to the Next Phase:** Once the internal build is verified, create and push a tag for the next phase.
    ```bash
    # This will promote the v1.2.3 build from 'internal' to 'NewAlpha'
    git tag v1.2.3-closed.1
    git push origin v1.2.3-closed.1
    ```

Pushing each tag automatically triggers the `release.yml` GitHub Action.

## Managing Different Release Phases (Manual Steps Post-Workflow)

After the `release.yml` workflow completes, manual actions are needed on GitHub and in the Google Play Console.

### Phase 1: Internal / QA Release
*   **Tag format:** `vX.X.X-internal.Y`
*   **Automated Action:** The AAB is **uploaded** to the `internal` track and rolled out automatically.
*   **Manual Steps:**
    1.  **GitHub:** Find the **draft release**, verify artifacts, and publish it if desired.
    2.  **Google Play Console:** Verify the release has been successfully rolled out to internal testers.

### Phase 2: Closed Alpha Release
*   **Tag format:** `vX.X.X-closed.Y`
*   **Automated Action:** The workflow attempts to **promote** the build from `internal` to the `NewAlpha` track.
*   **Manual Steps:**
    1.  **GitHub:** Find and publish the **draft release**.
    2.  **Google Play Console:**
        *   **If promotion succeeded:** The release will be live on the `NewAlpha` track. Verify its status.
        *   **If promotion failed (fallback):** The AAB will be a **draft** on the `NewAlpha` track. You must manually review and submit it for your closed alpha testers.

### Phase 3: Open Alpha / Beta Release
*   **Tag format:** `vX.X.X-open.Y`
*   **Automated Action:** The workflow attempts to **promote** the build from `alpha` to the `beta` track.
*   **Manual Steps:**
    1.  **GitHub:** Find and publish the **draft pre-release**.
    2.  **Google Play Console:**
        *   **If promotion succeeded:** The release will be live on the `beta` track. Verify its status.
        *   **If promotion failed (fallback):** The AAB will be a **draft** on the `beta` track. You must manually review, add release notes, and submit it.

### Phase 4: Production Release
*   **Tag format:** `vX.X.X`
*   **Automated Action:** The workflow attempts to **promote** the build from `beta` to the `production` track.
*   **Manual Steps:**
    1.  **GitHub:** Find the **draft release**. **Crucially, uncheck "This is a pre-release"** before publishing.
    2.  **Google Play Console:**
        *   **If promotion succeeded:** The release will be live on the `production` track.
        *   **If promotion failed (fallback):** The AAB will be a **draft** on the `production` track. You must manually review, add release notes, and **start a staged rollout**.

## Iterating on Pre-Releases

If bugs are found in a release:
1.  Commit fixes to your development branch.
2.  Create a new, incremented tag for the **same release phase** (e.g., if `v1.2.3-open.1` had bugs, create `v1.2.3-open.2`).
3.  Push the new tag. This will trigger a new upload to the `internal` track (if it's an internal tag) or a new promotion/fallback for other tracks.
4.  Follow the manual post-workflow steps for that release phase again.
