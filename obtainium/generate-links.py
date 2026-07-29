#!/usr/bin/env python3
"""Emit Obtainium one-tap install links for the Meshtastic channel configs.

Obtainium's `obtainium://app/<url-encoded config JSON>` scheme adds an app with
its settings baked in. The apps.obtainium.imranr.dev redirect wrapper exists so
the link survives being pasted somewhere that won't linkify a custom scheme.

Usage:
    python3 obtainium/generate-links.py            # markdown table
    python3 obtainium/generate-links.py --json     # raw config JSON per channel
"""

import argparse
import json
import sys
from urllib.parse import quote

REPO_URL = "https://github.com/meshtastic/Meshtastic-Android"
REDIRECT = "https://apps.obtainium.imranr.dev/redirect.html?r="

# Release assets are named androidApp-<flavor>[-<abi>]-release.apk; snapshot
# builds attach androidApp-<flavor>-<abi>-debug-<versionCode>.apk instead.
GOOGLE_APK = r"google-release\.apk$"
FDROID_APK = r"fdroid-.*-release\.apk$"
SNAPSHOT_APK = r"google-.*-debug-\d+\.apk$"

CHANNELS = [
    (
        "Stable (Google flavor)",
        "com.geeksville.mesh",
        "Meshtastic",
        {"apkFilterRegEx": GOOGLE_APK},
    ),
    (
        "Stable (F-Droid flavor)",
        "com.geeksville.mesh",
        "Meshtastic",
        {"apkFilterRegEx": FDROID_APK, "autoApkFilterByArch": True},
    ),
    (
        "Open beta",
        "com.geeksville.mesh",
        "Meshtastic Beta",
        {
            "includePrereleases": True,
            "filterReleaseTitlesByRegEx": "-open",
            "apkFilterRegEx": GOOGLE_APK,
        },
    ),
    (
        "Closed beta",
        "com.geeksville.mesh",
        "Meshtastic Alpha",
        {
            "includePrereleases": True,
            "filterReleaseTitlesByRegEx": "-closed",
            "apkFilterRegEx": GOOGLE_APK,
        },
    ),
    (
        "Bleeding edge (newest promoted test build)",
        "com.geeksville.mesh",
        "Meshtastic Beta",
        {
            "includePrereleases": True,
            "filterReleaseTitlesByRegEx": "-(closed|open)",
            "apkFilterRegEx": GOOGLE_APK,
        },
    ),
    (
        "Snapshot (latest commit on main)",
        "com.geeksville.mesh.google.debug",
        "Meshtastic Snapshot",
        {
            "includePrereleases": True,
            "filterReleaseTitlesByRegEx": "^Snapshot",
            "useLatestAssetDateAsReleaseDate": True,
            # The tag never moves off "snapshot", so date is the only usable version.
            "versionDetection": False,
            "releaseDateAsVersion": True,
            "apkFilterRegEx": SNAPSHOT_APK,
            "autoApkFilterByArch": True,
        },
    ),
]


def config_for(app_id, name, settings):
    return {
        "id": app_id,
        "url": REPO_URL,
        "author": "meshtastic",
        "name": name,
        "additionalSettings": json.dumps(settings, separators=(",", ":")),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", action="store_true", help="print configs, not links")
    args = parser.parse_args()

    if args.json:
        for label, app_id, name, settings in CHANNELS:
            print(f"# {label}")
            print(json.dumps(config_for(app_id, name, settings), separators=(",", ":")))
        return 0

    print("| Channel | Link |")
    print("|---|---|")
    for label, app_id, name, settings in CHANNELS:
        blob = json.dumps(config_for(app_id, name, settings), separators=(",", ":"))
        # Only the JSON payload is percent-encoded; the scheme prefix stays literal.
        scheme = "obtainium://app/" + quote(blob, safe="")
        print(f"| {label} | [Add to Obtainium]({REDIRECT}{scheme}) |")
    return 0


if __name__ == "__main__":
    sys.exit(main())
