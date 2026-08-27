#!/usr/bin/env python3
"""Check the flatpak platform-dependency list against the POMs it is supposed to mirror.

`flatpak-sources.json` is generated on an x86_64 runner, so it only ever contains the URLs an x86_64
resolution needs. The arm64 Flatpak then builds *offline* against that manifest, so every artifact it
resolves differently has to be force-resolved during generation — that is what the
`platformDependencies` block in the root build.gradle.kts is for.

Force-resolution there is non-transitive, so each arch-specific artifact needs naming individually,
version and classifier included. The build script derives what it can from the version catalog, but the
rest belongs to upstream POMs, not to us, and nothing stopped those going stale: they are read by no
tool, Renovate cannot see inside them (and should not — the right value is "what the parent POM says",
not "latest"), and a miss surfaces as `Could not find <jar>` eleven minutes into the arm64 build.

This derives the list that *should* be there and fails in seconds if the real one disagrees, in the
same spirit as the vendored-Gradle-distribution guard in verify-flatpak.yml. It runs from the
check-metadata job in pull-request.yml rather than from verify-flatpak.yml, whose path filter excludes
gradle/libs.versions.toml — a dependency bump, which is what causes this drift, would never have run it.

Run from the repository root, with network access:

    python3 scripts/verify-flatpak/check-platform-deps.py
"""

from __future__ import annotations

import re
import urllib.error
import urllib.request
import xml.etree.ElementTree as ElementTree
from pathlib import Path
from typing import NoReturn

MAVEN_CENTRAL = "https://repo1.maven.org/maven2"
POM_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}

# Suffixes that mark an *architecture*, so an artifact carrying one is built per CPU. An OS-only
# suffix does not count: `location-runtime-linux` is the same jar on both Linux arches, so the
# generation host's own resolution already captures it.
ARCH_SUFFIXES = ("arm64", "aarch64", "x64", "x86_64")

CATALOG = Path("gradle/libs.versions.toml")
BUILD_SCRIPT = Path("build.gradle.kts")
DESKTOP_SCRIPT = Path("desktopApp/build.gradle.kts")


def catalog_versions() -> dict[str, str]:
    """The `[versions]` table, so a version we also declare can be checked against its one source."""
    text = CATALOG.read_text()
    versions_table = text.split("[versions]", 1)[1].split("\n[", 1)[0]
    return dict(re.findall(r'^([\w.-]+)\s*=\s*"([^"]+)"', versions_table, re.MULTILINE))


def catalog_libraries() -> dict[str, str]:
    """The `[libraries]` table as alias -> `group:artifact:version`, with version.ref resolved."""
    text = CATALOG.read_text()
    versions = catalog_versions()
    table = text.split("[libraries]", 1)[1].split("\n[", 1)[0]
    libraries = {}
    for alias, body in re.findall(r'^([\w.-]+)\s*=\s*\{([^}]*)\}', table, re.MULTILINE):
        module = re.search(r'module\s*=\s*"([^"]+)"', body)
        ref = re.search(r'version\.ref\s*=\s*"([^"]+)"', body)
        literal = re.search(r'version\s*=\s*"([^"]+)"', body)
        version = versions.get(ref.group(1)) if ref else (literal.group(1) if literal else None)
        if module and version:
            libraries[alias] = f"{module.group(1)}:{version}"
    return libraries


def desktop_linux_runtimes() -> set[str]:
    """The per-arch Linux artifacts desktopApp itself asks for.

    Read from the source rather than assumed, so a runtime added there in future is caught here rather
    than eleven minutes into an arm64 Flatpak build. desktopApp names them through catalog accessors —
    `libs.some.artifact.linux.arm64` — whose dotted alias is the catalog key with dashes.
    """
    text = DESKTOP_SCRIPT.read_text()
    libraries = catalog_libraries()
    found = set()
    for accessor in re.findall(r"libs((?:\.[a-z0-9]+)+)", text):
        alias = accessor.lstrip(".").replace(".", "-")
        coordinate = libraries.get(alias)
        if coordinate and "linux" in alias and any(alias.endswith(s) for s in ARCH_SUFFIXES):
            found.add(coordinate)
    return found


def script_versions() -> dict[str, str]:
    """`val x = catalog.findVersion("alias")` bindings in the build script, resolved to versions.

    The script interpolates these into coordinate templates, so they have to be substituted before a
    template can be compared with anything.
    """
    text = BUILD_SCRIPT.read_text()
    versions = catalog_versions()
    bindings = re.findall(r'val (\w+)\s*=\s*catalog\.findVersion\("([\w.-]+)"\)', text)
    return {name: versions[alias] for name, alias in bindings if alias in versions}


def catalog_derived(platforms: set[str]) -> set[str]:
    """Coordinates the build script derives from the catalog rather than spelling out.

    It looks up an alias built as `"<prefix>-$platform"` once per target platform, so the alias prefix in
    the script plus the platform list says exactly what it will produce. Read from the script rather than
    assumed, so deleting the derivation leaves those coordinates undeclared here too. Any interpolated
    string literal counts as a candidate prefix; one that names no catalog alias yields nothing.
    """
    text = BUILD_SCRIPT.read_text()
    libraries = catalog_libraries()
    found = set()
    for prefix in re.findall(r'"([\w-]+)-\$\w+"', text):
        for platform in platforms:
            coordinate = libraries.get(f"{prefix}-{platform}")
            if coordinate:
                found.add(coordinate)
    return found


def declared() -> tuple[set[str], set[str]]:
    """The platforms and the coordinate templates the build script declares."""
    text = BUILD_SCRIPT.read_text()
    platforms_line = re.search(r"val platforms\s*=\s*setOf\(([^)]*)\)", text)
    templates_block = re.search(r"platformDependencies\.set\((.*?)\n\s*\)\n", text, re.DOTALL)
    if platforms_line is None or templates_block is None:
        fail(["could not find targetPlatforms/platformDependencies in build.gradle.kts"])
    return (
        set(re.findall(r'"([^"]+)"', platforms_line.group(1))),
        set(re.findall(r'"([^"]+)"', templates_block.group(1))),
    )


def expand(templates: set[str], platforms: set[str]) -> set[str]:
    """Every coordinate the plugin will actually force-resolve."""
    return {template.replace("{platform}", platform) for template in templates for platform in platforms}


class Unreachable(Exception):
    """Maven Central could not be read at all, which is not the same as an artifact being absent."""


def substitute(coordinate: str, versions: dict[str, str]) -> str:
    """A coordinate template with its `$name` version interpolations replaced by real versions."""
    for name, version in versions.items():
        coordinate = coordinate.replace(f"${name}", version)
    return coordinate


def fetch_pom(coordinate: str) -> ElementTree.Element | None:
    """The POM for `group:artifact:version[:classifier]`, or None if Maven Central says it is absent.

    Raises [Unreachable] for anything that is not a 404, so an outage is never reported as a version
    that does not exist — the two need opposite responses from the caller.
    """
    group, artifact, version = coordinate.split(":")[:3]
    url = f"{MAVEN_CENTRAL}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    try:
        with urllib.request.urlopen(url, timeout=30) as response:  # noqa: S310 - fixed https host
            return ElementTree.fromstring(response.read())
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise Unreachable(f"{url}: HTTP {error.code}") from error
    except (urllib.error.URLError, ElementTree.ParseError) as error:
        raise Unreachable(f"{url}: {error}") from error


def arch_specific_dependencies(pom: ElementTree.Element) -> set[str]:
    """The dependencies of `pom` that are built per platform, as full coordinates.

    Two shapes count. Any classifier at all: in this dependency graph a classifier always names a
    native payload, and the two arches disagree even when one of them is spelled without an arch
    (LWJGL calls x64 plain `natives-linux`). And an artifactId ending in an architecture, which is how
    skiko and compose-desktop name theirs. Everything else resolves identically on both arches and the
    generation host's own resolution already captured it.
    """
    found = set()
    for dependency in pom.findall(".//m:dependencies/m:dependency", POM_NAMESPACE):
        group = dependency.findtext("m:groupId", "", POM_NAMESPACE)
        artifact = dependency.findtext("m:artifactId", "", POM_NAMESPACE)
        version = dependency.findtext("m:version", "", POM_NAMESPACE)
        classifier = dependency.findtext("m:classifier", "", POM_NAMESPACE)
        if not (group and artifact and version):
            continue
        per_platform = bool(classifier) or any(artifact.endswith(f"-{suffix}") for suffix in ARCH_SUFFIXES)
        if per_platform:
            found.add(f"{group}:{artifact}:{version}" + (f":{classifier}" if classifier else ""))
    return found


def fail(problems: list[str]) -> NoReturn:
    print("Flatpak platform-dependency drift detected:")
    for problem in problems:
        print("  -", problem)
    print()
    print("Fix: update platformDependencies in build.gradle.kts. See its comment for why each is needed.")
    raise SystemExit(1)


def main() -> None:
    versions = catalog_versions()
    platforms, templates = declared()
    substitutions = script_versions()
    literals = {substitute(c, substitutions) for c in expand(templates, platforms)}
    coordinates = literals | catalog_derived(platforms)
    problems: list[str] = []

    unresolved = sorted(c for c in coordinates if "$" in c)
    if unresolved:
        problems.append(
            f"the interpolated version in {unresolved} could not be resolved — has the build script changed shape?"
        )

    # Nothing hand-written should restate a version the catalog already holds. The build script reads
    # those from the catalog now, so this fires only if someone reintroduces a literal.
    for key, module in (
        ("maplibre-compose", "org.maplibre.compose:maplibre-compose-runtime-vulkan-"),
        ("compose-multiplatform", "org.jetbrains.compose.desktop:desktop-jvm-"),
    ):
        expected = versions.get(key)
        pinned = {c.split(":")[2] for c in literals if c.startswith(module)}
        if expected and pinned and pinned != {expected}:
            problems.append(
                f"{module}* is pinned to {sorted(pinned)} but gradle/libs.versions.toml sets {key} = {expected}"
            )

    # Anything desktopApp resolves per Linux arch has to be declared, or the arm64 build has no URL for
    # it at all — that is how the maplibre runtime got missed in the first place.
    for runtime in sorted(desktop_linux_runtimes()):
        if runtime not in coordinates:
            problems.append(f"desktopApp resolves {runtime} on Linux, which is not declared")

    # And everything arch-specific those roots depend on, because force-resolution is non-transitive.
    roots = sorted({c for c in coordinates if len(c.split(":")) == 3} | desktop_linux_runtimes())
    try:
        for root in roots:
            pom = fetch_pom(root)
            if pom is None:
                problems.append(f"{root} is declared but has no published POM — is the version right?")
                continue
            for needed in sorted(arch_specific_dependencies(pom)):
                if needed not in coordinates:
                    problems.append(f"{root} needs {needed}, which is not declared")
    except Unreachable as error:
        # Nothing can be concluded without the POMs, and blocking every PR on a Maven Central outage is
        # worse than letting the arm64 build go back to being the slow backstop it was before this check.
        if problems:
            fail(problems)
        print(f"SKIPPED: could not reach Maven Central ({error}); transitive coverage not verified.")
        raise SystemExit(0) from error

    if problems:
        fail(problems)
    print(f"OK: {len(coordinates)} platform coordinates declared, all arch-specific transitives covered.")


if __name__ == "__main__":
    main()
