# Meshtastic-Android Release Process

This document outlines the steps for releasing a new version of the Meshtastic-Android application. Adhering to this process ensures consistency and helps manage the release lifecycle from initial testing to production deployment, leveraging automation via the `release.yml` GitHub Action for builds and initial GitHub Release drafting.

**Note:** The `release.yml` GitHub Action is manually triggered. It builds artifacts and, if its `create_github_release` parameter is true (default), creates a Git tag and a **draft pre-release** on GitHub. Finalizing and publishing the GitHub Release, as well as uploads to the Google Play Console, are manual steps.

## Prerequisites

Before initiating the release process, ensure the following are completed:

1.  **Main Branch Stability:** The `main` branch must be stable, with all features and bug fixes intended for the release merged and thoroughly tested.
2.  **Automated Testing:** All automated tests (unit, integration, UI) must be passing on the release candidate code, CI must be passing. No explicit tests will be run on the `release.yml` Github Action.
3.  **Version Update:**
    *   Update `VERSION_NAME_BASE` in the `buildSrc/src/main/kotlin/Configs.kt` file according to Semantic Versioning (e.g., `X.X.X`). This value will be used for the Git tag (e.g., `vX.X.X`) and the GitHub Release name. This update should be done on the release branch.
4.  **Final Checks:** Perform thorough manual testing of critical user flows and new features on various devices and Android versions.

## Release Steps

The release process is divided into distinct phases. The `release.yml` GitHub Action is triggered manually for each phase that requires a build and GitHub Release object.

### Phase 1: Closed Alpha Release (Closed Testing)

This phase involves creating a release candidate and deploying it to a limited audience for initial feedback.

1.  **Create Release Branch:**
    *   From the latest commit of the `main` branch, create a new branch named `release/x.x.x` (e.g., `release/1.2.3`).
        ```bash
        # Ensure you are on the latest main branch
        git checkout main
        git pull origin main
        # Create the release branch
        git checkout -b release/x.x.x
        ```
    *   Update `VERSION_NAME_BASE` in `buildSrc/src/main/kotlin/Configs.kt` on this branch (e.g., to `1.2.3`).
    *   Commit and push the release branch:
        ```bash
        git add buildSrc/src/main/kotlin/Configs.kt
        git commit -m "Set version for release 1.2.3"
        git push origin release/x.x.x
        ```

2.  **Draft Pull Request (PR):**
    *   On GitHub, create a **draft** Pull Request from the `release/x.x.x` branch targeting the `main` branch.
    *   Set the PR title to `Release x.x.x` (e.g., `Release 1.2.3`).
    *   In the PR description, summarize the key changes. This can serve as a basis for the release notes.

3.  **Initiate Closed Alpha Build and Draft Release via `release.yml`:**
    *   Manually trigger the `release.yml` GitHub Action (e.g., through the GitHub Actions UI).
        *   Set the `branch` input to your `release/x.x.x` branch.
        *   Set the `create_github_release` input to `false`. This prevents the action from creating a GitHub Release at this stage to keep visibility low during Closed Alpha testing.
    *   The `release.yml` GitHub Action will:
        *   Build signed release APK (F-Droid) and Android App Bundle/APK (Google) from the `release/x.x.x` branch.\

4.  **Manual Closed Alpha Deployment to Google Play:**
    *   Download the AAB from the assets created during the previous step.
    *   Manually upload this AAB to a **Closed Test Channel** in the Google Play Console.

5.  **Monitor Feedback:** Actively collect feedback from closed testers. Address any critical bugs by committing fixes to the `release/x.x.x` branch and pushing them.
    *   For re-deployment: Manually re-trigger the `release.yml` GitHub Action as in step 3. Note: This will attempt to create the same tag. If the tag already exists, the GitHub Release creation might behave unexpectedly or require manual intervention (e.g., deleting the old tag and release before re-running, or the action might update the existing draft release if `actions/create-release@v1` is configured to do so – verify its behavior with existing tags). Then, manually upload the new AAB to the Closed Test Channel. Consider if `VERSION_NAME_BASE` needs a patch increment for subsequent Closed Alpha builds if distinct tags are desired.

**Note on Promoting Builds:** If the Closed Alpha testing phase was successful and no code changes (and thus no re-builds) were required, the AAB uploaded to the Closed Test Channel during step 4 can often be directly promoted to the Open Test Channel (Open Alpha) in the Google Play Console. If changes were made, proceed with a new build for Open Alpha as described below.

### Phase 2: Open Alpha Release (Open Testing)

Once the Closed Alpha release is stable and initial feedback is addressed, the release is promoted to a wider audience.

1.  **Promote Pull Request:**
    *   Change the status of the `Release x.x.x` PR from "Draft" to **"Ready for Review"**.
    *   Incorporate any feedback from code reviews into the `release/x.x.x` branch and push the changes.

2.  **Initiate Open Alpha Build and Draft Release via `release.yml`:**
    *   Ensure the `release/x.x.x` branch contains all fixes and `VERSION_NAME_BASE` in `Configs.kt` reflects the intended version for this Open Alpha.
    *   Manually trigger the `release.yml` GitHub Action as in Closed Alpha Phase (Step 3), ensuring `branch` points to `release/x.x.x`.
    *   The `release.yml` GitHub Action will perform the same steps: build, tag (e.g., `vX.X.X`), and create/update a **draft pre-release** on GitHub titled "Meshtastic Android X.X.X (versionCode) alpha".

3.  **Finalize GitHub Release (Manual):**
    *   Navigate to the **draft pre-release** created by the GitHub Action.
    *   **Edit the release:**
        *   Change the title to reflect a production release (e.g., "Meshtastic Android X.X.X (versionCode) beta").
        *   **Check "This is a pre-release"**.
        *   (Re)generate release notes from the previous beta tag, the notes will be generated based on the template: `.github/release.yml`.
        *   **Publish the release** (making it no longer a draft). This makes the alpha release and artifacts visible to the public and available for download via GitHub or third parties like Fdroid and Obtainium.

4.  **Manual Open Alpha Deployment to Google Play:**
    *   Download the AAB from the GitHub draft pre-release.
    *   Manually upload this AAB to the **Open Test Channel (Alpha)** in the Google Play Console.

5.  **Monitor Feedback:** Continue to monitor feedback. Address critical issues by committing fixes to `release/x.x.x`. For re-deployment, repeat step 2 and 3 of this Open Alpha phase.

**Note on Promoting Builds:** If the Open Alpha testing phase was successful and no code changes (and thus no re-builds) were required beyond those already incorporated for this Open Alpha build, the AAB uploaded to the Open Test Channel (Alpha) during step 3 can often be directly promoted to the Production Channel in the Google Play Console. If changes were made, ensure all final changes are on the `main` branch before proceeding with the Production build as described below.

### Phase 3: Production Release

After successful Open Alpha testing and when the release candidate is considered stable.

1.  **Final Review and Merge Pull Request:**
    *   Conduct a final review of the `Release x.x.x` PR.
    *   Ensure `VERSION_NAME_BASE` in `Configs.kt` on the `release/x.x.x` branch is the correct final version (e.g., `1.2.3`).
    *   Once approved, **merge** the `release/x.x.x` PR into the `main` branch.

2.  **Initiate Production Build and Draft GitHub Release via `release.yml`:**
    *   Manually trigger the `release.yml` GitHub Action:
        *   Set the `branch` input to `main`.
        *   Ensure `create_github_release` input is `true`.
    *   The `release.yml` GitHub Action will:
        *   Build artifacts from the latest commit on the `main` branch (which now includes the merged release changes and correct `VERSION_NAME_BASE`).
        *   Create the final Git tag (e.g., `v1.2.3`) on the merge commit on `main`.
        *   Create/Update a **draft pre-release** on GitHub titled "Meshtastic Android X.X.X (versionCode) alpha" associated with this tag.
        *   Attach the F-Droid/universal APK and Android App Bundle (AAB) to this draft pre-release. The AAB is suitable for Google Play Console uploads.


3.  **Finalize GitHub Release (Manual):**
    *   Navigate to the **draft pre-release** created by the GitHub Action.
    *   **Edit the release:**
        *   Change the title to reflect a production release (e.g., "Meshtastic Android X.X.X (versionCode) beta").
        *   **Uncheck "This is a pre-release"**.
        *   (Re)generate release notes from the previous beta tag, the notes will be generated based on the template: `.github/release.yml`.
        *   **Publish the release** (making it no longer a draft). This makes the beta(production) release visible to the public and available for download via GitHub or third parties like Fdroid and Obtainium.

4.  **Manual Production Deployment to Google Play:**
    *   Download the AAB from the now-finalized GitHub Release assets.
    *   Manually upload this AAB to the **Production Channel** in the Google Play Console, ideally using a **staged rollout**.

## Post-Release Activities

1.  **Monitoring:** Closely monitor app performance (e.g., using Datadog), crash reports (e.g., using Firebase Crashlytics), and user feedback.
2.  **Communication:** Announce the new release to the user community.
3.  **Hotfixes:** If critical bugs are discovered:
    *   Create a hotfix branch (e.g., `hotfix/x.x.y`) from the production tag on `main` or directly from `main`.
    *   Implement and test the fix. Update `VERSION_NAME_BASE` in `buildSrc/src/main/kotlin/Configs.kt` for the patch version (e.g., `1.2.4`).
    *   Merge the hotfix into `main` (and cherry-pick to any active release branches if necessary).
    *   Manually trigger the `release.yml` GitHub Action, setting the `branch` to `main` (or your hotfix branch if building before merge).
    *   The action will build, create a new tag (e.g., `v1.2.4`), and create a new **draft pre-release** ("...alpha").
    *   Manually finalize this GitHub release as in "Phase 3: Production Release - Step 3" (update title, uncheck pre-release/draft, add notes, publish).
    *   Manually upload the generated AAB to the necessary Play Console tracks.
4.  **Retrospective (Optional):** Conduct a team retrospective.

---
