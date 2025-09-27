# Meshtastic-Android Release Process (Condensed)

This guide summarizes the steps for releasing a new version of Meshtastic-Android. The process is automated via GitHub Actions and Fastlane, triggered by pushing a Git tag from a `release/*` branch.

## Overview
- **Tagging:** Push a tag (`vX.X.X[-track.Y]`) from a `release/*` branch to start the release workflow.
- **CI Automation:** Builds both flavors, uploads to Google Play (correct track), and creates/updates a draft GitHub release.
- **Changelog:** Release notes are auto-generated from PR labels via [`.github/release.yml`](.github/release.yml). Label PRs for accurate changelogs.
- **Draft Release:** All tags for the same base version (e.g., `v2.3.5`) update the same draft release. The release title uses the full tag (e.g., `v2.3.5-internal.1`).

## Tagging & Tracks
- **Internal:** `vX.X.X-internal.Y`
- **Closed:** `vX.X.X-closed.Y`
- **Open:** `vX.X.X-open.Y`
- **Production:** `vX.X.X`
- Increment `.Y` for fixes/iterations.

## Release Steps
1. **Branch:** Create `release/X.X.X` from `main`. Only critical fixes allowed.
2. **Tag & Push:** Tag the release commit and push (see below).
3. **CI:** Wait for CI to finish. Artifacts are uploaded, and a draft GitHub release is created/updated.
4. **Verify:** Check Google Play Console and GitHub draft release.
5. **Promote:** Tag the same commit for the next track as needed.
6. **Finalize:**
   - **Production:** Publish the GitHub release, then promote in Google Play Console.
   - **Other tracks:** Verify with testers.
7. **Merge:** After production, merge `release/X.X.X` back to `main` and delete the branch.

## Tagging Example
```bash
# On release branch
git tag v2.3.5-internal.1
git push origin v2.3.5-internal.1
# For fixes:
git tag v2.3.5-internal.2
git push origin v2.3.5-internal.2
# Promote:
git tag v2.3.5-closed.1
git push origin v2.3.5-closed.1
```

## Manual Checklist
- [ ] Verify build in Google Play Console
- [ ] Review and publish GitHub draft release (for production)
- [ ] Merge release branch to main after production
- [ ] Label PRs for changelog accuracy

## Build Attestations & Provenance

All release artifacts are accompanied by explicit GitHub build attestations (provenance). After each artifact is uploaded in the release workflow, a provenance attestation is generated using the `actions/attest-build-provenance` action. This provides cryptographic proof that the artifacts were built by our trusted GitHub Actions workflow, ensuring supply chain integrity and allowing users to verify the origin of each release.

- Attestations are generated immediately after each artifact upload in the workflow.
- You can view and verify provenance in the GitHub UI under each release asset.
- For more details, see [GitHub's documentation on build provenance](https://docs.github.com/en/actions/security-guides/security-hardening-for-github-actions#provenance-attestations).

> **Note:** The GitHub release is always attached to the base version tag (e.g., `v2.3.5`). All track tags for the same version update the same draft release. Look for the draft under the base version tag.

> **Best Practice:** Always promote the last verified build from the previous track to the next track. Do not introduce new changes between tracks unless absolutely necessary. This ensures consistency, traceability, and minimizes risk.
