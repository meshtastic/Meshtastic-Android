#!/usr/bin/env python3
"""Validate Fastlane store-listing metadata against store length limits.

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
import sys
from pathlib import Path

# Repo root = parent of this script's directory (scripts/).
REPO_ROOT = Path(__file__).resolve().parent.parent
METADATA_DIR = REPO_ROOT / "fastlane" / "metadata" / "android"

# Per-file character limits. Keys are file names under each locale directory.
# 80 is the F-Droid summary / Google Play short-description limit; 30 is the
# Play title limit. Add more entries here to extend coverage.
LIMITS = {
    "short_description.txt": 80,
    "title.txt": 30,
}

# Running inside GitHub Actions enables ::error:: annotations on the PR.
IN_GITHUB_ACTIONS = os.environ.get("GITHUB_ACTIONS") == "true"


def char_count(path: Path) -> int:
    """Code-point length of a metadata file, ignoring trailing whitespace."""
    return len(path.read_text(encoding="utf-8").rstrip())


def main() -> int:
    if not METADATA_DIR.is_dir():
        print(f"error: metadata directory not found: {METADATA_DIR}", file=sys.stderr)
        return 2

    violations: list[tuple[Path, int, int]] = []

    for file_name, limit in sorted(LIMITS.items()):
        for path in sorted(METADATA_DIR.glob(f"*/{file_name}")):
            count = char_count(path)
            if count > limit:
                violations.append((path, count, limit))

    if not violations:
        print("All store-listing metadata is within length limits.")
        return 0

    print("Store-listing metadata exceeds length limits:\n")
    for path, count, limit in violations:
        rel = path.relative_to(REPO_ROOT)
        message = f"{rel} is {count} chars (limit {limit})"
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
