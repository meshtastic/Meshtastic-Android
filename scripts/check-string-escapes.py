#!/usr/bin/env python3
"""Guard against Android-style backslash-escaped quotes in Compose resources.

These strings live under ``composeResources`` and are read by the Compose
Multiplatform resources library, whose build-time ``handleSpecialCharacters``
only processes ``\\n``, ``\\t``, ``\\uXXXX`` and ``\\\\`` -- it does NOT strip
``\\"`` or ``\\'`` the way Android's AAPT does. A backslash written before a
quote or apostrophe therefore renders *literally* in the UI (see the Italian
``permission_missing_31`` regression, PR #6357). The correct value is a bare
``"`` / ``'`` (or ``&#34;`` / ``&#39;`` if you want it explicit).

Scope: the English **base** ``values/strings.xml`` only -- the source of truth
authored in this repo. The per-locale ``values-*/`` files are Crowdin mirrors;
their escaping is fixed upstream by the project's custom post-export processor
(strips ``\\'`` and ``\\"`` on export), so editing them here would just be
overwritten on the next sync. This guard catches developer-authored escapes in
the source before they get propagated to every translation.

Exit status is non-zero if any base string contains ``\\"`` or ``\\'``, so it
can gate CI.
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

# Repo root = parent of this script's directory (scripts/).
REPO_ROOT = Path(__file__).resolve().parent.parent

# Base (source-language) resource files only: `values/`, never `values-*/`.
BASE_STRINGS_GLOB = "**/composeResources/values/strings.xml"

# A backslash directly before a quote or apostrophe, but not one that is itself
# escaped (`\\"` = literal backslash + quote, which is intentional). `\n`, `\t`,
# `\uXXXX` and `\\` are meaningful CMP escapes and are deliberately left alone.
ESCAPED_QUOTE = re.compile(r"(?<!\\)\\(['\"])")

# Running inside GitHub Actions enables ::error:: annotations on the PR.
IN_GITHUB_ACTIONS = os.environ.get("GITHUB_ACTIONS") == "true"


def main() -> int:
    files = sorted(REPO_ROOT.glob(BASE_STRINGS_GLOB))
    if not files:
        print(f"error: no base strings.xml found under {REPO_ROOT}", file=sys.stderr)
        return 2

    violations: list[tuple[Path, int, str]] = []

    for path in files:
        for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if ESCAPED_QUOTE.search(line):
                violations.append((path, lineno, line.strip()))

    if not violations:
        print("No backslash-escaped quotes found in base string resources.")
        return 0

    print("Backslash-escaped quotes found in base string resources:\n")
    for path, lineno, text in violations:
        rel = path.relative_to(REPO_ROOT)
        message = "escaped quote/apostrophe renders literally in Compose Multiplatform"
        print(f"  - {rel}:{lineno}: {text}")
        if IN_GITHUB_ACTIONS:
            print(f"::error file={rel},line={lineno}::{message}")

    print(
        "\nCompose Multiplatform does not strip \\\" or \\' (unlike Android AAPT), "
        "so the backslash shows up in the UI. Use a bare \" / ' (or &#34; / &#39;)."
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
