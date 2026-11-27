#!/usr/bin/env python3
"""Self-test for discord-release-post.py: run `python3 scripts/test_discord_release_post.py`."""
import importlib.util
import re
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("post", Path(__file__).with_name("discord-release-post.py"))
post = importlib.util.module_from_spec(spec)
spec.loader.exec_module(post)

URL = "https://github.com/meshtastic/Meshtastic-Android/releases/tag/v2.8.5-open.1"
PULL = "https://github.com/meshtastic/Meshtastic-Android/pull"


def notes(entries: int) -> str:
    lines = ["## Highlights", "", "Guided setup for a new node.", "", "## What's Changed", "### 🛠️ Fixes"]
    lines += [f"* fix(ble): adopt thing number {i} by @jamesarich in {PULL}/{i}" for i in range(1, entries + 1)]
    lines += ["", "**Full Changelog**: https://github.com/meshtastic/Meshtastic-Android/compare/a...b"]
    return "\n".join(lines)


class DiscordReleasePost(unittest.TestCase):
    def test_entries_lose_prefix_author_and_url(self):
        text = post.description(notes(1), "Beta (Open)", URL)
        self.assertEqual(
            text,
            "Promoted to the **Beta (Open)** track.\n\nGuided setup for a new node.\n\n### Fixes\n- Adopt thing number 1 (#1)",
        )

    def test_cut_keeps_the_whole_link_and_counts_what_it_dropped(self):
        url = f"{URL}?{'x' * 200}"
        text = post.description(notes(150), "Production", url)
        shown = len(re.findall(r"^- ", text, re.M))
        self.assertLessEqual(len(text), 4096)
        self.assertTrue(text.endswith(f"[{150 - shown} more in the full notes]({url})"), text[-160:])
        self.assertLess(shown, 150)

    def test_no_notes_still_names_the_track(self):
        self.assertEqual(post.description("", "Alpha (Closed)", URL), "Promoted to the **Alpha (Closed)** track.")


if __name__ == "__main__":
    unittest.main()
