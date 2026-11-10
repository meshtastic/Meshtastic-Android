#!/usr/bin/env python3
"""Point the Flathub manifest at a release: the source tag and commit, and the Gradle zip.

Usage: bump-flathub-manifest.py <manifest.yaml> <tag> <commit> <gradle-wrapper.properties>
       bump-flathub-manifest.py --gradle-only <manifest.yaml> <gradle-wrapper.properties>

The Gradle distribution URL and sha256 come from the tag's own wrapper properties, since the
offline build can only run the Gradle version that tag pins. --gradle-only rewrites just the
Gradle zip, which is how the verify-flatpak overlay gets the working tree's wrapper. Each field is rewritten in
place, so comments and layout survive. Any field that does not match exactly once fails the
run instead of guessing, and the manifest is then bumped by hand.
"""

import re
import sys

GIT_SOURCE = re.compile(
    r"^(?P<lead>\s+url: https://github\.com/meshtastic/Meshtastic-Android\.git\n"
    r"\s+tag: )\S+(?P<mid>\n\s+commit: )[0-9a-f]{40}$",
    re.MULTILINE,
)
GRADLE_ZIP = re.compile(
    r"^(?P<lead>\s+url: )https://services\.gradle\.org/distributions/gradle-[^\s/]+\.zip"
    r"(?P<mid>\n\s+sha256: )[0-9a-f]{64}$",
    re.MULTILINE,
)


def wrapper_distribution(path: str) -> tuple[str, str]:
    props = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                props[key.strip()] = value.strip().replace("\\:", ":")
    url = props.get("distributionUrl", "")
    sha = props.get("distributionSha256Sum", "")
    if not re.fullmatch(r"https://services\.gradle\.org/distributions/gradle-[^\s/]+\.zip", url):
        sys.exit(f"{path}: distributionUrl '{url}' is not a services.gradle.org distribution")
    if not re.fullmatch(r"[0-9a-f]{64}", sha):
        sys.exit(f"{path}: distributionSha256Sum '{sha}' is not a sha256")
    return url, sha


def replace_once(pattern: re.Pattern, value_a: str, value_b: str, text: str, what: str) -> str:
    new, count = pattern.subn(lambda m: m["lead"] + value_a + m["mid"] + value_b, text)
    if count != 1:
        sys.exit(f"{what}: expected exactly one match in the manifest, found {count}")
    return new


def main() -> None:
    args = sys.argv[1:]
    gradle_only = args[:1] == ["--gradle-only"]
    if gradle_only and len(args) == 3:
        manifest, wrapper = args[1:]
    elif not gradle_only and len(args) == 4:
        manifest, tag, commit, wrapper = args
        if not re.fullmatch(r"v\d+\.\d+\.\d+", tag):
            sys.exit(f"'{tag}' is not a production tag (vX.Y.Z)")
        if not re.fullmatch(r"[0-9a-f]{40}", commit):
            sys.exit(f"'{commit}' is not a full commit sha")
    else:
        sys.exit(__doc__)
    url, sha = wrapper_distribution(wrapper)

    with open(manifest, encoding="utf-8") as f:
        text = f.read()
    if not gradle_only:
        text = replace_once(GIT_SOURCE, tag, commit, text, "Meshtastic-Android git source (url, tag, commit)")
    text = replace_once(GRADLE_ZIP, url, sha, text, "Gradle distribution (url, sha256)")
    with open(manifest, "w", encoding="utf-8") as f:
        f.write(text)
    zip_name = url.rsplit("/", 1)[-1]
    print(f"{manifest}: {zip_name}" if gradle_only else f"{manifest}: {tag} at {commit}, {zip_name}")


if __name__ == "__main__":
    main()
