#!/usr/bin/env python3
"""Prepend the AppStream <description> for this version to the generated release notes.

The metainfo entry is the only hand-written, user-facing prose in the repo and is
already required by pull-request.yml on the VERSION_NAME_BASE bump. Reuse it rather
than asking anyone to write the same sentence a second time.

Missing or empty prose is written through as a visible placeholder, never silence:
the release is created as a draft, so a human sees the placeholder before publishing.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# Rendered markdown, not an HTML comment: a comment is invisible on the release page,
# which is exactly where it needs to be noticed before someone hits publish.
PLACEHOLDER = (
    "## Highlights\n\n"
    "> [!WARNING]\n"
    "> No release highlights were found for {v}. Add a `<description>` for this version to\n"
    "> `desktopApp/packaging/linux/org.meshtastic.MeshtasticDesktop.metainfo.xml`, or write them\n"
    "> here before publishing this draft.\n"
)


def highlights(metainfo: Path, version: str) -> str | None:
    try:
        root = ET.parse(metainfo).getroot()
    except (ET.ParseError, OSError) as exc:
        print(f"could not read {metainfo}: {exc}", file=sys.stderr)
        return None
    for rel in root.findall(".//release"):
        if rel.get("version") != version:
            continue
        desc = rel.find("description")
        if desc is None:
            return None
        paras = [" ".join((p.text or "").split()) for p in desc.findall("p")]
        paras = [p for p in paras if p]
        return "\n\n".join(paras) or None
    return None


def main() -> int:
    metainfo, config, notes = (Path(a) for a in sys.argv[1:4])
    m = re.search(r"^VERSION_NAME_BASE=(.+)$", config.read_text(), re.M)
    if not m:
        print("VERSION_NAME_BASE not found; leaving notes unchanged", file=sys.stderr)
        return 0
    version = m.group(1).strip()

    body = notes.read_text() if notes.exists() else ""
    text = highlights(metainfo, version)
    if text:
        block = f"## Highlights\n\n{text}\n"
    else:
        print(f"no <description> for {version}; emitting placeholder", file=sys.stderr)
        block = PLACEHOLDER.format(v=version)
    notes.write_text(f"{block}\n{body}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
