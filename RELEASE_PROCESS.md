# Release Process

This document outlines the process for creating and promoting releases for the Meshtastic Android application. The system is designed to be robust, auditable, and highly automated, using a combination of user-facing GitHub Actions "wizards" and a central, tag-triggered "engine".

## Philosophy

-   **Git Tag Driven**: The entire release lifecycle is initiated and controlled by pushing version tags to the repository.
-   **Automated Engine**: A central workflow (`release.yml`) acts as the engine, listening for new version tags. It handles all the heavy lifting: building, versioning, uploading to Google Play, and managing GitHub Releases.
-   **User-Friendly Wizards**: Manually creating tags is discouraged. Instead, two "wizard" workflows (`create-internal-release.yml` and `promote-release.yml`) provide a simple UI in the GitHub Actions tab to guide developers through creating and promoting releases safely.

## Versioning Scheme

Releases follow a semantic versioning scheme, `vX.Y.Z`, with suffixes to denote the release channel and iteration.

-   `v2.8.0-internal.1`: An internal build, iteration 1.
-   `v2.8.0-closed.1`: A closed testing (Alpha) build.
-   `v2.8.0-open.1`: An open testing (Beta) build.
-   `v2.8.0`: The final production release.

---

## The Release Lifecycle

### Step 1: Creating a New Internal Build

This is the starting point for any new release, whether it's a brand-new version, a patch, or a hotfix.

1.  Navigate to the **Actions** tab in the GitHub repository.
2.  Select the **"Create Internal Release Tag"** workflow.
3.  Click **"Run workflow"**.
4.  Fill in the `base_version` field with the version you want to create (e.g., `2.8.0`).
5.  Run the workflow.

**What Happens Automatically:**

-   The wizard calculates the next iteration number (e.g., `.1`, `.2`, etc.) and pushes a new tag to the commit (e.g., `v2.8.0-internal.1`).
-   The push triggers the `release.yml` engine, which builds the application, uploads it to the Google Play **Internal** track, and creates a corresponding **draft pre-release** on GitHub.

### Step 2: Promoting an Existing Build

Once an internal build has been tested and is ready for a wider audience, you promote it.

1.  Navigate to the **Actions** tab in the GitHub repository.
2.  Select the **"Promote Release"** workflow.
3.  Click **"Run workflow"**.
4.  Specify the `target_stage` (`closed`, `open`, or `production`). The default, `auto`, will automatically promote to the next logical stage.
5.  Optionally, specify the `base_version` to promote. If left blank, the wizard will find the latest internal tag and use its base version.
6.  Run the workflow.

**What Happens Automatically:**

-   The wizard determines the correct commit from the latest internal tag for that `base_version`.
-   It pushes a new promotion tag (e.g., `v2.8.0-closed.1`) to that commit.
-   The push triggers the `release.yml` engine. It intelligently **skips the build steps** and proceeds to:
    -   Promote the build on Google Play to the target track.
    -   Update the existing draft GitHub Release, renaming it and marking it as a non-draft pre-release (or full release for production).

### Special Case: Hotfixes / Superseding a Release

The system is designed to handle hotfixes gracefully. If `v2.8.0-internal.1` has been created, but a critical bug is found, the process is simple:

1.  Merge the fix into your main branch.
2.  Go to the **"Create Internal Release Tag"** workflow again.
3.  Enter the *same* `base_version`: `2.8.0`.

**What Happens Automatically:**

-   The wizard creates and pushes a new tag, `v2.8.0-internal.2`, to the **new commit**.
-   The `release.yml` engine detects that an existing release for `v2.8.0` points to an *older* commit.
-   It correctly interprets this as a "superseding" event. It **automatically deletes the old GitHub release and its base tag**, effectively restarting the release process for `v2.8.0` from the new, corrected commit. This prevents a broken or outdated build from ever being promoted.
