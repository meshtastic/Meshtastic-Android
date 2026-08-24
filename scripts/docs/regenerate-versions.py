#!/usr/bin/env python3
"""Regenerate versions.json (and the root placeholder) for the docs site.

Scans a built gh-pages tree and derives the manifest consumed by the site's
version switcher from the directories actually present, so the manifest can
never disagree with what is published.

Channel layout on gh-pages:
    /                     production release docs (owns the root)
    /vX.Y.Z/              permanent per-release snapshot
    /vX.Y.Z-open.N/       per-tag open-testing snapshot
    /vX.Y.Z-closed.N/     per-tag closed-testing snapshot
    /main/                snapshot of the main branch
    /api/                 Dokka reference (unversioned)

Prerelease snapshots accumulate during a version cycle and are reaped by
post-release-cleanup.yml once the production vX.Y.Z tag ships.

Usage: regenerate-versions.py <site-dir>
"""

from __future__ import annotations

import json
import os
import re
import sys

RELEASE = re.compile(r"^v(\d+)\.(\d+)\.(\d+)$")
PRERELEASE = re.compile(r"^v(\d+)\.(\d+)\.(\d+)-(open|closed)\.(\d+)$")

# Marks a root index.html this script generated. Production release content
# never carries it, which is how we tell "root is unowned, retarget it freely"
# apart from "a real release owns the root, leave it alone".
PLACEHOLDER_MARKER = "<!-- meshtastic-docs-root-placeholder -->"

PLACEHOLDER = (
    PLACEHOLDER_MARKER
    + """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta http-equiv="refresh" content="0; url=./{target}/">
  <title>Meshtastic Android Docs</title>
</head>
<body><a href="./{target}/">Redirecting to the {target} docs…</a></body>
</html>
"""
)

# Track ranking for the switcher and the root fallback. Open testing is publicly
# available, so it outranks closed testing. Sequence numbers are per-track
# counters and are never compared across tracks.
TRACK_RANK = {"open": 1, "closed": 0}


def scan(site: str) -> tuple[list[dict], list[dict]]:
    releases: list[dict] = []
    prereleases: list[dict] = []
    for name in sorted(os.listdir(site)):
        if not os.path.isdir(os.path.join(site, name)):
            continue
        if m := RELEASE.match(name):
            releases.append(
                {"dir": name, "version": ".".join(m.groups()), "sort": tuple(int(x) for x in m.groups())}
            )
        elif m := PRERELEASE.match(name):
            major, minor, patch, track, seq = m.groups()
            prereleases.append(
                {
                    "dir": name,
                    "version": f"{major}.{minor}.{patch}",
                    "track": track,
                    "seq": int(seq),
                    "sort": (int(major), int(minor), int(patch), int(seq)),
                }
            )
    releases.sort(key=lambda r: r["sort"], reverse=True)
    # Group by track (open before closed), newest version/sequence first within
    # each. Track must dominate: 'seq' counts up independently per track, so
    # comparing open.1 against closed.10 numerically would be meaningless.
    prereleases.sort(key=lambda p: (TRACK_RANK[p["track"]], p["sort"]), reverse=True)
    return releases, prereleases


def main() -> int:
    if len(sys.argv) != 2:
        print(f"usage: {os.path.basename(sys.argv[0])} <site-dir>", file=sys.stderr)
        return 2
    site = sys.argv[1]
    if not os.path.isdir(site):
        print(f"not a directory: {site}", file=sys.stderr)
        return 2

    releases, prereleases = scan(site)
    manifest = {
        "latest": releases[0]["version"] if releases else None,
        "versions": [r["version"] for r in releases],
        "prereleases": [{k: p[k] for k in ("dir", "version", "track", "seq")} for p in prereleases],
        "hasMain": os.path.isdir(os.path.join(site, "main")),
    }
    with open(os.path.join(site, "versions.json"), "w") as handle:
        json.dump(manifest, handle, indent=2)

    # The root belongs to production releases. Until one exists, redirect it to
    # the best available channel so the Pages URL is never a 404:
    # newest open prerelease -> newest closed prerelease -> main snapshot.
    #
    # Re-evaluated on every run so the root upgrades as better channels appear
    # (main -> closed -> open), but only when the current root is our own
    # placeholder — real release content is never overwritten.
    root_index = os.path.join(site, "index.html")
    owned_by_release = False
    if os.path.exists(root_index):
        with open(root_index, encoding="utf-8", errors="replace") as handle:
            owned_by_release = PLACEHOLDER_MARKER not in handle.read(len(PLACEHOLDER_MARKER) + 64)

    if not owned_by_release:
        target = next((p["dir"] for p in prereleases if p["track"] == "open"), None) or next(
            (p["dir"] for p in prereleases if p["track"] == "closed"), None
        )
        if target is None and manifest["hasMain"]:
            target = "main"
        if target:
            with open(root_index, "w") as handle:
                handle.write(PLACEHOLDER.format(target=target))
            print(f"Root placeholder -> {target}/ (no production release published yet)")
    else:
        print("Root is owned by a production release; left untouched.")

    print(f"versions.json: {json.dumps(manifest)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
