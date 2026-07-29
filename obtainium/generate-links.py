#!/usr/bin/env python3
"""Generate the Obtainium artifacts from one source of truth.

This script owns two things:

  1. the one-tap deep-link table inside the developer guide, written between the
     BEGIN/END GENERATED markers, and
  2. the per-flavor Obtainium import files in this directory.

Run it after changing CHANNELS or FLAVORS; run with --check to confirm nothing
has drifted.

    python3 obtainium/generate-links.py            # write the doc + export files
    python3 obtainium/generate-links.py --check    # verify, exit 1 on drift

`com.geeksville.mesh.json` is deliberately NOT generated here: it is a
byte-identical mirror of what we submitted to apps.obtainium.imranr.dev and is
maintained by hand alongside that PR.
"""

import argparse
import json
import sys
from pathlib import Path
from urllib.parse import quote

REPO_URL = "https://github.com/meshtastic/Meshtastic-Android"
REDIRECT = "https://apps.obtainium.imranr.dev/redirect.html?r="

REPO_ROOT = Path(__file__).resolve().parent.parent
DOC = REPO_ROOT / "docs/en/developer/test-builds.md"
README = REPO_ROOT / "README.md"
OBTAINIUM_DIR = REPO_ROOT / "obtainium"

BEGIN = "<!-- BEGIN GENERATED LINKS: obtainium/generate-links.py -->"
END = "<!-- END GENERATED LINKS -->"

# The developer guide documents every channel; the project README only carries
# the two channels a normal user should be choosing between.
README_CHANNELS = ("stable", "open")

# Release assets are androidApp-<flavor>[-<abi>]-release.apk; snapshot builds
# attach androidApp-<flavor>-<abi>-debug-<versionCode>.apk instead. The google
# flavor ships a single universal release APK, so it needs no arch filter; the
# fdroid flavor is split per ABI and lets Obtainium's arch filter choose.
FLAVORS = {
    "google": {
        "label": "google",
        "release_apk": {"apkFilterRegEx": r"google-release\.apk$"},
        "debug_apk": {
            "apkFilterRegEx": r"google-.*-debug-\d+\.apk$",
            "autoApkFilterByArch": True,
        },
        "debug_id": "com.geeksville.mesh.google.debug",
    },
    "fdroid": {
        "label": "fdroid",
        "release_apk": {
            "apkFilterRegEx": r"fdroid-.*-release\.apk$",
            "autoApkFilterByArch": True,
        },
        "debug_apk": {
            "apkFilterRegEx": r"fdroid-.*-debug-\d+\.apk$",
            "autoApkFilterByArch": True,
        },
        "debug_id": "com.geeksville.mesh.fdroid.debug",
    },
}

RELEASE_ID = "com.geeksville.mesh"

# Every non-debug channel is RELEASE_ID, so only one of them can be tracked at a
# time. `debug` marks the snapshot channel, which gets its own application ID.
CHANNELS = [
    {"key": "stable", "label": "Stable", "name": "Meshtastic", "settings": {}},
    {
        "key": "open",
        "label": "Open beta",
        "name": "Meshtastic Beta",
        "settings": {"includePrereleases": True, "filterReleaseTitlesByRegEx": "-open"},
    },
    {
        "key": "closed",
        "label": "Closed beta",
        "name": "Meshtastic Alpha",
        "settings": {"includePrereleases": True, "filterReleaseTitlesByRegEx": "-closed"},
    },
    {
        "key": "bleeding",
        "label": "Bleeding edge (newest promoted test build)",
        "name": "Meshtastic Beta",
        "settings": {
            "includePrereleases": True,
            "filterReleaseTitlesByRegEx": "-(closed|open)",
        },
    },
    {
        "key": "snapshot",
        "label": "Snapshot (latest commit on `main`)",
        "name": "Meshtastic Snapshot",
        "debug": True,
        "settings": {
            "includePrereleases": True,
            "filterReleaseTitlesByRegEx": "^Snapshot",
            "useLatestAssetDateAsReleaseDate": True,
            # The tag never moves off "snapshot", so the release date is the only
            # thing that changes between builds.
            "versionDetection": False,
            "releaseDateAsVersion": True,
        },
    },
]

# What each per-flavor import file carries.
#
# Release builds of both flavors are RELEASE_ID, so an import file can only ever
# carry ONE of them — Obtainium's import calls saveApps() keyed by id, and a
# second entry with the same id silently overwrites the first. That is why the
# flavor choice is expressed as two separate files, and why the beta channels
# (also RELEASE_ID) are left to the deep links rather than bundled here.
#
# Debug builds are the exception: they carry a per-flavor `.debug` suffix, so
# both snapshots are genuinely distinct apps and every file ships both.
EXPORT_RELEASE_CHANNEL = "stable"
EXPORT_DEBUG_CHANNELS = ("snapshot",)


def config_for(channel, flavor_key):
    """Build one Obtainium app config for a channel/flavor pair."""
    flavor = FLAVORS[flavor_key]
    is_debug = channel.get("debug", False)
    settings = dict(channel["settings"])
    settings.update(flavor["debug_apk"] if is_debug else flavor["release_apk"])
    # Flavor-suffix only the snapshots. Both debug flavors can coexist, so their
    # labels have to disambiguate; a release entry is always singular, so
    # "Meshtastic" beats branding it with a build-flavor suffix forever.
    name = f"{channel['name']} ({flavor['label']})" if is_debug else channel["name"]
    # appName is the only reliable label: App.finalName is
    # `additionalSettings['appName'] ?? name`, and Obtainium otherwise falls back
    # to the installed app's own label (a debug build shows up as "Google Debug").
    settings["appName"] = name
    return {
        "id": flavor["debug_id"] if is_debug else RELEASE_ID,
        "url": REPO_URL,
        "author": "meshtastic",
        "name": name,
        "additionalSettings": json.dumps(settings, separators=(",", ":")),
    }


def deep_link(channel, flavor_key):
    blob = json.dumps(config_for(channel, flavor_key), separators=(",", ":"))
    # Only the JSON payload is percent-encoded; the scheme prefix stays literal.
    return f"{REDIRECT}obtainium://app/{quote(blob, safe='')}"


def render_table(channels=None, label_of=None):
    """Render the marker-delimited link table for `channels` (default: all)."""
    channels = channels or CHANNELS
    lines = [
        BEGIN,
        "",
        "| Channel | `google` flavor | `fdroid` flavor |",
        "|---|---|---|",
    ]
    for channel in channels:
        cells = " | ".join(
            f"[Add]({deep_link(channel, flavor_key)})" for flavor_key in FLAVORS
        )
        label = label_of(channel) if label_of else channel["label"]
        lines.append(f"| {label} | {cells} |")
    lines += ["", END]
    return "\n".join(lines)


def inject(path, body):
    """Replace the marker-delimited block in `path` with `body`."""
    text = path.read_text()
    start, end = text.index(BEGIN), text.index(END) + len(END)
    return text[:start] + body + text[end:]


def render_export(flavor_key):
    by_key = {c["key"]: c for c in CHANNELS}
    # One release entry — this file's flavor — plus every debug flavor, which can
    # all coexist thanks to their per-flavor application-ID suffixes.
    apps = [config_for(by_key[EXPORT_RELEASE_CHANNEL], flavor_key)]
    for key in EXPORT_DEBUG_CHANNELS:
        apps += [config_for(by_key[key], f) for f in FLAVORS]
    # Legacy `{apps, settings}` shape on purpose: Obtainium reads it via the
    # schemaVersion-absent branch, so we avoid inventing exportedAt/appVersion
    # provenance for a file no Obtainium install actually produced.
    return json.dumps({"apps": apps, "settings": None}, indent=4) + "\n"


def export_path(flavor_key):
    return OBTAINIUM_DIR / f"meshtastic-obtainium-export-{flavor_key}.json"


README_LABELS = {"stable": "**Latest release**", "open": "**Open beta**"}


def targets():
    """Map of path -> expected full file content."""
    out = {export_path(k): render_export(k) for k in FLAVORS}
    out[DOC] = inject(DOC, render_table())
    readme_channels = [c for c in CHANNELS if c["key"] in README_CHANNELS]
    out[README] = inject(
        README,
        render_table(readme_channels, label_of=lambda c: README_LABELS[c["key"]]),
    )
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--check", action="store_true", help="verify generated files are current"
    )
    args = parser.parse_args()

    try:
        expected = targets()
    except ValueError:
        print(f"error: {DOC} is missing the BEGIN/END GENERATED LINKS markers")
        return 1

    # A missing file counts as drifted rather than crashing, so --check works on
    # a fresh checkout and the first run can create the export files.
    drifted = [
        p
        for p, content in expected.items()
        if not p.exists() or p.read_text() != content
    ]

    if args.check:
        for path in drifted:
            print(f"drifted: {path.relative_to(REPO_ROOT)}")
        if drifted:
            print("run: python3 obtainium/generate-links.py")
            return 1
        print(f"up to date: {len(expected)} generated file(s)")
        return 0

    for path, content in expected.items():
        path.write_text(content)
        print(f"wrote: {path.relative_to(REPO_ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
