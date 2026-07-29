#!/usr/bin/env python3
"""Generate the Obtainium artifacts from one source of truth.

This script owns:

  1. the one-tap deep-link tables in the project README and the developer guide,
  2. the per-flavor Obtainium import files in this directory, and
  3. with --refresh, the "currently published" channel table in the guide.

Two kinds of staleness, two modes:

    python3 obtainium/generate-links.py            # write offline targets
    python3 obtainium/generate-links.py --check    # verify offline targets, exit 1 on drift
    python3 obtainium/generate-links.py --refresh  # + hit the releases API (network)

--check is deterministic and offline: the links and import files derive purely
from CHANNELS x FLAVORS, so they can only drift when a commit changes them. That
is a pull-request concern.

--refresh is the part that goes stale on its own. It resolves each channel
against the live GitHub releases and asserts every `apkFilterRegEx` still matches
the assets our release workflows actually publish — so renaming a release asset
fails loudly here instead of silently breaking every user's update check. It also
records which channels are currently published, which changes as builds are
promoted. That is a scheduled concern; see scheduled-updates.yml.

`com.geeksville.mesh.json` is deliberately NOT generated here: it is a
byte-identical mirror of what we submitted to apps.obtainium.imranr.dev and is
maintained by hand alongside that PR.
"""

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
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

STATUS_BEGIN = "<!-- BEGIN GENERATED STATUS: obtainium/generate-links.py --refresh -->"
STATUS_END = "<!-- END GENERATED STATUS -->"

RELEASES_API = "https://api.github.com/repos/meshtastic/Meshtastic-Android/releases?per_page=100"

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


def replace_block(text, body, begin=BEGIN, end=END):
    """Replace the marker-delimited block in `text` with `body`."""
    start, stop = text.index(begin), text.index(end) + len(end)
    return text[:start] + body + text[stop:]


# --- live release data (--refresh only) -------------------------------------


def fetch_releases():
    request = urllib.request.Request(
        RELEASES_API, headers={"Accept": "application/vnd.github+json"}
    )
    # Authenticate when a token is present purely for the rate limit; the data is
    # public either way, so this still works locally with no token.
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def resolve_release(releases, channel):
    """The release a channel's filters select, or None if that channel is empty.

    Mirrors the subset of Obtainium's selection we rely on: honour
    includePrereleases, apply the release-title filter, skip drafts, and take the
    newest match. Obtainium walks its own sorted list, but for a published-channel
    probe "newest title match" is the same answer — confirmed on-device.
    """
    settings = channel["settings"]
    include_prereleases = settings.get("includePrereleases", False)
    title_filter = settings.get("filterReleaseTitlesByRegEx")
    matches = []
    for release in releases:
        if release.get("draft"):
            continue
        if release.get("prerelease") and not include_prereleases:
            continue
        title = (release.get("name") or release.get("tag_name") or "").strip()
        if title_filter and not re.search(title_filter, title):
            continue
        matches.append(release)
    if not matches:
        return None
    return max(matches, key=lambda r: r.get("published_at") or "")


def matching_assets(release, channel, flavor_key):
    """Asset names in `release` that this channel/flavor's apkFilterRegEx selects."""
    pattern = json.loads(config_for(channel, flavor_key)["additionalSettings"])[
        "apkFilterRegEx"
    ]
    return [
        asset["name"]
        for asset in release.get("assets", [])
        if asset["name"].lower().endswith((".apk", ".xapk", ".apkm", ".apks"))
        and re.search(pattern, asset["name"])
    ]


def probe(releases):
    """Resolve every channel/flavor against live releases.

    Returns (rows, problems): rows feed the generated status table, problems are
    regexes that no longer match anything in a release that does exist — i.e. the
    release assets were renamed and these configs are broken.
    """
    rows, problems = [], []
    for channel in CHANNELS:
        release = resolve_release(releases, channel)
        counts = {}
        if release is not None:
            for flavor_key in FLAVORS:
                assets = matching_assets(release, channel, flavor_key)
                counts[flavor_key] = len(assets)
                if not assets:
                    problems.append(
                        f"{channel['key']}/{flavor_key}: apkFilterRegEx matched no "
                        f"APK in {release.get('tag_name')} — release assets renamed?"
                    )
        rows.append({"channel": channel, "release": release, "counts": counts})
    return rows, problems


def render_status(rows):
    """The live channel table. README only — never the archived/bundled guide."""
    lines = [STATUS_BEGIN, "", "| Channel | Currently | Released |", "|---|---|---|"]
    for row in rows:
        channel, release = row["channel"], row["release"]
        label = README_LABELS.get(channel["key"], channel["label"])
        if release is None:
            lines.append(f"| {label} | *none published right now* | — |")
            continue
        # Snapshot is rebuilt per push, so its tag is not a useful "version".
        if channel.get("debug"):
            version = "rolling, rebuilt on every push to `main`"
        else:
            version = f"`{release.get('tag_name', '?')}`"
        date = (release.get("published_at") or "")[:10] or "—"
        lines.append(f"| {label} | {version} | {date} |")
    lines += ["", STATUS_END]
    return "\n".join(lines)


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


def targets(status_rows=None):
    """Map of path -> expected full file content.

    `status_rows` is supplied only by --refresh; without it the live status block
    is left exactly as committed, so --check stays offline and deterministic.
    """
    out = {export_path(k): render_export(k) for k in FLAVORS}

    # The guide is archived per release tag and bundled into the app, so it gets
    # only the deep links — those derive from constants and never go stale. Live
    # release state goes in the README alone, which GitHub always renders from the
    # default branch.
    out[DOC] = replace_block(DOC.read_text(), render_table())

    readme_channels = [c for c in CHANNELS if c["key"] in README_CHANNELS]
    readme = replace_block(
        README.read_text(),
        render_table(readme_channels, label_of=lambda c: README_LABELS[c["key"]]),
    )
    if status_rows is not None:
        readme = replace_block(
            readme,
            render_status([r for r in status_rows if r["channel"]["key"] in README_CHANNELS]),
            STATUS_BEGIN,
            STATUS_END,
        )
    out[README] = readme
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--check", action="store_true", help="verify generated files are current"
    )
    parser.add_argument(
        "--refresh",
        action="store_true",
        help="also probe the live releases API and refresh the channel status table",
    )
    args = parser.parse_args()

    status_rows = None
    if args.refresh:
        try:
            releases = fetch_releases()
        except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as err:
            # A flaky API must not be reported as "configs are broken"; leave the
            # committed status block alone and let the next run pick it up.
            print(f"::warning::releases API unreachable ({err}); skipping --refresh")
            return 0
        status_rows, problems = probe(releases)
        for problem in problems:
            print(f"::error::{problem}")
        if problems:
            print(
                "An apkFilterRegEx no longer matches the published assets. Either the "
                "release workflow renamed them, or FLAVORS needs updating."
            )
            return 1

    try:
        expected = targets(status_rows)
    except ValueError:
        print(f"error: {DOC} is missing the BEGIN/END GENERATED markers")
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
