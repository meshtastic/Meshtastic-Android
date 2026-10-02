#!/usr/bin/env python3
"""Restore the structural front matter of translated docs pages from English.

Crowdin translates every front matter value in docs/<locale>/, but some keys are
structure the site reads rather than prose:

    layout     names a file in _layouts/ or the theme; a translated name renders
               the page without the theme.
    nav_order  a number the theme sorts by.

Each of these keys in docs/<locale>/<path>.md is set to what docs/en/<path>.md
has, verbatim, including multi-line values and absence.

parent and grand_parent are removed from every locale page, with or without an
English twin. A locale has no section pages of its own, and just-the-docs lists
every page whose parent matches a page's title in that page's table of contents,
nav_exclude or not, so a parent can only attach the locale page to an English
section. The locale_page layout links each page to its English original instead.

Every other line is left as Crowdin wrote it.

A locale page with no front matter while its English page has one is reported
but not rewritten: Crowdin rebuilds each translation from the English source's
structure on the next download.

Usage: sync-locale-front-matter.py [--check] [<docs-dir>]

With --check nothing is written and the exit status is non-zero when any locale
page differs from English on layout or nav_order, or has a parent or grand_parent.
Under GitHub Actions each finding is also an annotation on the file.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
STRUCTURAL_KEYS = ("layout", "nav_order")
NAVIGATION_KEYS = ("parent", "grand_parent")
LOCALE_DIR = re.compile(r"^[a-z]{2,3}(-r[A-Za-z]+)?$")
TOP_LEVEL_KEY = re.compile(r"^([A-Za-z_][A-Za-z0-9_-]*)\s*:(\s|$)")
IN_GITHUB_ACTIONS = os.environ.get("GITHUB_ACTIONS") == "true"

Entry = tuple[str | None, list[str]]


def split_lines(text: str) -> list[str]:
    # str.splitlines would also split on U+2028 and friends, which are content here.
    parts = text.split("\n")
    lines = [part + "\n" for part in parts[:-1]]
    return lines + [parts[-1]] if parts[-1] else lines


def content(lines: list[str]) -> list[str]:
    end = len(lines)
    while end > 1 and not lines[end - 1].strip():
        end -= 1
    return lines[:end]


class FrontMatter:
    """A page split into its front matter entries, one per top-level key."""

    def __init__(self, head: str, entries: list[Entry], tail: list[str]) -> None:
        self.head = head
        # Lines before the first key, if any, form an entry whose key is None.
        self.entries = entries
        self.tail = tail

    @classmethod
    def parse(cls, text: str) -> FrontMatter | None:
        lines = split_lines(text)
        if not lines or lines[0].rstrip() != "---":
            return None
        close = next((i for i in range(1, len(lines)) if lines[i].rstrip() in ("---", "...")), None)
        if close is None:
            return None
        entries: list[Entry] = []
        for line in lines[1:close]:
            match = TOP_LEVEL_KEY.match(line)
            if match:
                entries.append((match.group(1), [line]))
            elif entries:
                entries[-1][1].append(line)
            else:
                entries.append((None, [line]))
        return cls(lines[0], entries, lines[close:])

    def get(self, key: str) -> list[str] | None:
        return next((lines for k, lines in self.entries if k == key), None)

    def line_of(self, key: str) -> int:
        line = 2
        for k, lines in self.entries:
            if k == key:
                return line
            line += len(lines)
        return 1

    def render(self) -> str:
        return self.head + "".join("".join(lines) for _, lines in self.entries) + "".join(self.tail)


def shown_value(lines: list[str] | None) -> str:
    if lines is None:
        return "(absent)"
    first = lines[0].split(":", 1)[1].strip()
    return first + (" ..." if len(content(lines)) > 1 else "")


def sync(locale: FrontMatter, english: FrontMatter) -> list[tuple[str, str, str]]:
    """Rewrites locale in place and returns (key, locale value, English value) per change."""
    changes = []
    newline = "\r\n" if locale.head.endswith("\r\n") else "\n"
    for key in STRUCTURAL_KEYS:
        want = english.get(key)
        have = locale.get(key)
        if want is not None:
            want = [line.rstrip("\r\n") + newline for line in content(want)]
        if (content(have) if have is not None else None) == want:
            continue
        changes.append((key, shown_value(have), shown_value(want)))
        if want is None:
            locale.entries = [(k, lines) for k, lines in locale.entries if k != key]
        elif have is None:
            locale.entries.append((key, want))
        else:
            # Keep the blank lines Crowdin left after the entry.
            replacement = want + have[len(content(have)):]
            locale.entries = [(k, replacement if k == key else lines) for k, lines in locale.entries]
    return changes


def strip(locale: FrontMatter) -> list[tuple[str, str]]:
    """Removes NAVIGATION_KEYS from locale in place and returns (key, value) per removed key."""
    removed = [(key, shown_value(locale.get(key))) for key in NAVIGATION_KEYS if locale.get(key) is not None]
    if removed:
        locale.entries = [(k, lines) for k, lines in locale.entries if k not in NAVIGATION_KEYS]
    return removed


def annotate(level: str, path: Path, line: int, message: str) -> None:
    if IN_GITHUB_ACTIONS:
        print(f"::{level} file={path},line={line}::{message}")
    else:
        print(f"{path}:{line}: {level}: {message}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="report drift without writing")
    parser.add_argument("docs_dir", nargs="?", type=Path, default=REPO_ROOT / "docs")
    args = parser.parse_args()

    docs: Path = args.docs_dir
    english_root = docs / "en"
    if not english_root.is_dir():
        print(f"{english_root} does not exist", file=sys.stderr)
        return 2

    locale_roots = sorted(
        p for p in docs.iterdir() if p.is_dir() and p.name != "en" and LOCALE_DIR.match(p.name)
    )
    drifted = 0
    for locale_root in locale_roots:
        for page in sorted(locale_root.rglob("*.md")):
            source = english_root / page.relative_to(locale_root)
            english = FrontMatter.parse(source.read_bytes().decode("utf-8")) if source.is_file() else None
            shown = page.relative_to(REPO_ROOT) if page.is_relative_to(REPO_ROOT) else page
            locale = FrontMatter.parse(page.read_bytes().decode("utf-8"))
            if locale is None:
                if english is None:
                    continue
                missing = [key for key in STRUCTURAL_KEYS if english.get(key) is not None]
                if args.check and missing:
                    drifted += 1
                    annotate("error", shown, 1, f"no front matter, so no {', '.join(missing)}; docs/en has them")
                else:
                    annotate("warning", shown, 1, "no front matter, while its docs/en page has one")
                continue
            lines = {key: locale.line_of(key) for key in STRUCTURAL_KEYS + NAVIGATION_KEYS}
            changes = sync(locale, english) if english is not None else []
            removed = strip(locale)
            if not changes and not removed:
                continue
            drifted += 1
            for key, have, want in changes:
                if args.check:
                    annotate("error", shown, lines[key], f"{key} is '{have}', docs/en has '{want}'")
                else:
                    print(f"{shown}: {key} '{have}' -> '{want}'")
            for key, have in removed:
                if args.check:
                    annotate("error", shown, lines[key], f"{key} is '{have}'; locale pages have no {key}")
                else:
                    print(f"{shown}: {key} '{have}' removed")
            if not args.check:
                page.write_bytes(locale.render().encode("utf-8"))

    if args.check and drifted:
        print(
            f"{drifted} locale page(s) have {' or '.join(STRUCTURAL_KEYS)} unlike docs/en, "
            f"or have {' or '.join(NAVIGATION_KEYS)}. "
            "Fix with: python3 scripts/docs/sync-locale-front-matter.py",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
