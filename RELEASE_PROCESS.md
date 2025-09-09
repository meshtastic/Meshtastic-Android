# Meshtastic-Android Release Process

This document outlines the steps for releasing a new version of the Meshtastic-Android application. Adhering to this process ensures consistency and helps manage the release lifecycle, leveraging automation via the `release.yml` GitHub Action.

**Note on Automation:** The `release.yml` GitHub Action is primarily triggered by **pushing a Git tag** matching the pattern `v*` (e.g., `v1.2.3`, `v1.2.3-open.1`). It can also be manually triggered via `workflow_dispatch` from the GitHub Actions UI.

The workflow uses a simple and robust **"upload-only"** model. It automatically:
*   Determines a `versionName` from the Git tag.
*   Generates a unique, always-increasing `versionCode` based on the number of minutes since the Unix epoch. This prevents `versionCode` conflicts and will not overflow until the year 6052.
*   Builds fresh F-Droid (APK) and Google (AAB, APK) artifacts for every run.
*   Creates a **draft GitHub Release** and attaches the artifacts.
*   Attests build provenance for the artifacts.
*   **Uploads** the newly built AAB directly to the appropriate track in the Google Play Console based on the tag.

There is no promotion of builds between tracks; every release is a new, independent upload. Finalizing and publishing the GitHub Release and the Google Play Store submission remain **manual steps**.

## Prerequisites

Before initiating the release process, ensure the following are completed:

1.  **Main Branch Stability:** The `main` branch (or your chosen release branch) must be stable, with all features and bug fixes intended for the release merged and thoroughly tested.
2.  **Automated Testing:** All automated tests must be passing.
3.  **Versioning and Tagging Strategy:**
    *   Tags **must** start with `v` and follow Semantic Versioning (e.g., `vX.X.X`).
    *   Use the correct suffixes for the desired release track:
        *   **Internal/QA:** `vX.X.X-internal.Y`
        *   **Closed Alpha:** `vX.X.X-closed.Y`
        *   **Open Alpha/Beta:** `vX.X.X-open.Y`
        *   **Production:** `vX.X.X` (no suffix)
    *   **Recommendation:** Before tagging, update `VERSION_NAME_BASE` in `buildSrc/src/main/kotlin/Configs.kt` to match the `X.X.X` part of your tag. This ensures consistency for local development builds.

## Core Release Workflow: Triggering via Tag Push

1.  **Create and push a tag for the desired release track.**
    ```bash
    # This build will be uploaded and rolled out on the 'internal' track
    git tag v1.2.3-internal.1
    git push origin v1.2.3-internal.1
    ```
2.  **Wait for the workflow to complete.**
3.  **Verify the build** in the Google Play Console and with testers.
4.  When ready to advance to the next track, create and push a new tag.
    ```bash
    # This will create and upload a NEW build to the 'NewAlpha' (closed alpha) track
    git tag v1.2.3-closed.1
    git push origin v1.2.3-closed.1
    ```

## Iterating on a Bad Build

If you discover a critical bug in a build, the process is simple:

1.  **Fix the Code:** Merge the necessary bug fixes into your main branch.
2.  **Create a New Iteration Tag:** Create a new tag for the same release phase, simply incrementing the final number.
    ```bash
    # If v1.2.3-internal.1 was bad, the new build is v1.2.3-internal.2
    git tag v1.2.3-internal.2
    git push origin v1.2.3-internal.2
    ```
3.  **A New Build is Uploaded:** The workflow will run, generate a new epoch-minute-based `versionCode`, and upload a fresh build to the `internal` track. There is no risk of a `versionCode` collision.

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
*   **Automated Action:** A new AAB is built and **uploaded** as a **draft** to the `NewAlpha` track.
*   **Manual Steps:**
    1.  **GitHub:** Find and publish the **draft release**.
    2.  **Google Play Console:** Manually review the draft release and submit it for your closed alpha testers.

### Phase 3: Open Alpha / Beta Release
*   **Tag format:** `vX.X.X-open.Y`
*   **Automated Action:** A new AAB is built and **uploaded** as a **draft** to the `beta` track.
*   **Manual Steps:**
    1.  **GitHub:** Find and publish the **draft pre-release**.
    2.  **Google Play Console:** Manually review the draft, add release notes, and submit it.

### Phase 4: Production Release
*   **Tag format:** `vX.X.X`
*   **Automated Action:** A new AAB is built and **uploaded** to the `production` track. By default, it is configured for a 10% staged rollout.
*   **Manual Steps:**
    1.  **GitHub:** Find the **draft release**. **Crucially, uncheck "This is a pre-release"** before publishing.
    2.  **Google Play Console:** Manually review the release, add release notes, and **start the staged rollout**.
