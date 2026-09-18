#!/usr/bin/env python3
"""Validate Fastlane store-listing metadata against the stores' rules.

The ``fastlane/metadata/android`` tree is a mirror of Crowdin: translations are
downloaded on a schedule (see ``.github/workflows/scheduled-updates.yml``).
Crowdin's max-length toggle only blocks *new* submissions; translations entered
before enforcement was enabled are grandfathered in and keep syncing down. This
script is the repo-side guard that catches them regardless of Crowdin state.

Lengths are measured in Unicode code points (what Google Play and F-Droid /
IzzyOnDroid count), not bytes -- a byte count badly over-reports Cyrillic and
CJK strings.

Exit status is non-zero if any file exceeds its limit, so it can gate CI.
"""

from __future__ import annotations

import os
import re
import sys
from pathlib import Path

# Repo root = parent of this script's directory (scripts/).
REPO_ROOT = Path(__file__).resolve().parent.parent
METADATA_DIR = REPO_ROOT / "fastlane" / "metadata" / "android"

# Per-file character limits. Keys are globs under each locale directory. Each
# value is the tighter of Google Play's limit and F-Droid's
# (fdroidserver/common.py `char_limits`): 30 is Play's title limit (F-Droid
# allows 50); 80, 4000 and 500 are the same on both.
LIMITS = {
    "short_description.txt": 80,
    "title.txt": 30,
    "full_description.txt": 4000,
    # Play's "what's new" cap, for every changelog a release leaves behind. The
    # pattern is rooted at the locale directory, not at a bare file name.
    "changelogs/*.txt": 500,
}

# HTML that fdroidserver's lint rejects in a description
# (fdroidserver/lint.py, "Forbidden HTML tags"). F-Droid and IzzyOnDroid read
# full_description.txt straight from this tree, so a hit here is a listing
# failure there, not a warning.
FORBIDDEN_HTML = re.compile(
    r"<(applet|base|body|button|embed|form|head|html|iframe|img|input|link"
    r"|object|picture|script|source|style|svg|video)\b",
    re.IGNORECASE,
)

# Running inside GitHub Actions enables ::error:: annotations on the PR.
IN_GITHUB_ACTIONS = os.environ.get("GITHUB_ACTIONS") == "true"


def char_count(path: Path) -> int:
    """Code-point length of a metadata file, ignoring trailing whitespace."""
    return len(path.read_text(encoding="utf-8").rstrip())


def main() -> int:
    if not METADATA_DIR.is_dir():
        print(f"error: metadata directory not found: {METADATA_DIR}", file=sys.stderr)
        return 2

    violations: list[tuple[Path, str]] = []

    for pattern, limit in sorted(LIMITS.items()):
        for path in sorted(METADATA_DIR.glob(f"*/{pattern}")):
            count = char_count(path)
            if count > limit:
                violations.append((path, f"is {count} chars (limit {limit})"))

    for path in sorted(METADATA_DIR.glob("*/full_description.txt")):
        for match in FORBIDDEN_HTML.finditer(path.read_text(encoding="utf-8")):
            violations.append((path, f"uses <{match.group(1)}>, which F-Droid forbids"))

    if not violations:
        print("All store-listing metadata is within length limits and uses no forbidden HTML.")
        return 0

    print("Store-listing metadata breaks store rules:\n")
    for path, problem in violations:
        rel = path.relative_to(REPO_ROOT)
        message = f"{rel} {problem}"
        print(f"  - {message}")
        if IN_GITHUB_ACTIONS:
            # Annotate the offending file directly in the PR diff view.
            print(f"::error file={rel}::{message}")

    print(
        "\nThese files are mirrored from Crowdin. Fix them at the source "
        "(shorten or remove the overlength translation so it is re-translated), "
        "then re-sync -- editing the mirror here is overwritten on the next sync."
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
