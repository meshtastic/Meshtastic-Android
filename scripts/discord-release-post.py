#!/usr/bin/env python3
"""Print the Discord webhook payload announcing a release, built from its GitHub release notes.

    discord-release-post.py <notes.md> <tag> <track> <url>

The notes are the published release body: a Highlights paragraph, then GitHub's generated
sections of "* type(scope): subject by @author in <pull url>" lines. The post keeps the
Highlights, then each section with the conventional-commit prefix, author and URL stripped
down to "subject (#N)". Discord caps an embed description at 4096 characters, so the list is
cut on a bullet boundary and says how many entries it dropped.
"""
import json
import re
import sys
from pathlib import Path

LIMIT = 4096
COLOR = 5763719
ENTRY = re.compile(r"^\*\s+(?:\w+(?:\([^)]*\))?!?:\s+)?(.+?)\s+by @\S+ in \S+/pull/(\d+)\s*$")
EMOJI = re.compile(r"^[^\w#]+\s*")


def parse(notes: str) -> tuple[str, list[tuple[str, list[str]]]]:
    highlights: list[str] = []
    sections: list[tuple[str, list[str]]] = []
    in_highlights = False
    for line in notes.splitlines():
        line = line.rstrip()
        if line.startswith("## Highlights"):
            in_highlights = True
        elif line.startswith("### "):
            in_highlights = False
            sections.append((EMOJI.sub("", line[4:]), []))
        elif line.startswith("## ") or line.startswith("**Full Changelog**"):
            in_highlights = False
        elif in_highlights:
            if line and not line.startswith(("<!--", ">")):
                highlights.append(line)
        elif sections and (m := ENTRY.match(line)):
            subject = m.group(1)
            sections[-1][1].append(f"- {subject[:1].upper()}{subject[1:]} (#{m.group(2)})")
    return "\n".join(highlights), [(name, items) for name, items in sections if items]


def description(notes: str, track: str, url: str) -> str:
    highlights, sections = parse(notes)
    head = f"Promoted to the **{track}** track."
    if highlights:
        head += f"\n\n{highlights}"
    blocks = [(f"### {name}", items) for name, items in sections]
    out = head
    omitted = sum(len(items) for _, items in blocks)
    for heading, items in blocks:
        block = f"\n\n{heading}"
        if len(out) + len(block) + len(items[0]) + 1 > LIMIT - 80:
            break
        out += block
        for item in items:
            if len(out) + len(item) + 1 > LIMIT - 80:
                break
            out += f"\n{item}"
            omitted -= 1
    if omitted:
        out += f"\n\n[{omitted} more in the full notes]({url})"
    return out[:LIMIT]


def main() -> int:
    notes, tag, track, url = sys.argv[1:5]
    text = Path(notes).read_text() if Path(notes).exists() else ""
    payload = {
        "content": None,
        "embeds": [
            {
                "title": f"Android {tag}",
                "description": description(text, track, url),
                "url": url,
                "color": COLOR,
            }
        ],
    }
    print(json.dumps(payload))
    return 0


if __name__ == "__main__":
    sys.exit(main())
