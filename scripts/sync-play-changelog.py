#!/usr/bin/env python3
"""Write the Play "what's new" text from the AppStream release description.

metainfo.xml already carries one hand-written paragraph per version, required by
pull-request.yml on the VERSION_NAME_BASE bump. This renders that same paragraph
into fastlane/metadata/android/en-US/changelogs/default.txt, followed by a link to
the full release notes, so the store listing says what the release did.

default.txt rather than a <versionCode>.txt: it is the file crowdin.yml already
maps, so translations continue in place instead of starting from zero on a new
source file every internal build. Play falls back to it for any build without a
version-specific file, which is all of them here.

Run it in the PR that bumps VERSION_NAME_BASE, so Crowdin has the whole internal
cycle to translate. Every promotion (closed, open, production) uploads the file for
each locale onto the promoted release.

    python3 scripts/sync-play-changelog.py [--check]
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
METAINFO = REPO_ROOT / "desktopApp/packaging/linux/org.meshtastic.MeshtasticDesktop.metainfo.xml"
CONFIG = REPO_ROOT / "config.properties"
TARGET = REPO_ROOT / "fastlane/metadata/android/en-US/changelogs/default.txt"
RELEASES_URL = "https://github.com/meshtastic/Meshtastic-Android/releases"

# Google Play caps "what's new" at 500 characters per locale. Translations run
# longer than English, so leave room rather than filling it.
PLAY_LIMIT = 500
BUDGET = 380


def version() -> str:
    m = re.search(r"^VERSION_NAME_BASE=(.+)$", CONFIG.read_text(), re.M)
    if not m:
        sys.exit("VERSION_NAME_BASE not found in config.properties")
    return m.group(1).strip()


def description(v: str) -> str:
    root = ET.parse(METAINFO).getroot()
    for rel in root.findall(".//release"):
        if rel.get("version") != v:
            continue
        desc = rel.find("description")
        if desc is None:
            break
        paras = [" ".join((p.text or "").split()) for p in desc.findall("p")]
        paras = [p for p in paras if p]
        if paras:
            return " ".join(paras)
        break
    sys.exit(
        f"no <description> for {v} in {METAINFO.relative_to(REPO_ROOT)} - "
        "add one alongside the VERSION_NAME_BASE bump"
    )


def render(text: str) -> str:
    if len(text) > BUDGET:
        cut = text[:BUDGET].rsplit(" ", 1)[0].rstrip(" .,;:")
        text = f"{cut}…"
    return f"{text}\n\nFull release notes: {RELEASES_URL}\n"


def main() -> int:
    body = render(description(version()))
    if len(body.rstrip()) > PLAY_LIMIT:
        sys.exit(f"rendered text is {len(body.rstrip())} chars, over Play's {PLAY_LIMIT}")
    if "--check" in sys.argv:
        current = TARGET.read_text() if TARGET.exists() else ""
        if current != body:
            print(
                f"::error file={TARGET.relative_to(REPO_ROOT)}::Play what's-new is stale. "
                "Run python3 scripts/sync-play-changelog.py and commit the result.",
                file=sys.stderr,
            )
            return 1
        print("Play what's-new matches metainfo.")
        return 0
    TARGET.write_text(body)
    print(f"wrote {TARGET.relative_to(REPO_ROOT)} ({len(body.rstrip())} chars)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
