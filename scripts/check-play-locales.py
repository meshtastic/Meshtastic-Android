#!/usr/bin/env python3
"""Fail when a fastlane locale directory is not a language Google Play accepts.

Nothing else checks this. Crowdin's locale placeholder renders Crowdin's own codes, and
the Crowdin CLI silently ignores config keys it does not recognise, so a mapping typo is
invisible there. On the other side, supply uploads every directory it finds - uploader.rb's
all_languages is a bare Dir.entries with no reference to Supply::Languages::ALL_LANGUAGES -
and fastlane documents no behaviour for unknown folders. The rejection therefore arrives
from the Play API mid-upload, after earlier locales have already been written.

The list below is Play's published set (Play Console Help, "Add your own translations"),
not fastlane's ALL_LANGUAGES. They disagree: fastlane omits Albanian, which Play lists.
Play is the authority here, because Play is what rejects the upload.
"""
import sys
from pathlib import Path

METADATA = Path(__file__).resolve().parent.parent / "fastlane/metadata/android"

PLAY_LANGUAGES = {
    "af",
    "am",
    "ar",
    "az-AZ",
    "be",
    "bg",
    "bn-BD",
    "ca",
    "cs-CZ",
    "da-DK",
    "de-DE",
    "el-GR",
    "en-AU",
    "en-CA",
    "en-GB",
    "en-IN",
    "en-SG",
    "en-US",
    "en-ZA",
    "es-419",
    "es-ES",
    "es-US",
    "et",
    "eu-ES",
    "fa",
    "fi-FI",
    "fil",
    "fr-CA",
    "fr-FR",
    "gl-ES",
    "hi-IN",
    "hr",
    "hu-HU",
    "hy-AM",
    "id",
    "is-IS",
    "it-IT",
    "iw-IL",
    "ja-JP",
    "ka-GE",
    "km-KH",
    "kn-IN",
    "ko-KR",
    "ky-KG",
    "lo-LA",
    "lt",
    "lv",
    "mk-MK",
    "ml-IN",
    "mn-MN",
    "mr-IN",
    "ms",
    "ms-MY",
    "my-MM",
    "ne-NP",
    "nl-NL",
    "no-NO",
    "pl-PL",
    "pt-BR",
    "pt-PT",
    "rm",
    "ro",
    "ru-RU",
    "si-LK",
    "sk",
    "sl",
    "sq",
    "sr",
    "sv-SE",
    "sw",
    "ta-IN",
    "te-IN",
    "th",
    "tr-TR",
    "uk",
    "vi",
    "zh-CN",
    "zh-HK",
    "zh-TW",
    "zu",
}


def main() -> int:
    if not METADATA.is_dir():
        print(f"error: {METADATA} not found", file=sys.stderr)
        return 2
    dirs = sorted(p.name for p in METADATA.iterdir() if p.is_dir() and not p.name.startswith("."))
    bad = [d for d in dirs if d not in PLAY_LANGUAGES]
    if not bad:
        print(f"All {len(dirs)} fastlane locale directories are languages Google Play accepts.")
        return 0
    for d in bad:
        print(
            f"::error file=fastlane/metadata/android/{d}::'{d}' is not a Play language code. "
            "Map it in crowdin.yml languages_mapping, or exclude it there.",
            file=sys.stderr,
        )
    print(f"\n{len(bad)} of {len(dirs)} locale directories would be rejected by Play.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
