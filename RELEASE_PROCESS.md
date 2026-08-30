# Meshtastic Release Process

This guide summarizes the steps for releasing new versions of Meshtastic Android and Desktop. The core flow is automated once a developer triggers the **Create or Promote Release** workflow; Microsoft Store and winget publishing run as separate workflows (see Desktop Store Publishing).

## Overview

The entire release process is managed by a single GitHub Action: **`Create or Promote Release`**.

-   **Trigger:** To start a new release or promote an existing one, a developer runs the workflow from the GitHub Actions tab.
-   **Inputs:** The workflow requires the following inputs:
    1.  `base_version`: The base version number you are releasing (e.g., `2.8.0`).
    2.  `channel`: The release channel you are targeting (`internal`, `closed`, `open`, or `production`).
    3.  `dry_run`: If `true`, calculates the tag but does not push it or start the release (default: `false`).
    4.  `no_review_in_flight`: **Promotions only, and a hard gate.** Before promoting, check
        Play Console → Publishing overview → Submission activity; if anything reads "In
        review", wait. Tick this box to confirm — the workflow fails without it, because
        each promotion creates a new Play submission that *cancels and restarts* any review
        already in flight. Internal releases and dry runs are exempt (Play internal testing
        skips full review).
-   **Automation:** The workflow handles everything automatically:
    -   **Generates Changelog:** Categorizes merged PRs by their labels (per `.github/release.yml`) into GitHub's auto-generated release notes; a separate automation workflow opens a PR to fold the same notes into `CHANGELOG.md`.
    -   **Tags & Builds** *(internal releases)*: Pushes the incremental tag first — there is no lint/test gate in this workflow, that's the separate PR/CI pipeline — then builds the Android bundle/APK and Desktop installers from that tag; if the build fails, an automatic cleanup job deletes the tag so a retry starts clean. Promotions skip this entirely and retag the already-built artifact (see below).
    -   **Deploys Android:** Uploads the build to the correct Google Play track and attaches artifacts (`.aab`/`.apk`) to a GitHub Release.
    -   **Deploys Desktop** *(internal releases)*: Builds native installers (DMG, MSI, EXE, DEB, RPM, AppImage) and Flatpak sources on a matrix of runners and attaches them to the GitHub Release.
-   **Changelog:** Both the GitHub Release notes and `CHANGELOG.md` are generated from merged PR labels, not raw commit messages — label PRs correctly (`enhancement`, `bugfix`, etc.) to keep them accurate.
-   **Not part of this workflow:** Firmware/hardware/device-links lists and Crowdin translations are kept current by a separate hourly workflow, `scheduled-updates.yml` ("Scheduled Updates (Firmware, Hardware, Translations)"), which opens its own PR rather than committing directly — it never runs as part of a release. `VERSION_NAME_BASE` in `config.properties` is likewise never written by automation: a maintainer bumps it by hand in an ordinary PR (e.g. "chore: bump VERSION_NAME_BASE to 2.8.2 (#6820)") before starting a release for a new base version, paired with a matching `<release>` entry in `desktopApp/packaging/linux/org.meshtastic.MeshtasticDesktop.metainfo.xml` — a `pull-request.yml` check fails the PR if that entry is missing. `Create or Promote Release` only *reads* `VERSION_NAME_BASE`/`VERSION_CODE_OFFSET` from `config.properties` to compute the build's version name/code.

## Release Steps

### 1. Start an Internal Release

1.  Navigate to the **Actions** tab in the GitHub repository.
2.  Select the **`Create or Promote Release`** workflow.
3.  Click the **"Run workflow"** dropdown.
4.  Enter the `base_version` (e.g., `2.8.0`).
5.  Select the `internal` channel.
6.  Click **"Run workflow"**. (Tip: enable `dry_run` first to preview the tag that would be created without pushing anything.)

The workflow will:
1.  **Tag** the current commit on the branch with an incremental internal tag (e.g., `v2.8.0-internal.1`) — no new commit is created; it tags whatever is already at `HEAD`.
2.  **Build & Deploy** the built Android artifact to the Play Store Internal track.
3.  **Build Desktop** native installers and Flatpak sources on macOS, Windows, and Linux runners.
4.  Publish a **draft** pre-release on GitHub with all artifacts attached. It stays a draft until
    the first promotion (closed/open/production), at which point `promote.yml` un-drafts the
    *same* release object (retagging it to the new channel's tag) rather than creating a new one.

### 2. Promote to the Next Channel

Once an internal build has been verified, you can promote it to a wider audience.

1.  Run the **`Create or Promote Release`** workflow again with the same `base_version`.
2.  Select the next channel in the sequence (e.g., `closed`, then `open`).
3.  The workflow will create a new incremental tag for that channel (e.g., `v2.8.0-closed.1`) and create a **published** pre-release on GitHub.

### 3. Promote to Production

After testing is complete on all pre-release channels, you can create the final public release.

1.  Run the **`Create or Promote Release`** workflow one last time.
2.  Use the same `base_version`.
3.  Select the `production` channel.
4.  The workflow will create a clean version tag (e.g., `v2.8.0`) and create a **published, stable** (non-prerelease) release on GitHub.

### 4. Post-Release

1.  **Verify Android:** Check the Google Play Console to ensure the build is available on the correct track.
2.  **Verify Desktop:** Download and smoke-test at least one installer (DMG, MSI, or AppImage) from the GitHub Release.
3.  **Verify the desktop store submissions** *(production only — see below)*: the Microsoft Store
    submission in Partner Center, and the pull request opened against `microsoft/winget-pkgs`.
4.  **Merge:** If a `release/*` branch was used for stabilization (CI runs the same PR checks
    against PRs targeting `release/**` as it does for `main`), merge it back into `main` now
    that production has shipped.

### Desktop Store Publishing (production only)

Publishing a **production** release also fires two workflows, both keyed on the GitHub
`release: released` event:

| Workflow | Target | Credentials |
|---|---|---|
| `msstore-publish.yml` | Microsoft Store, via the Partner Center API using the MSStore CLI (#6864 replaced the deprecated `microsoft/store-submission` action) | `MSSTORE_*` secrets |
| `winget-publish.yml` | A PR against `microsoft/winget-pkgs` | `WINGET_TOKEN` PAT |

Neither fires for drafts or pre-releases, so internal/closed/open promotions are ignored.
`released` also fires when `promote.yml` flips an existing pre-release to a full release — but
because that edit uses the workflow's own `GITHUB_TOKEN`, and events caused by `GITHUB_TOKEN`
never start workflow runs, `promote.yml` dispatches both workflows explicitly as well. Each also
accepts a manual `workflow_dispatch` with a `tag`, which is the retry path if either fails.

## Desktop Release Details

Desktop native installers are built automatically as part of every `internal` release. There is no separate promotion flow for Desktop — installers are built once during the `internal` release and attached to the GitHub Release alongside Android artifacts; promotions to later channels reuse them.

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

Flatpak packaging is maintained externally at [flathub/org.meshtastic.MeshtasticDesktop](https://github.com/flathub/org.meshtastic.MeshtasticDesktop). It builds `:desktopApp:packageUberJarForCurrentOS` (not the native distribution pipeline) and includes its own AppStream metainfo, `.desktop` entry, and JBR bundling. The offline-build sources it consumes are captured in-repo by `scripts/verify-flatpak/` (see its README).

## Build Attestations & Provenance

All release artifacts are accompanied by explicit GitHub build attestations (provenance). This provides cryptographic proof that the artifacts were built by our trusted GitHub Actions workflow, ensuring supply chain integrity.

-   You can view and verify provenance in the GitHub UI under each release asset.
-   For more details, see [GitHub's documentation on build provenance](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions#provenance-attestations).
