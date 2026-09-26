#!/usr/bin/env python3
"""Open the next version line: VERSION_NAME_BASE plus everything pull-request.yml requires with it.

That is the AppStream <release> entry in metainfo.xml, its five <image> URLs moved to the
new version's release assets, and the Play what's-new rendered from the entry by
sync-play-changelog.py. The entry's paragraph is a placeholder; rewrite it and re-run
sync-play-changelog.py before the internal cut, or it ships as the store text.

Running it twice for the same version changes nothing the second time.

    python3 scripts/bump-version-name.py <X.Y.Z>
"""
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CONFIG = REPO_ROOT / "config.properties"
METAINFO = REPO_ROOT / "desktopApp/packaging/linux/org.meshtastic.MeshtasticDesktop.metainfo.xml"
SYNC = REPO_ROOT / "scripts/sync-play-changelog.py"
PLACEHOLDER = "Stability and reliability fixes."


def bump_config(v: str) -> None:
    text, n = re.subn(r"^VERSION_NAME_BASE=.*$", f"VERSION_NAME_BASE={v}", CONFIG.read_text(), flags=re.M)
    if n != 1:
        sys.exit(f"expected one VERSION_NAME_BASE line in {CONFIG.name}, found {n}")
    CONFIG.write_text(text)


def bump_metainfo(v: str) -> None:
    text = METAINFO.read_text()
    if f'<release version="{v}"' not in text:
        first = re.search(r"^([ \t]*)<release ", text, re.M)
        if not first:
            sys.exit(f"no <release> entry in {METAINFO.name} to insert above")
        ind = first.group(1)
        date = datetime.now(timezone.utc).date().isoformat()
        entry = (
            f'{ind}<release version="{v}" date="{date}">\n'
            f"{ind}  <description>\n"
            f"{ind}    <p>{PLACEHOLDER}</p>\n"
            f"{ind}  </description>\n"
            f"{ind}</release>\n"
        )
        text = text[: first.start()] + entry + text[first.start() :]
    text = re.sub(r"(<image>[^<]*/releases/download/)v[^/<]+/", rf"\g<1>v{v}/", text)
    METAINFO.write_text(text)


def main() -> int:
    if len(sys.argv) != 2 or not re.fullmatch(r"\d+\.\d+\.\d+", sys.argv[1]):
        sys.exit("usage: bump-version-name.py <X.Y.Z>")
    v = sys.argv[1]
    bump_config(v)
    bump_metainfo(v)
    subprocess.run([sys.executable, str(SYNC)], check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
