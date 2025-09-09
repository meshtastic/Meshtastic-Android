# Meshtastic-Android Release Process

This document outlines the steps for releasing a new version of the Meshtastic-Android application. The process is heavily automated using GitHub Actions and Fastlane, triggered by pushing a Git tag from a `release/*` branch.

## High-Level Overview

The automation is designed to be safe, repeatable, and efficient. When a new tag matching the `v*` pattern is pushed from a release branch, the `release.yml` GitHub Action workflow will:

1.  **Determine Versioning:** A `versionName` is derived from the Git tag, and a unique, always-increasing `versionCode` is generated from the current timestamp.
2.  **Build in Parallel:** Two jobs run simultaneously to build the `google` and `fdroid` flavors of the app.
3.  **Deploy to Google Play:** The `google` build job uses Fastlane to automatically upload the Android App Bundle (AAB) to the correct track on the Google Play Console, based on the tag name.
4.  **Create a Draft GitHub Release:** After both builds are complete, a final job gathers the artifacts (AAB, Google APK, and F-Droid APK) and creates a single, consolidated **draft** release on GitHub.

Finalizing and publishing the release on both GitHub and the Google Play Console are the only **manual steps**.

## Versioning and Tagging Strategy

The entire process is driven by your Git tagging strategy. Tags **must** start with `v` and should follow Semantic Versioning. Use the correct suffix for the desired release track:

*   **Internal Track:** `vX.X.X-internal.Y` (e.g., `v2.3.5-internal.1`)
*   **Closed Track:** `vX.X.X-closed.Y` (e.g., `v2.3.5-closed.1`)
*   **Open Track:** `vX.X.X-open.Y` (e.g., `v2.3.5-open.1`)
*   **Production Track:** `vX.X.X` (e.g., `v2.3.5`)

The `.Y` suffix is for iterations. If you find a bug in `v2.3.5-closed.1`, you would fix it on the release branch and tag the new commit as `v2.3.5-closed.2`.

## Core Release Workflow

The entire release process happens on a dedicated release branch, allowing `main` to remain open for new feature development.

### 1. Creating the Release Branch
First, create a `release/X.X.X` branch from a stable `main`. This branch is now "feature frozen." Only critical bug fixes should be added.

As a housekeeping step, it's recommended to update the `VERSION_NAME_BASE` in `buildSrc/src/main/kotlin/Configs.kt` on this new branch. While the final release version is set by the Git tag in CI, this ensures local development builds have a sensible version name.

```bash
git checkout main
git pull origin main
git checkout -b release/2.3.5
# (Now, update the version in buildSrc, commit the change, and then push)
git push origin release/2.3.5
```

### 2. Testing and Iterating on a Track
Start by deploying to the `internal` track to begin testing.

**A. Create and Push a Tag:**
Tag a commit on the release branch to trigger the automation.
```bash
# Ensure you are on the release branch
git checkout release/2.3.5

# Tag for the "Internal" track
git tag v2.3.5-internal.1
git push origin v2.3.5-internal.1
```

**B. Monitor and Verify:**
Monitor the workflow in GitHub Actions. Once complete, verify the build in the Google Play Console and with your internal testers.

**C. Apply Fixes (If Necessary):**
If a bug is found, commit the fix to the release branch. Remember to also cherry-pick or merge this fix back to `main`. Then, create an iterated tag.
```bash
# Assuming you've committed a fix
git tag v2.3.5-internal.2
git push origin v2.3.5-internal.2
```
This will upload a new, fixed build to the same `internal` track. Repeat this process until the build is stable.

### 3. Promoting to the Next Track
Once you are confident that a build is stable, you can "promote" it to a wider audience by tagging the **exact same commit** for the next track.

```bash
# The commit tagged as v2.3.5-internal.2 is stable and ready for the "Closed" track
git tag v2.3.5-closed.1
git push origin v2.3.5-closed.1
```
This triggers the workflow again, but this time it will send the build to the `NewAlpha` track for your closed testers. You can then continue the cycle of testing, fixing, and promoting all the way to production.

### 4. Merging Back to `main`
After the final production release is complete and verified, merge the release branch back into `main` to ensure any hotfixes are included. Then, delete the release branch.
```bash
git checkout main
git pull origin main
git merge release/2.3.5
git push origin main
git branch -d release/2.3.5
git push origin --delete release/2.3.5
```

## Manual Finalization Steps

### For Internal Releases

*   **Automated Action:** The AAB is uploaded to the `internal` track and is **rolled out to 100% of testers automatically**.
*   **Your Manual Step:**
    1.  **Verify the build** in the Google Play Console and with your internal testers.

### For Closed Releases

*   **Automated Action:** The AAB is uploaded to the `NewAlpha` track and is **rolled out to 100% of testers automatically**.
*   **Your Manual Step:**
    1.  **Verify the build** in the Google Play Console and with your closed track testers.

### For Open Releases

*   **Automated Action:** The AAB is uploaded to the `beta` track and begins a **staged rollout to 25% of your open track testers automatically**.
*   **Your Manual Steps:**
    1.  **Verify the build** in the Google Play Console.
    2.  **(Optional)** Go to the GitHub "Releases" page, find the **draft release**, and publish it so your open track testers can see the official release notes.

### For Production Releases

*   **Automated Action:**
    *   The AAB is uploaded to the `production` track in a **draft** state. It is **not** rolled out to any users.
    *   A corresponding **draft** release is created on GitHub with all build artifacts.
*   **Your Manual Steps:**
    1.  **Publish on GitHub First:** Go to the GitHub "Releases" page and find the draft. Review the release notes and artifacts, then **Publish release**. This makes the release notes publicly visible.
    2.  **Promote on Google Play Second:** *After* publishing on GitHub, go to your Google Play Console. Find the draft release, review it, and then proceed to **start the rollout to production**.

## Monitoring the Release

After a release has been rolled out to users (especially for Open and Production), it is crucial to monitor its performance.

*   **Google Play Console:** Keep a close eye on the **Vitals** section for your app. Pay special attention to the crash rate and ANR (Application Not Responding) rate. A sudden spike in these numbers is a strong indicator of a problem.
*   **Datadog:** Check your Datadog dashboards for any unusual trends or new errors that may have been introduced with the release.
*   **Crashlytics:** Review crash reports in Firebase Crashlytics to identify any new issues that users may be experiencing.
*   **User Reviews:** Monitor user reviews on the Google Play Store for any negative feedback or reports of bugs.
*   **Community Feedback:** Monitor Discord, GitHub Issues, and community forums for feedback from users who have received the update.

If you identify a critical issue, be prepared to halt the rollout in the Google Play Console and tag a new, fixed version.
