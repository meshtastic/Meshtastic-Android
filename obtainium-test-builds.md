# Installing Meshtastic Test Builds with Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) installs and auto-updates
Android apps straight from their GitHub releases — no Play Store account, no
testing-track invite. This guide sets it up for Meshtastic and shows how to
follow the **open beta** or **closed beta** channels.

> **Heads up — signatures.** Builds from GitHub are signed with the project's
> release key, **not** Google Play's. If you already have Meshtastic installed
> from the Play Store, Android will refuse to update over it. You must
> **uninstall the Play Store version first** (this clears app data), then
> install via Obtainium and stay on Obtainium for updates. The fdroid and
> google flavors also differ in signature — pick one and don't switch.

## Which channels can Obtainium reach?

Meshtastic for Android promotes builds up a ladder:
`closed → open → production`. Each maps to a Google Play track, and the GitHub
release is published (un-drafted) when a build is promoted to closed or higher.

| Channel | Play track | On GitHub | Obtainium can install it? |
|---|---|---|---|
| **stable** | Production | published, marked *Latest* | ✅ Yes |
| **open** beta | Beta (Open) | published prerelease, tag `vX.Y.Z-open.N` | ✅ Yes |
| **closed** beta | Alpha (Closed) | published prerelease, tag `vX.Y.Z-closed.N` | ✅ Yes |
| **snapshot** | — (not on Play) | rolling prerelease, tag `snapshot` | ✅ Yes |

Only **one** promoted test build is "live" on GitHub at a time: as a build is
promoted, its release object moves forward (its tag changes from `-closed.N` to
`-open.N` to the clean production tag). So a `-closed`/`-open` build is
installable only while it is currently parked in that channel — once promoted
onward, the old channel tag no longer has a release.

**Snapshot** is different: it's an automated debug build of the latest commit on
`main`, rebuilt and re-published under the single moving `snapshot` tag on every
push. It never goes to Play. Because debug builds use a `.debug` application-ID
suffix (`com.geeksville.mesh.fdroid.debug` / `com.geeksville.mesh.google.debug`)
and the debug signing key, a snapshot installs as its **own separate app** — it
sits alongside a Play/stable/beta install, so the uninstall-first warning above
does **not** apply to it.

## Setup

1. Install Obtainium ([GitHub releases](https://github.com/ImranR98/Obtainium/releases)
   or [F-Droid / IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/dev.imranr.obtainium)).
2. Tap **Add App**.
3. **App Source URL:**
   ```
   https://github.com/meshtastic/Meshtastic-Android
   ```
4. Set the options for the channel you want (below).
5. Tap **Add**, then **Install**.

### Stable

- **Include prereleases:** off
- *(optional, stricter)* **Verify the 'latest' tag:** on
- **Filter APKs by regular expression:** see [Picking the APK](#picking-the-apk)

### Open beta

- **Include prereleases:** on
- **Filter release titles by regular expression:** `-open`
- **Filter APKs by regular expression:** see [Picking the APK](#picking-the-apk)

### Closed beta

- **Include prereleases:** on
- **Filter release titles by regular expression:** `-closed`
- **Filter APKs by regular expression:** see [Picking the APK](#picking-the-apk)

### Bleeding edge (newest promoted test build, any channel)

- **Include prereleases:** on
- **Filter release titles by regular expression:** `-(closed|open)`
- **Filter APKs by regular expression:** see [Picking the APK](#picking-the-apk)

Obtainium installs the newest promoted prerelease — whatever is currently in
open or closed. The title filter is required to skip the always-newer `snapshot`
prerelease; without it Obtainium would follow snapshot instead.

### Snapshot (latest commit on `main`)

- **Include prereleases:** on
- **Filter release titles by regular expression:** `^Snapshot`
- **Filter APKs by regular expression:** debug-signed names, see below

Follows `main` directly — updates on every push. These are **debug builds**
(`.debug` package, debug key), so they install as a separate app and won't
disturb a stable/beta install. The APKs are named `…-debug-<versionCode>.apk`
(not `-release.apk`), so use debug-suffixed filters:

| You want | Regex |
|---|---|
| Google flavor, most phones (arm64) | `google-arm64-v8a-debug-\d+\.apk` |
| fdroid flavor, most phones (arm64) | `fdroid-arm64-v8a-debug-\d+\.apk` |
| fdroid flavor, one-size-fits-all | `fdroid-universal-debug-\d+\.apk` |

Snapshot releases attach only the debug APKs — no `.aab` or desktop installers.

> **If your channel filter finds nothing:** when no build is parked in that
> exact channel, the title filter matches no current release (old channel tags
> are orphaned once promoted). For a strict channel pin, turn **Fallback to
> older releases** *off*. To always have something to install, use the
> *Bleeding edge* form above instead.

## Picking the APK

Each release attaches the **google** flavor APK, several **fdroid** flavor APKs,
plus an `.aab` (not installable) and the desktop installers. Pin exactly one
with **Filter APKs by regular expression**:

| You want | Regex |
|---|---|
| **Recommended for testing — Google flavor** | `google-release\.apk` |
| fdroid flavor, most phones (arm64) | `fdroid-arm64-v8a-release\.apk` |
| fdroid flavor, one-size-fits-all | `fdroid-universal-release\.apk` |

**Use the Google flavor while testing.** It ships Firebase Crashlytics and
Datadog RUM, so the crashes and errors you hit get reported back to the team —
which is the whole point of a test phase. It also has Google push (FCM) and
Google Maps. Pick an fdroid flavor only if you'd rather not send that
telemetry. Keep the same flavor on every update — switching flavors triggers
the signature-mismatch refusal described above.

## Notes

- **Track without installing:** turn on **Track-only** to get update
  notifications without Obtainium downloading anything.
