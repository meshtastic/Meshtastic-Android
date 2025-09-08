# Meshtastic-Android Release Process

zzThis document outlines the steps for releasing a new version of the Meshtastic-Android application. Adhering to this process ensures consistency and helps manage the release lifecycle, leveraging automation via the `release.yml` GitHub Action.

**Note on Automation:** The `release.yml` GitHub Action is primarily triggered by **pushing a Git tag** matching the pattern `v*` (e.g., `v1.2.3`, `v1.2.3-open.1`). It can also be manually triggered via `workflow_dispatch` from the GitHub Actions UI (select the desired branch/tag/commit).

The workflow automatically:
*   Determines version information from the tag.
*   Builds F-Droid (APK) and Google (AAB, APK) artifacts. If artifacts for the same commit SHA have been built before, it will use the cached artifacts instead of rebuilding.
*   Generates a changelog.
*   Creates a **draft GitHub Release**. The release is marked as a "pre-release" if the tag name contains `-internal`, `-closed`, or `-open`.
*   Attaches build artifacts, `version_info.txt`, and `changelog.txt` to the draft GitHub Release.
*   Attests build provenance for the artifacts.
*   Uploads the AAB to the Google Play Console as a **draft** to a track determined by the tag name:
    *   `internal` (for tags with `-internal`)
    *   `NewAlpha` (for tags with `-closed`)
    *   `beta` (for tags with `-open`)
    *   `production` (for tags without these suffixes)

Finalizing and publishing the GitHub Release and the Google Play Store submission are **manual steps**.

## Prerequisites

Before initiating the release process, ensure the following are completed:

1.  **Main Branch Stability:** The `main` branch (or your chosen release branch) must be stable, with all features and bug fixes intended for the release merged and thoroughly tested.
2.  **Automated Testing:** All automated tests (unit, integration, UI) must be passing on the release candidate code, and CI checks on pull requests must be green.
3.  **Versioning and Tagging Strategy:**
    *   The primary source for the release version name in the CI workflow is the Git tag (e.g., `v1.2.3` results in version name `1.2.3`).
    *   Tags **must** start with `v` and generally follow Semantic Versioning (e.g., `vX.X.X`).
    *   For pre-releases, use suffixes that the workflow recognizes to set the GitHub pre-release flag and Play Store track:
        *   **Internal/QA:** `vX.X.X-internal.Y` (e.g., `v1.2.3-internal.1`)
        *   **Closed Alpha:** `vX.X.X-closed.Y` (e.g., `v1.2.3-closed.1`)
        *   **Open Alpha/Beta:** `vX.X.X-open.Y` (e.g., `v1.2.3-open.1`)
    *   **Production releases** use no suffix (e.g., `vX.X.X`). The `Y` in suffixes is an increment for iterations of the same pre-release type.
    *   **Recommendation:** Before tagging, update `VERSION_NAME_BASE` in `buildSrc/src/main/kotlin/Configs.kt` to match the `X.X.X` part of your tag. This ensures consistency if the app uses this value internally. The CI workflow derives `APP_VERSION_NAME` directly from the tag and passes it to Gradle.
4.  **Final Checks:** Perform thorough manual testing of critical user flows and new features on various devices and Android versions.

## Core Release Workflow: Triggering via Tag Push

The primary way to initiate a release is by creating and pushing a tag:

1.  **Ensure Local Branch is Synced:**
    ```bash
    # Example: if releasing from main
    git checkout main
    git pull origin main 
    ```
2.  **Create and Push Tag:**
    Tag the commit you intend to release (e.g., the head of `main` or a release branch).
    ```bash
    # Example for an open beta release
    git tag v1.2.3-open.1
    git push origin v1.2.3-open.1
    ```
    Or, for a production release:
    ```bash
    git tag v1.2.3
    git push origin v1.2.3
    ```

Pushing the tag automatically triggers the `release.yml` GitHub Action, which performs the automated steps listed in the "Note on Automation" section at the beginning of this document.

## Managing Different Release Phases (Manual Steps Post-Workflow)

After the `release.yml` workflow completes, manual actions are needed on GitHub and in the Google Play Console.

### Phase 1: Internal / QA Release
*   **Tag format:** `vX.X.X-internal.Y` (e.g., `v1.2.3-internal.1`)
*   **Branching (Optional):**
    *   Consider creating a `release/x.x.x` branch from `main`.
    *   Update `Configs.kt` on this branch.
    *   Create a draft PR from `release/x.x.x` to `main`. Tag a commit on this branch.
*   **Manual Steps Post-Workflow:**
    1.  **GitHub:**
        *   Navigate to the "Releases" page of the repository.
        *   Find the **draft release** (e.g., "Release v1.2.3-internal.1"). It will be marked as "pre-release".
        *   Verify the attached artifacts.
        *   You can choose to publish this pre-release if you want it formally listed, or simply use the artifacts from the draft for internal distribution.
    2.  **Google Play Console:**
        *   The AAB will be uploaded as a **draft** to the **`qa` track**.
        *   Review the draft release in the Play Console and promote/submit it as needed for your internal testers.

### Phase 2: Closed Alpha Release
*   **Tag format:** `vX.X.X-closed.Y` (e.g., `v1.2.3-closed.1`)
*   **Manual Steps Post-Workflow:**
    1.  **GitHub:**
        *   Find the **draft release**. It will be marked as "pre-release".
        *   Verify artifacts. Consider publishing it as a pre-release for wider internal visibility if appropriate.
    2.  **Google Play Console:**
        *   The AAB will be a **draft** on the **`newalpha` track**.
        *   Review and submit it for your closed alpha testers.

### Phase 3: Open Alpha / Beta Release
*   **Tag format:** `vX.X.X-open.Y` (e.g., `v1.2.3-open.1`)
*   **Manual Steps Post-Workflow:**
    1.  **GitHub:**
        *   Find the **draft release**. It will be marked as "pre-release".
        *   Edit the release: Review the title (e.g., "Release v1.2.3-open.1") and the auto-generated changelog.
        *   Ensure "This is a pre-release" is checked.
        *   **Publish the release** on GitHub. This makes it visible to the public.
    2.  **Google Play Console:**
        *   The AAB will be a **draft** on the **`beta` track**.
        *   Review, add release notes (can copy from GitHub changelog), and submit it for your open testers.

### Phase 4: Production Release
*   **Tag format:** `vX.X.X` (e.g., `v1.2.3`)
*   **Branching:**
    *   Ensure all changes for the release are merged into `main`.
    *   Tag the final merge commit on `main`.
*   **Manual Steps Post-Workflow:**
    1.  **GitHub:**
        *   Find the **draft release** (e.g., "Release v1.2.3").
        *   Edit the release: Review title and changelog.
        *   **Crucially, uncheck "This is a pre-release"**.
        *   **Publish the release** on GitHub.
    2.  **Google Play Console:**
        *   The AAB will be a **draft** on the **`production` track**.
        *   Review, add release notes.
        *   **Start a staged rollout** or release to 100% of users.

## Iterating on Pre-Releases

If bugs are found in an internal, closed, or open alpha/beta:
1.  Commit fixes to your development branch (e.g., `release/x.x.x` or `main`).
2.  Create a new, incremented tag (e.g., if `v1.2.3-open.1` had bugs, use `v1.2.3-open.2`).
3.  Push the new tag.
4.  Follow the manual post-workflow steps for that release phase again.

## Post-Release Activities

1.  **Monitoring:** Closely monitor app performance, crash reports, and user feedback.
2.  **Communication:** Announce the new release to the user community as appropriate.
3.  **Hotfixes (for Production Releases):**
    *   If a critical bug is found in a production release:
        1.  Create a hotfix branch (e.g., `hotfix/x.x.y`) from `main` (or directly from the production tag `vX.X.X`).
        2.  Implement and test the fix.
        3.  Update `VERSION_NAME_BASE` in `buildSrc/src/main/kotlin/Configs.kt` for the patch version (e.g., `1.2.4`).
        4.  Merge the hotfix branch into `main`.
        5.  Tag the merge commit on `main` with the new patch version (e.g., `v1.2.4`).
        6.  Push the new tag (e.g., `git push origin v1.2.4`). This triggers the `release.yml` workflow.
        7.  Follow the **Manual Steps Post-Workflow** for a **Production Release** (uncheck "pre-release" on GitHub, manage production track draft in Play Console).

---
