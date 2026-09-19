#!/usr/bin/env python3
"""Validate ``fastlane/metadata/android`` against the rules of every store that reads it.

The tree is a Crowdin mirror (see ``.github/workflows/scheduled-updates.yml``) read by
three stores: ``supply`` uploads it to Google Play, and F-Droid and IzzyOnDroid read it
straight from git. Each check below is one way a store rejects it:

- **Locale directories.** ``supply`` uploads every directory it finds (uploader.rb's
  ``all_languages`` is a bare ``Dir.entries``) and the Crowdin CLI silently ignores
  mapping keys it does not recognise, so an unmapped code is rejected only by the Play
  API, partway through an upload, after earlier locales have already been written.
- **Lengths.** Crowdin's max-length toggle only blocks *new* submissions; translations
  entered before it was enabled are grandfathered in and keep syncing down. Measured in
  Unicode code points, which is what Play, F-Droid and IzzyOnDroid count -- a byte count
  badly over-reports Cyrillic and CJK.
- **HTML.** fdroidserver's lint rejects a fixed list of tags in a description.

Exit status is non-zero on any violation, so it can gate CI. Under GitHub Actions each
violation is also a ``::error`` annotation on the offending file.
"""

from __future__ import annotations

import os
import re
import sys
from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path

# Repo root = parent of this script's directory (scripts/).
REPO_ROOT = Path(__file__).resolve().parent.parent
METADATA_DIR = REPO_ROOT / "fastlane" / "metadata" / "android"

# Running inside GitHub Actions enables ::error:: annotations on the PR.
IN_GITHUB_ACTIONS = os.environ.get("GITHUB_ACTIONS") == "true"

# Play's published set (Play Console Help, "Add your own translations"), not fastlane's
# Supply::Languages::ALL_LANGUAGES. They disagree: fastlane omits Albanian, which Play
# lists. Play is the authority here, because Play is what rejects the upload.
PLAY_LANGUAGES = frozenset(
    {
        "af", "am", "ar", "az-AZ", "be", "bg", "bn-BD", "ca", "cs-CZ", "da-DK", "de-DE",
        "el-GR", "en-AU", "en-CA", "en-GB", "en-IN", "en-SG", "en-US", "en-ZA", "es-419",
        "es-ES", "es-US", "et", "eu-ES", "fa", "fi-FI", "fil", "fr-CA", "fr-FR", "gl-ES",
        "hi-IN", "hr", "hu-HU", "hy-AM", "id", "is-IS", "it-IT", "iw-IL", "ja-JP", "ka-GE",
        "km-KH", "kn-IN", "ko-KR", "ky-KG", "lo-LA", "lt", "lv", "mk-MK", "ml-IN", "mn-MN",
        "mr-IN", "ms", "ms-MY", "my-MM", "ne-NP", "nl-NL", "no-NO", "pl-PL", "pt-BR",
        "pt-PT", "rm", "ro", "ru-RU", "si-LK", "sk", "sl", "sq", "sr", "sv-SE", "sw",
        "ta-IN", "te-IN", "th", "tr-TR", "uk", "vi", "zh-CN", "zh-HK", "zh-TW", "zu",
    }
)

# Per-file character limits, as globs under each locale directory. Each value is the
# tighter of Google Play's limit and F-Droid's (fdroidserver/common.py `char_limits`):
# 30 is Play's title limit (F-Droid allows 50); 80, 4000 and 500 are the same on both.
LIMITS = {
    "title.txt": 30,
    "short_description.txt": 80,
    "full_description.txt": 4000,
    "changelogs/*.txt": 500,
}

# The tag list fdroidserver/lint.py rejects as "Forbidden HTML tags". F-Droid and
# IzzyOnDroid read full_description.txt straight from this tree, so a hit here is a
# listing failure there, not a warning.
FORBIDDEN_HTML = re.compile(
    r"<(applet|base|body|button|embed|form|head|html|iframe|img|input|link"
    r"|object|picture|script|source|style|svg|video)\b",
    re.IGNORECASE,
)

CROWDIN_HINT = (
    "These files are mirrored from Crowdin. Fix them at the source (shorten or remove "
    "the offending translation so it is re-translated), then re-sync -- editing the "
    "mirror here is overwritten on the next sync."
)
LOCALE_HINT = "Map the locale in crowdin.yml languages_mapping, or exclude it there."


@dataclass(frozen=True, slots=True)
class Violation:
    path: Path  # relative to the repo root, so it doubles as the annotation target
    problem: str
    hint: str

    @property
    def message(self) -> str:
        return f"{self.path} {self.problem}"


def locale_dirs() -> list[Path]:
    return sorted(p for p in METADATA_DIR.iterdir() if p.is_dir() and not p.name.startswith("."))


def check_locales() -> Iterator[Violation]:
    for directory in locale_dirs():
        if directory.name not in PLAY_LANGUAGES:
            yield Violation(
                directory.relative_to(REPO_ROOT),
                f"is not a language code Google Play accepts ('{directory.name}')",
                LOCALE_HINT,
            )


def check_lengths() -> Iterator[Violation]:
    for pattern, limit in LIMITS.items():
        for path in sorted(METADATA_DIR.glob(f"*/{pattern}")):
            # Trailing whitespace is not content; stores strip it too.
            count = len(path.read_text(encoding="utf-8").rstrip())
            if count > limit:
                yield Violation(
                    path.relative_to(REPO_ROOT), f"is {count} chars (limit {limit})", CROWDIN_HINT
                )


def check_html() -> Iterator[Violation]:
    for path in sorted(METADATA_DIR.glob("*/full_description.txt")):
        for match in FORBIDDEN_HTML.finditer(path.read_text(encoding="utf-8")):
            yield Violation(
                path.relative_to(REPO_ROOT),
                f"uses <{match.group(1)}>, which F-Droid forbids",
                CROWDIN_HINT,
            )


CHECKS = (check_locales, check_lengths, check_html)


def main() -> int:
    if not METADATA_DIR.is_dir():
        print(f"error: metadata directory not found: {METADATA_DIR}", file=sys.stderr)
        return 2

    violations = [violation for check in CHECKS for violation in check()]
    if not violations:
        print(f"All {len(locale_dirs())} store-listing locales are within every store's rules.")
        return 0

    print("Store-listing metadata breaks store rules:\n")
    for violation in violations:
        print(f"  - {violation.message}")
        if IN_GITHUB_ACTIONS:
            # Annotate the offending file directly in the PR diff view.
            print(f"::error file={violation.path}::{violation.problem}")

    # One hint per cause, in first-seen order.
    for hint in dict.fromkeys(violation.hint for violation in violations):
        print(f"\n{hint}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
